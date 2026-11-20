package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.AccountProfile;
import com.hospital.appointment.identity.domain.UserAccount;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

    AccountProfile findProfile(UUID accountId);

    UserAccount insert(UserAccount account, AccountProfile profile);

    UserAccount update(UserAccount account, AccountProfile profile, long expectedVersion);

    void markLogin(UUID accountId, Instant at);
}
