package com.jonnathangarcia.conservationsync;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramImportService {

    private final JdbcTemplate jdbcTemplate;

    public ProgramImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ImportResult importRows(List<ProgramRecordInput> rows) {
        int created = 0;
        int unchanged = 0;

        for (var row : rows) {
            int affectedRows = jdbcTemplate.update("""
                    INSERT INTO program_record (
                        source_id,
                        region_code,
                        status,
                        area_hectares,
                        source_updated_at
                    )
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (source_id) DO NOTHING
                    """,
                    row.sourceId(),
                    row.regionCode(),
                    row.status(),
                    row.areaHectares(),
                    row.sourceUpdatedAt()
            );

            if (affectedRows == 1) {
                created++;
            } else {
                unchanged++;
            }
        }

        return new ImportResult(
                rows.size(),
                created,
                0,
                unchanged,
                0
        );
    }
}