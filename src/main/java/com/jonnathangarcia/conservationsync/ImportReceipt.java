package com.jonnathangarcia.conservationsync;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportReceipt(UUID id, OffsetDateTime completedAt, ImportResult result) {
}
