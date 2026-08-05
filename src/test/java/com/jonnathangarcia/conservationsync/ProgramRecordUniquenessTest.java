package com.jonnathangarcia.conservationsync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;


@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ProgramRecordUniquenessTest {
    private static final String SOURCE_ID = "SYN-DB-UNIQUE-001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void databaseRejectsDuplicateSourceIds() {
        assertThat(insertRecord()).isEqualTo(1);

        assertThatThrownBy(() -> insertRecord())
                .isInstanceOf(DataIntegrityViolationException.class);

        var count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM program_record
                WHERE source_id = ?
                """, Long.class, SOURCE_ID);

        assertThat(count).isEqualTo(1L);
    }

    private int insertRecord() {
        return jdbcTemplate.update("""
                INSERT INTO program_record (
                    source_id,
                    region_code,
                    status,
                    area_hectares,
                    source_updated_at
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                SOURCE_ID,
                "MB",
                "ACTIVE",
                12.50,
                OffsetDateTime.parse("2026-08-01T14:00:00Z")
        );
    }
}
