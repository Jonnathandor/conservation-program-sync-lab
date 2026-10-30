package com.jonnathangarcia.conservationsync;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProgramImportReceiptAcceptanceTest {
    private static final String HEADERS =
            "source_id,region_code,status,area_hectares,source_updated_at\n";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void completedMixedUploadsHaveSeparateRetrievableReceiptsAndCompatibleBodies() throws Exception {
        String csv = HEADERS + """
                SYN-RECEIPT-VALID-001,MB,ACTIVE,40.00,2026-08-05T14:00:00Z
                SYN-RECEIPT-BAD-001,SK,ACTIVE,15.00,bad-date
                """;
        long receiptsBefore = receiptCount();

        var first = mockMvc.perform(multipart("/api/imports").file(file(csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andReturn().getResponse().getHeader("Location");
        assertThat(first).startsWith("/api/imports/");
        UUID firstId = UUID.fromString(first.substring("/api/imports/".length()));

        mockMvc.perform(get(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.id").value(firstId.toString()))
                .andExpect(jsonPath("$.completedAt").isString())
                .andExpect(jsonPath("$.result.length()").value(5))
                .andExpect(jsonPath("$.result.received").value(2))
                .andExpect(jsonPath("$.result.created").value(1))
                .andExpect(jsonPath("$.result.updated").value(0))
                .andExpect(jsonPath("$.result.unchanged").value(0))
                .andExpect(jsonPath("$.result.rejected").value(1));
        var completedAt = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM import_receipt WHERE id = ?",
                OffsetDateTime.class, firstId);
        assertThat(completedAt).isNotNull();

        var second = mockMvc.perform(multipart("/api/imports").file(file(csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.unchanged").value(1))
                .andReturn().getResponse().getHeader("Location");
        assertThat(second).isNotEqualTo(first);
        mockMvc.perform(get(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.received").value(2))
                .andExpect(jsonPath("$.result.created").value(0))
                .andExpect(jsonPath("$.result.unchanged").value(1))
                .andExpect(jsonPath("$.result.rejected").value(1));
        assertThat(receiptCount()).isEqualTo(receiptsBefore + 2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM program_record WHERE source_id = 'SYN-RECEIPT-VALID-001'
                """, Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM program_record_quarantine WHERE source_id = 'SYN-RECEIPT-BAD-001'
                """, Long.class)).isEqualTo(2);
    }

    @Test
    void validHeaderOnlyUploadAlsoHasCompletedReceipt() throws Exception {
        var location = mockMvc.perform(multipart("/api/imports").file(file(HEADERS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(0))
                .andReturn().getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.received").value(0))
                .andExpect(jsonPath("$.result.created").value(0))
                .andExpect(jsonPath("$.result.updated").value(0))
                .andExpect(jsonPath("$.result.unchanged").value(0))
                .andExpect(jsonPath("$.result.rejected").value(0));
    }

    @Test
    void unknownReceiptReturnsClear404AndMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/imports/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_RECEIPT_NOT_FOUND"));
        mockMvc.perform(get("/api/imports/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiptInsertFailureRollsBackCanonicalAndQuarantineWrites() throws Exception {
        var csv = HEADERS + """
                SYN-RECEIPT-ROLLBACK-VALID,MB,ACTIVE,20.00,2026-08-05T14:00:00Z
                SYN-RECEIPT-ROLLBACK-BAD,SK,ACTIVE,15.00,bad-date
                """;
        long receiptsBefore = receiptCount();

        // Disposable test database only: reject this two-row receipt, after data import.
        // No test transaction wraps the request: the application must supply atomicity.
        jdbcTemplate.execute("""
                ALTER TABLE import_receipt ADD CONSTRAINT test_receipt_insert_failure
                CHECK (received <> 2) NOT VALID
                """);
        try {
            assertThatThrownBy(() -> mockMvc.perform(multipart("/api/imports").file(file(csv))))
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("ALTER TABLE import_receipt DROP CONSTRAINT test_receipt_insert_failure");
        }

        assertThat(receiptCount()).isEqualTo(receiptsBefore);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM program_record WHERE source_id = 'SYN-RECEIPT-ROLLBACK-VALID'
                """, Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM program_record_quarantine WHERE source_id = 'SYN-RECEIPT-ROLLBACK-BAD'
                """, Long.class)).isZero();
    }

    private MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "synthetic.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
    }

    private long receiptCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_receipt", Long.class);
    }
}
