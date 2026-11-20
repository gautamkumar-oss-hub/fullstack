package com.hospital.appointment.scheduling.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts database constraint races into the same safe scheduling contract as preflight checks. */
@RestControllerAdvice(basePackages = "com.hospital.appointment.scheduling.api")
class SchedulingExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> constraintConflict() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource already exists, overlaps another schedule, or is referenced by an active resource.");
        problem.setTitle("Request conflict");
        problem.setProperty("code", "duplicate_or_overlap");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
