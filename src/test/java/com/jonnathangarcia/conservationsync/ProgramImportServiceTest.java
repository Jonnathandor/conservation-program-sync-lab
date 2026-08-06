package com.jonnathangarcia.conservationsync;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ProgramImportServiceTest {
    private static final String SOURCE_ID = "SYN-SERVICE-001";

    @Autowired
    private ProgramImportService importService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void removeExistingFixture() {
        jdbcTemplate.update("""
                DELETE FROM program_record
                WHERE source_id = ?
                """, SOURCE_ID);
    }

    @Test
    void importingAnIdenticalRecordTwiceReportsCreatedThenUnchanged() {
        var record = new ProgramRecordInput(
                SOURCE_ID,
                "MB",
                "ACTIVE",
                new BigDecimal("12.50"),
                OffsetDateTime.parse("2026-08-01T14:00:00Z")
        );

        var firstImport = importService.importRows(List.of(record));

        assertThat(firstImport)
                .isEqualTo(new ImportResult(
                        1, // received
                        1, // created
                        0, // updated
                        0, // unchanged
                        0  // rejected
                ));

        var secondImport = importService.importRows(List.of(record));

        assertThat(secondImport)
                .isEqualTo(new ImportResult(
                        1,
                        0,
                        0,
                        1,
                        0
                ));

        var storedRecords = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM program_record
                WHERE source_id = ?
                """, Long.class, SOURCE_ID);

        assertThat(storedRecords).isEqualTo(1L);
    }
}
