package com.hospital.appointment.identity.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String normalizedEmail,
        String passwordHash,
        Role role,
        AccountStatus status,
        boolean emailVerified,
        Instant emailVerifiedAt,
        boolean forcePasswordChange,
        Instant passwordChangedAt,
        long authzVersion,
        long version,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean canAuthenticate() {
        return status == AccountStatus.ACTIVE && emailVerified;
    }
}
