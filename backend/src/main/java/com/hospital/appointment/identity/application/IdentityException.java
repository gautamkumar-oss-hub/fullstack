package com.hospital.appointment.identity.application;

import org.springframework.http.HttpStatus;

public class IdentityException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public IdentityException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static IdentityException unauthorized() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "The sign-in details are not valid.");
    }

    public static IdentityException conflict() {
        return new IdentityException(HttpStatus.CONFLICT, "account_state_changed", "The account changed. Retry the operation.");
    }
}
