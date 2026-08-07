package com.jonnathangarcia.conservationsync;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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

    public List<ProgramRecordInput> parse(InputStream inputStream)
            throws IOException {

        var rows = new ArrayList<ProgramRecordInput>();

        try (
                var reader = new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8
                );
                var parser = FORMAT.parse(reader)
        ) {
            for (var csvRecord : parser) {
                rows.add(new ProgramRecordInput(
                        csvRecord.get("source_id"),
                        csvRecord.get("region_code"),
                        csvRecord.get("status"),
                        new BigDecimal(csvRecord.get("area_hectares")),
                        OffsetDateTime.parse(
                                csvRecord.get("source_updated_at")
                        )
                ));
            }
        }

        return rows;
    }
}