package com.jonnathangarcia.conservationsync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramImportReceiptService {
    private final ProgramImportService importService;
    private final JdbcTemplate jdbcTemplate;

    public ProgramImportReceiptService(ProgramImportService importService, JdbcTemplate jdbcTemplate) {
        this.importService = importService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ImportReceipt importAndRecord(List<ProgramCsvRow> rows) {
        // The existing import joins this transaction; receipt failure rolls it all back.
        var result = importService.importCsvRows(rows);
        return jdbcTemplate.queryForObject("""
                INSERT INTO import_receipt (id, received, created, updated, unchanged, rejected)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id, completed_at, received, created, updated, unchanged, rejected
                """,
                this::mapReceipt,
                UUID.randomUUID(), result.received(), result.created(), result.updated(),
                result.unchanged(), result.rejected());
    }

    @Transactional(readOnly = true)
    public Optional<ImportReceipt> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, completed_at, received, created, updated, unchanged, rejected
                FROM import_receipt WHERE id = ?
                """, this::mapReceipt, id).stream().findFirst();
    }

    private ImportReceipt mapReceipt(ResultSet row, int rowNumber) throws SQLException {
        return new ImportReceipt(
                row.getObject("id", UUID.class),
                row.getObject("completed_at", OffsetDateTime.class),
                new ImportResult(row.getInt("received"), row.getInt("created"),
                        row.getInt("updated"), row.getInt("unchanged"), row.getInt("rejected")));
    }
}
