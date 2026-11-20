package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.RefreshSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    void insert(RefreshSession session);

    /** Atomically rotates one unrevoked token; false means replay or a concurrent rotation. */
    boolean rotate(UUID expectedId, Instant expectedLastUsedAt, RefreshSession replacement, Instant now);

    void revokeFamily(UUID familyId, String reason, Instant now);

    void revokeAllForAccount(UUID accountId, String reason, Instant now);
}
