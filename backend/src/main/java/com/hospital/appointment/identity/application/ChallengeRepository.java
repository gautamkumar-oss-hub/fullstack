package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.IdentityChallenge;
import com.hospital.appointment.identity.domain.IdentityChallenge.ChallengeType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepository {
    void invalidate(UUID accountId, ChallengeType type, Instant now);

    void insert(IdentityChallenge challenge);

    Optional<IdentityChallenge> latest(UUID accountId, ChallengeType type);

    /** Returns false after expiry, consumption, or the attempt bound has been reached. */
    boolean consume(UUID id, String expectedHash, Instant now, int maximumAttempts);

    void recordAttempt(UUID id, int expectedAttempts, Instant now, int maximumAttempts);
}
