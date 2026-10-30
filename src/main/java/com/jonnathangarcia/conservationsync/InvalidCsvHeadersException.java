package com.jonnathangarcia.conservationsync;

public class InvalidCsvHeadersException extends RuntimeException {
    public InvalidCsvHeadersException() {
        super("CSV headers must contain source_id, region_code, status, area_hectares, "
                + "and source_updated_at exactly once, in any order, with no blank or extra names.");
    }
}
