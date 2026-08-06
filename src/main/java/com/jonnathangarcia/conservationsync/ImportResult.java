package com.jonnathangarcia.conservationsync;

public record ImportResult(
        int received,
        int created,
        int updated,
        int unchanged,
        int rejected
) {
}