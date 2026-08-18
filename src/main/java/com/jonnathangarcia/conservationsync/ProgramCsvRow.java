package com.jonnathangarcia.conservationsync;

public record ProgramCsvRow(
        String sourceId,
        String regionCode,
        String status,
        String areaHectares,
        String sourceUpdatedAt
) {
}