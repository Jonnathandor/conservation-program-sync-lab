package com.jonnathangarcia.conservationsync;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProgramCsvHeaderAcceptanceTest {
    private static final String HEADERS =
            "source_id,region_code,status,area_hectares,source_updated_at";
    private static final String ROWS = """
            SYN-HEADER-VALID-001,MB,ACTIVE,40.00,2026-08-05T14:00:00Z
            SYN-HEADER-BAD-TIME-001,SK,ACTIVE,15.00,bad-date
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    static Stream<Arguments> invalidFiles() {
        return Stream.of(
                Arguments.of("missing", "source_id,region_code,status,area_hectares\n" + ROWS),
                Arguments.of("duplicate replacing required name",
                        "source_id,region_code,status,area_hectares,source_id\n" + ROWS),
                Arguments.of("duplicate appended to complete header", HEADERS + ",source_id\n" + ROWS),
                Arguments.of("blank", "source_id,region_code,status,area_hectares,   \n" + ROWS),
                Arguments.of("extra", HEADERS + ",notes\n" + ROWS),
                Arguments.of("wrong case", HEADERS.replace("source_id", "SOURCE_ID") + "\n" + ROWS),
                Arguments.of("empty file", ""),
                Arguments.of("blank header line", "\n" + ROWS),
                Arguments.of("invalid header before broken row", "source_id\n\"unterminated"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFiles")
    void invalidHeadersRejectWholeFileWithoutDatabaseWrites(String category, String csv)
            throws Exception {
        long canonicalBefore = count("program_record");
        long quarantineBefore = count("program_record_quarantine");

        mockMvc.perform(multipart("/api/imports").file(file(csv)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CSV_HEADERS"))
                .andExpect(jsonPath("$.message").value(
                        "CSV headers must contain source_id, region_code, status, area_hectares, "
                                + "and source_updated_at exactly once, in any order, with no blank or extra names."));

        assertThat(count("program_record")).isEqualTo(canonicalBefore);
        assertThat(count("program_record_quarantine")).isEqualTo(quarantineBefore);
    }

    @Test
    void reorderedTrimmedHeadersStillMapValuesByName() throws Exception {
        var csv = """
                source_updated_at, status ,source_id,area_hectares,region_code
                2026-08-05T14:00:00Z,ACTIVE,SYN-HEADER-REORDERED-001,42.50,MB
                """;

        mockMvc.perform(multipart("/api/imports").file(file(csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(1))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(0))
                .andExpect(jsonPath("$.rejected").value(0));

        var record = jdbcTemplate.queryForMap("""
                SELECT region_code, status, area_hectares
                FROM program_record WHERE source_id = 'SYN-HEADER-REORDERED-001'
                """);
        assertThat(record).containsEntry("region_code", "MB").containsEntry("status", "ACTIVE");
        assertThat((java.math.BigDecimal) record.get("area_hectares"))
                .isEqualByComparingTo("42.50");
    }

    @Test
    void validHeaderOnlyFileKeepsExistingEmptyImportBehavior() throws Exception {
        long canonicalBefore = count("program_record");
        long quarantineBefore = count("program_record_quarantine");

        mockMvc.perform(multipart("/api/imports").file(file(HEADERS + "\n")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.received").value(0))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(0))
                .andExpect(jsonPath("$.rejected").value(0));

        assertThat(count("program_record")).isEqualTo(canonicalBefore);
        assertThat(count("program_record_quarantine")).isEqualTo(quarantineBefore);
    }

    private MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "headers.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
    }

    private long count(String table) {
        // Called only with the two fixed table names in this test, never request input.
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
