package com.jonnathangarcia.conservationsync;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProgramRecordInput(
        String sourceId,
        String regionCode,
        String status,
        BigDecimal areaHectares,
        OffsetDateTime sourceUpdatedAt
) {
}