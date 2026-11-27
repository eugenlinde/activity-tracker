package dev.eugenlinde.activitytracker.errorreport.controller;

import dev.eugenlinde.activitytracker.errorreport.ErrorReporter;
import dev.eugenlinde.activitytracker.errorreport.dto.ClientErrorRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client-errors")
public class ClientErrorController {

    private final ErrorReporter errorReporter;

    public ClientErrorController(ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    @PostMapping
    public ResponseEntity<Void> report(@RequestBody @Valid ClientErrorRequest request) {
        errorReporter.reportBrowser(request.name(), request.frames(), request.route());
        return ResponseEntity.noContent().build();
    }
}
