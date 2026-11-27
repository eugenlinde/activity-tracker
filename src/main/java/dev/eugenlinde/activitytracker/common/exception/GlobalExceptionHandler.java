package dev.eugenlinde.activitytracker.common.exception;

import dev.eugenlinde.activitytracker.errorreport.ErrorReporter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ObjectProvider<ErrorReporter> errorReporter;

    public GlobalExceptionHandler(ObjectProvider<ErrorReporter> errorReporter) {
        this.errorReporter = errorReporter;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpectedException(Exception exception, HttpServletRequest request) {
        log.error(exception.getMessage(), exception);
        reportUnexpected(exception, request);
        return new ResponseEntity<>("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void reportUnexpected(Exception exception, HttpServletRequest request) {
        try {
            ErrorReporter reporter = errorReporter.getIfAvailable();
            if (reporter == null) {
                return;
            }
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            reporter.report(exception, request.getMethod(), pattern instanceof String text ? text : null);
        } catch (Exception failure) {
            log.warn("Error report failed: {}", failure.getClass().getName());
        }
    }

    @ExceptionHandler(UserExistsException.class)
    public ResponseEntity<Object> userExistsException(UserExistsException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> authenticationException(AuthenticationException exception) {
        return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ActivityNotFoundException.class)
    public ResponseEntity<Object> activityNotFoundException(ActivityNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ShareNotFoundException.class)
    public ResponseEntity<Object> shareNotFoundException(ShareNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Object> userNotFoundException(UserNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> dataIntegrityViolationException(DataIntegrityViolationException exception) {
        log.warn(exception.getMessage(), exception);
        return new ResponseEntity<>("Request conflicts with existing data", HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ShareExistsException.class)
    public ResponseEntity<Object> shareExistsException(ShareExistsException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SelfShareException.class)
    public ResponseEntity<Object> selfShareException(SelfShareException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Object> authorizationDeniedException(AuthorizationDeniedException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Object> storageException(StorageException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<Object> unsupportedStorageTypeException(UnsupportedMediaTypeException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<Object> mediaNotFoundException(MediaNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SignupNotAllowedException.class)
    public ResponseEntity<Object> signupNotAllowedException(SignupNotAllowedException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(WeightEntryNotFoundException.class)
    public ResponseEntity<Object> weightEntryNotFoundException(WeightEntryNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(StepEntryNotFoundException.class)
    public ResponseEntity<Object> stepEntryNotFoundException(StepEntryNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DietEntryNotFoundException.class)
    public ResponseEntity<Object> dietEntryNotFoundException(DietEntryNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidEntryDateException.class)
    public ResponseEntity<Object> invalidEntryDateException(InvalidEntryDateException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<Object> invalidDateRangeException(InvalidDateRangeException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmptyImportFileException.class)
    public ResponseEntity<Object> emptyImportFileException(EmptyImportFileException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ImportTooLargeException.class)
    public ResponseEntity<Object> importTooLargeException(ImportTooLargeException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatusCode.valueOf(413));
    }

    @ExceptionHandler(ImportLimitExceededException.class)
    public ResponseEntity<Object> importLimitExceededException(ImportLimitExceededException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(ImportMappingException.class)
    public ResponseEntity<Object> importMappingException(ImportMappingException exception) {
        log.warn("Import mapping failed: {}", exception.getMessage());
        return new ResponseEntity<>("Could not read this file. Trying again may help; if it keeps failing, the sheet is laid out in a way the import does not understand.", HttpStatus.BAD_GATEWAY);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        BindingResult bindingResult = exception.getBindingResult();
        Map<String, List<String>> errors = new HashMap<>();
        bindingResult.getFieldErrors().forEach(fieldError -> {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
        });
        log.warn("Validation failed: {}", errors);

        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

}
