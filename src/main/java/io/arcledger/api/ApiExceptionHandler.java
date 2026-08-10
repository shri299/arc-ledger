package io.arcledger.api;

import io.arcledger.api.ApiModels.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", exception.getMessage(), Instant.now()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", exception.getMessage(), Instant.now()));
    }
}
