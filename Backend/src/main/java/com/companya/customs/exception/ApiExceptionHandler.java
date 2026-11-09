package com.companya.customs.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    record ErrorResponse(Instant timestamp, int status, String error, String message) { }
    @ExceptionHandler(ApiExceptions.NotFound.class) ResponseEntity<ErrorResponse> notFound(ApiExceptions.NotFound ex) { return error(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler({ApiExceptions.Validation.class, MethodArgumentNotValidException.class, ConstraintViolationException.class}) ResponseEntity<ErrorResponse> validation(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException e ? e.getBindingResult().getFieldErrors().stream().map(x -> x.getField() + ": " + x.getDefaultMessage()).collect(Collectors.joining(", ")) : ex.getMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }
    @ExceptionHandler(ApiExceptions.ExportBlocked.class) ResponseEntity<ErrorResponse> blocked(ApiExceptions.ExportBlocked ex) { return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()); }
    @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> generic(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
