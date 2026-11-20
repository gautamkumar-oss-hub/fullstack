package com.hospital.appointment.identity.api;

import com.hospital.appointment.identity.application.IdentityException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IdentityExceptionHandler {

    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ProblemDetail> identity(IdentityException exception) {
        return response(exception.status(), exception.getMessage(), exception.code());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the submitted fields and try again.");
        detail.setTitle("Invalid request");
        detail.setProperty("code", "validation_failed");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(), (first, second) -> first)));
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> malformed(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "The request could not be understood.", "invalid_request");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", "forbidden");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "The resource could not be changed with these details.", "resource_conflict");
    }

    private static ResponseEntity<ProblemDetail> response(HttpStatus status, String message, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("code", code);
        return ResponseEntity.status(status).body(detail);
    }
}
