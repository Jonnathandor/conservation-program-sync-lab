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

    private static final String MIXED_VALID_SOURCE_ID =
            "SYN-MIX-VALID-001";

    private static final String MIXED_INVALID_SOURCE_ID =
            "SYN-MIX-INVALID-001";

    private static final byte[] MIXED_CSV = """
            source_id,region_code,status,area_hectares,source_updated_at
            %s,MB,ACTIVE,25.00,2026-08-03T14:00:00Z
            %s,SK,NOT_A_VALID_STATUS,7.50,2026-08-03T15:00:00Z
            """
            .formatted(
                    MIXED_VALID_SOURCE_ID,
                    MIXED_INVALID_SOURCE_ID
            )
            .getBytes(StandardCharsets.UTF_8);

    private static final String MALFORMED_VALID_SOURCE_ID =
            "SYN-MALFORMED-VALID-001";

    private static final String MALFORMED_AREA_SOURCE_ID =
            "SYN-MALFORMED-AREA-001";

    private static final byte[] MALFORMED_AREA_CSV = """
            source_id,region_code,status,area_hectares,source_updated_at
            %s,MB,ACTIVE,30.00,2026-08-04T14:00:00Z
            %s,SK,ACTIVE,twelve,2026-08-04T15:00:00Z
            """
            .formatted(
                    MALFORMED_VALID_SOURCE_ID,
                    MALFORMED_AREA_SOURCE_ID
            )
            .getBytes(StandardCharsets.UTF_8);

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

    @Test
    void importingMixedCsvPersistsValidRecordAndQuarantinesInvalidRecord()
            throws Exception {

        var file = new MockMultipartFile(
                "file",
                "mixed-records.csv",
                "text/csv",
                MIXED_CSV
        );

        mockMvc.perform(
                        multipart("/api/imports").file(file)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(0))
                .andExpect(jsonPath("$.rejected").value(1));

        var canonicalRows = jdbcTemplate.queryForList("""
                SELECT source_id, region_code, status
                FROM program_record
                WHERE source_id IN (?, ?)
                """,
                MIXED_VALID_SOURCE_ID,
                MIXED_INVALID_SOURCE_ID
        );

        assertThat(canonicalRows).hasSize(1);
        assertThat(canonicalRows.get(0))
                .containsEntry(
                        "source_id",
                        MIXED_VALID_SOURCE_ID
                )
                .containsEntry("region_code", "MB")
                .containsEntry("status", "ACTIVE");

        var quarantinedRows = jdbcTemplate.queryForList("""
                SELECT source_id, status, rejection_reason
                FROM program_record_quarantine
                WHERE source_id IN (?, ?)
                """,
                MIXED_VALID_SOURCE_ID,
                MIXED_INVALID_SOURCE_ID
        );

        assertThat(quarantinedRows).hasSize(1);
        assertThat(quarantinedRows.get(0))
                .containsEntry(
                        "source_id",
                        MIXED_INVALID_SOURCE_ID
                )
                .containsEntry(
                        "status",
                        "NOT_A_VALID_STATUS"
                )
                .containsEntry(
                        "rejection_reason",
                        "Unsupported status: NOT_A_VALID_STATUS"
                );
    }

    @Test
    void importingMalformedAreaQuarantinesBadRowWithoutStoppingValidRow()
            throws Exception {

        var file = new MockMultipartFile(
                "file",
                "malformed-area.csv",
                "text/csv",
                MALFORMED_AREA_CSV
        );

        mockMvc.perform(
                        multipart("/api/imports").file(file)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(0))
                .andExpect(jsonPath("$.rejected").value(1));

        var canonicalRows = jdbcTemplate.queryForList("""
                SELECT source_id
                FROM program_record
                WHERE source_id IN (?, ?)
                """,
                MALFORMED_VALID_SOURCE_ID,
                MALFORMED_AREA_SOURCE_ID
        );

        assertThat(canonicalRows).hasSize(1);
        assertThat(canonicalRows.get(0))
                .containsEntry(
                        "source_id",
                        MALFORMED_VALID_SOURCE_ID
                );

        var quarantinedRows = jdbcTemplate.queryForList("""
                SELECT
                    source_id,
                    area_hectares,
                    rejection_reason
                FROM program_record_quarantine
                WHERE source_id = ?
                """,
                MALFORMED_AREA_SOURCE_ID
        );

        assertThat(quarantinedRows).hasSize(1);
        assertThat(quarantinedRows.get(0))
                .containsEntry(
                        "source_id",
                        MALFORMED_AREA_SOURCE_ID
                )
                .containsEntry("area_hectares", "twelve")
                .containsEntry(
                        "rejection_reason",
                        "Invalid area_hectares: twelve"
                );
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