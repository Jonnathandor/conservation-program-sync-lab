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
        int updated = 0;
        int unchanged = 0;

        for (var row : rows) {
            int insertedRows = jdbcTemplate.update("""
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

            if (insertedRows == 1) {
                created++;
                continue;
            }

            int updatedRows = jdbcTemplate.update("""
                    UPDATE program_record
                    SET region_code = ?,
                        status = ?,
                        area_hectares = ?,
                        source_updated_at = ?
                    WHERE source_id = ?
                    AND source_updated_at < ?
                    """,
                    row.regionCode(),
                    row.status(),
                    row.areaHectares(),
                    row.sourceUpdatedAt(),
                    row.sourceId(),
                    row.sourceUpdatedAt()
            );

            if (updatedRows == 1) {
                updated++;
            } else {
                unchanged++;
            }
        }

        return new ImportResult(
                rows.size(),
                created,
                updated,
                unchanged,
                0
        );
    }
}