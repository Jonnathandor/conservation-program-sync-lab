package com.jonnathangarcia.conservationsync;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class ProgramImportAcceptanceTest {
        private static final String SOURCE_ID = "SYN-MB-001";

        private static final byte[] VALID_CSV = """
            source_id,region_code,status,area_hectares,source_updated_at
            SYN-MB-001,MB,ACTIVE,12.50,2026-08-01T14:00:00Z
            """.getBytes(StandardCharsets.UTF_8);

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void reimportingTheSameValidRecordDoesNotCreateADuplicate()
                throws Exception {

            importCsv()
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.received").value(1))
                    .andExpect(jsonPath("$.created").value(1))
                    .andExpect(jsonPath("$.updated").value(0))
                    .andExpect(jsonPath("$.unchanged").value(0))
                    .andExpect(jsonPath("$.rejected").value(0));

            importCsv()
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.received").value(1))
                    .andExpect(jsonPath("$.created").value(0))
                    .andExpect(jsonPath("$.updated").value(0))
                    .andExpect(jsonPath("$.unchanged").value(1))
                    .andExpect(jsonPath("$.rejected").value(0));

            var rows = jdbcTemplate.queryForList("""
                    SELECT source_id, region_code, status
                    FROM program_record
                    WHERE source_id = ?
                    """, SOURCE_ID);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0))
                    .containsEntry("source_id", SOURCE_ID)
                    .containsEntry("region_code", "MB")
                    .containsEntry("status", "ACTIVE");
        }

        private ResultActions importCsv() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "one-valid-record.csv",
                "text/csv",
                VALID_CSV
        );

        return mockMvc.perform(
                multipart("/api/imports").file(file)
        );
    }

}
