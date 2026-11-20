package com.hospital.appointment.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
        UUID id,
        UUID familyId,
        Instant familyCreatedAt,
        UUID accountId,
        String tokenHash,
        UUID parentId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt,
        Instant rotatedAt,
        Instant revokedAt,
        String revokeReason) {

    public boolean reusableAt(Instant now) {
        return revokedAt != null || rotatedAt != null;
    }
}
