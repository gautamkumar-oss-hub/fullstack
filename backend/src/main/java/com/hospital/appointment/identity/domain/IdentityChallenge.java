package com.hospital.appointment.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record IdentityChallenge(
        UUID id,
        UUID accountId,
        ChallengeType type,
        String secretHash,
        Instant createdAt,
        Instant expiresAt,
        Instant consumedAt,
        int attempts,
        int sendCount,
        Instant nextResendAt) {

    public enum ChallengeType {
        EMAIL_VERIFICATION,
        PASSWORD_RECOVERY,
        MFA
    }
}
