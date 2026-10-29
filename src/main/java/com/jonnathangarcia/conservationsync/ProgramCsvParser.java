package com.jonnathangarcia.conservationsync;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.springframework.stereotype.Component;

@Component
public class ProgramCsvParser {

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
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
}