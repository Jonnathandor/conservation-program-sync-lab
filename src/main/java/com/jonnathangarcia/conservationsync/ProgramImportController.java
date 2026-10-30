package com.jonnathangarcia.conservationsync;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ProgramImportController {

    private final ProgramCsvParser csvParser;
    private final ProgramImportReceiptService receiptService;

    public ProgramImportController(
            ProgramCsvParser csvParser,
            ProgramImportReceiptService receiptService
    ) {
        this.csvParser = csvParser;
        this.receiptService = receiptService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importPrograms(
            @RequestPart("file") MultipartFile file
    ) throws IOException {

        var rows = csvParser.parse(file.getInputStream());

        var receipt = receiptService.importAndRecord(rows);
        return ResponseEntity.created(URI.create("/api/imports/" + receipt.id()))
                .body(receipt.result());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findReceipt(@PathVariable UUID id) {
        var receipt = receiptService.findById(id);
        if (receipt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    new ImportError("IMPORT_RECEIPT_NOT_FOUND", "No completed import receipt exists for this ID."));
        }
        return ResponseEntity.ok(receipt.get());
    }

    @ExceptionHandler(InvalidCsvHeadersException.class)
    public ResponseEntity<ImportError> invalidHeaders(InvalidCsvHeadersException exception) {
        return ResponseEntity.badRequest().body(
                new ImportError("INVALID_CSV_HEADERS", exception.getMessage()));
    }

    public record ImportError(String code, String message) {
    }
}
