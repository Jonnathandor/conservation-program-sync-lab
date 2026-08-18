package com.jonnathangarcia.conservationsync;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ProgramImportController {

    private final ProgramCsvParser csvParser;
    private final ProgramImportService importService;

    public ProgramImportController(
            ProgramCsvParser csvParser,
            ProgramImportService importService
    ) {
        this.csvParser = csvParser;
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importPrograms(
            @RequestPart("file") MultipartFile file
    ) throws IOException {

        var rows = csvParser.parse(file.getInputStream());

        return importService.importCsvRows(rows);
    }
}