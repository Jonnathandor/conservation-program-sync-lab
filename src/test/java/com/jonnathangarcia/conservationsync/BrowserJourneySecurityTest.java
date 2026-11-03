package com.jonnathangarcia.conservationsync;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "meadowline.demo.password=synthetic-test-password")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BrowserJourneySecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void anonymousCannotReadHistoryOrReceiptsOrUpload() throws Exception {
        mvc.perform(get("/api/imports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/imports/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/imports").file(file()).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedWritesRequireCsrfAndWrongRoleCannotRead() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM import_receipt", Long.class);
        mvc.perform(multipart("/api/imports").file(file()).with(user("demo").roles("DEMO_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/imports").with(user("other").roles("OTHER")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_receipt", Long.class)).isEqualTo(before);
    }

    @Test
    void realFormLoginEstablishesSessionAndCsrfLogoutInvalidatesIt() throws Exception {
        var login = mvc.perform(formLogin().user("demo").password("synthetic-test-password"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/session").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"));
        mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndLoginWithoutCsrfFail() throws Exception {
        mvc.perform(formLogin().user("demo").password("wrong"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").param("username", "demo").param("password", "synthetic-test-password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyIsBoundedNewestFirstAndCanBeRevisitedWithSession() throws Exception {
        var ids = new ArrayList<UUID>();
        try {
            for (int i = 0; i < 25; i++) {
                var id = UUID.randomUUID(); ids.add(id);
                jdbc.update("""
                        INSERT INTO import_receipt (id, completed_at, received, created, updated, unchanged, rejected)
                        VALUES (?, ?, 0, 0, 0, 0, 0)
                        """, id, OffsetDateTime.parse("2100-01-01T00:00:00Z").plusSeconds(i));
            }
            mvc.perform(get("/api/imports").with(user("demo").roles("DEMO_USER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.receipts.length()").value(20))
                    .andExpect(jsonPath("$.receipts[0].id").value(ids.get(24).toString()))
                    .andExpect(jsonPath("$.hasMore").value(true));
            mvc.perform(get("/api/imports?offset=20").with(user("demo").roles("DEMO_USER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.receipts[0].id").value(ids.get(4).toString()));
            mvc.perform(get("/api/imports?offset=-1").with(user("demo").roles("DEMO_USER")))
                    .andExpect(status().isBadRequest());
        } finally {
            for (var id : ids) jdbc.update("DELETE FROM import_receipt WHERE id = ?", id);
        }
    }

    @Test
    void realSessionUploadsRejectsInvalidHeadersAndRevisitsSavedHistory() throws Exception {
        var login = mvc.perform(formLogin().user("demo").password("synthetic-test-password"))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        var csrfResponse = mvc.perform(get("/api/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String header = com.jayway.jsonpath.JsonPath.read(csrfResponse, "$.headerName");
        String token = com.jayway.jsonpath.JsonPath.read(csrfResponse, "$.token");
        var csv = """
                source_id,region_code,status,area_hectares,source_updated_at
                SYN-SESSION-JOURNEY-VALID,MB,ACTIVE,12.50,2026-08-05T14:00:00Z
                SYN-SESSION-JOURNEY-BAD,SK,ACTIVE,15.00,bad-date
                """;
        var upload = new MockMultipartFile("file", "synthetic.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var first = mvc.perform(multipart("/api/imports").file(upload)
                        .session(session).header(header, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(first).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.received").value(2));
        var second = mvc.perform(multipart("/api/imports").file(upload)
                        .session(session).header(header, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unchanged").value(1))
                .andReturn().getResponse().getHeader("Location");
        assertThat(second).isNotEqualTo(first);

        long before = jdbc.queryForObject("SELECT COUNT(*) FROM import_receipt", Long.class);
        var invalid = new MockMultipartFile("file", "invalid.csv", "text/csv",
                "source_id,extra\nSYN-NOT-IMPORTED,value\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/imports").file(invalid)
                        .session(session).header(header, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CSV_HEADERS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_receipt", Long.class))
                .isEqualTo(before);
        // A later request with the same session represents revisiting the page/API.
        mvc.perform(get("/api/imports").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receipts[?(@.id == '" + first.substring(first.lastIndexOf('/') + 1) + "')]").isNotEmpty());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "empty.csv", "text/csv",
                "source_id,region_code,status,area_hectares,source_updated_at\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
