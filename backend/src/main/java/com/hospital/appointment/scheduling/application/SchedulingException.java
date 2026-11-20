package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.identity.application.IdentityException;
import org.springframework.http.HttpStatus;

/** Safe, stable scheduling failures; no database details cross this boundary. */
public final class SchedulingException extends IdentityException {
    private SchedulingException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static SchedulingException invalid(String message) {
        return new SchedulingException(HttpStatus.BAD_REQUEST, "validation_failed", message);
    }

    public static SchedulingException notFound(String resource) {
        return new SchedulingException(HttpStatus.NOT_FOUND, "resource_not_found", "The " + resource + " was not found.");
    }

    public static SchedulingException conflict() {
        return new SchedulingException(HttpStatus.CONFLICT, "resource_changed", "This resource changed. Reload it and retry.");
    }

    public static SchedulingException duplicate(String message) {
        return new SchedulingException(HttpStatus.CONFLICT, "duplicate_or_overlap", message);
    }
}
