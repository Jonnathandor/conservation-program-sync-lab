package com.jonnathangarcia.conservationsync;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

@Component
public class ProgramCsvParser {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "source_id", "region_code", "status", "area_hectares", "source_updated_at");

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            // Keep every header name available so our validation handles blanks/duplicates.
            .setAllowMissingColumnNames(true)
            .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
            .get();

    public List<ProgramCsvRow> parse(InputStream inputStream)
            throws IOException {

        var rows = new ArrayList<ProgramCsvRow>();

        try (
                var reader = new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8
                );
                var parser = FORMAT.parse(reader)
        ) {
            validateHeaders(parser.getHeaderNames());

            for (var csvRecord : parser) {
                rows.add(new ProgramCsvRow(
                        csvRecord.get("source_id"),
                        csvRecord.get("region_code"),
                        csvRecord.get("status"),
                        csvRecord.get("area_hectares"),
                        csvRecord.get("source_updated_at")
                ));
            }
        }

        return rows;
    }

    private void validateHeaders(List<String> headers) {
        var uniqueHeaders = new HashSet<>(headers);
        if (headers.stream().anyMatch(String::isBlank)
                || uniqueHeaders.size() != headers.size()
                || !uniqueHeaders.equals(REQUIRED_HEADERS)) {
            throw new InvalidCsvHeadersException();
        }
    }
}
