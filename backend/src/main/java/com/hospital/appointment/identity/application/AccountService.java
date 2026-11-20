package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.AccountProfile;
import com.hospital.appointment.identity.domain.AccountStatus;
import com.hospital.appointment.identity.domain.Role;
import com.hospital.appointment.identity.domain.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher passwords;
    private final SecurityAudit audit;
    private final Clock clock;

    AccountService(AccountRepository accounts, SessionRepository sessions, PasswordHasher passwords, SecurityAudit audit, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwords = passwords;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UserAccount create(String email, String rawPassword, Role role, String firstName, String lastName, String phone, boolean verified) {
        String normalized = UserAccount.normalizeEmail(email);
        if (accounts.findByNormalizedEmail(normalized).isPresent()) {
            throw new IdentityException(HttpStatus.CONFLICT, "email_unavailable", "An account cannot be created with these details.");
        }
        Instant now = Instant.now(clock);
        UserAccount account = new UserAccount(UUID.randomUUID(), normalized, normalized, passwords.hash(rawPassword), role,
                verified ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION, verified, verified ? now : null,
                role == Role.STAFF, now, 1, 0, null, now, now);
        AccountProfile profile = new AccountProfile(account.id(), firstName.trim(), lastName.trim(), phone == null ? null : phone.trim(), now, now);
        return accounts.insert(account, profile);
    }

    @Transactional(readOnly = true)
    public AccountView view(UserAccount account) {
        AccountProfile profile = accounts.findProfile(account.id());
        return new AccountView(account.id(), account.email(), profile.firstName(), profile.lastName(), account.role(), account.status(),
                account.emailVerified(), account.forcePasswordChange(), account.authzVersion(), account.version());
    }

    @Transactional(readOnly = true)
    public AccountView view(UUID accountId) {
        return view(required(accountId));
    }

    @Transactional(readOnly = true)
    public UserAccount required(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new IdentityException(HttpStatus.NOT_FOUND, "account_not_found", "The account was not found."));
    }

    @Transactional(readOnly = true)
    public UserAccount findByEmail(String email) {
        return accounts.findByNormalizedEmail(UserAccount.normalizeEmail(email)).orElse(null);
    }

    @Transactional
    public void markLogin(UserAccount account) {
        accounts.markLogin(account.id(), Instant.now(clock));
    }

    @Transactional
    public void updateVerification(UserAccount account, long expectedVersion) {
        accounts.update(account, accounts.findProfile(account.id()), expectedVersion);
    }

    @Transactional
    public void updatePassword(UserAccount account, long expectedVersion) {
        accounts.update(account, accounts.findProfile(account.id()), expectedVersion);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountView createManagedAccount(UUID actorId, String email, String rawPassword, Role role,
                                            String firstName, String lastName, String phone, String source) {
        UserAccount created = create(email, rawPassword, role, firstName, lastName, phone, true);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("actorAccountId", actorId.toString());
        details.put("targetAccountId", created.id().toString());
        details.put("role", role.name());
        audit.record("admin_account_created", created.id(), source, details);
        return view(created);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountView updateManagedAccount(UUID actorId, UUID accountId, Role role, AccountStatus status, String firstName, String lastName,
                                            String phone, String newPassword, long expectedVersion, String source) {
        UserAccount current = required(accountId);
        UserAccount changed = current;
        Instant now = Instant.now(clock);
        boolean securityChanged = role != current.role() || status != current.status();
        if (securityChanged) {
            changed = new UserAccount(current.id(), current.email(), current.normalizedEmail(), current.passwordHash(), role, status,
                    current.emailVerified(), current.emailVerifiedAt(), role == Role.STAFF, current.passwordChangedAt(),
                    current.authzVersion() + 1, current.version(), current.lastLoginAt(), current.createdAt(), now);
        }
        if (newPassword != null) {
            changed = new UserAccount(changed.id(), changed.email(), changed.normalizedEmail(), passwords.hash(newPassword), changed.role(), changed.status(),
                    changed.emailVerified(), changed.emailVerifiedAt(), changed.role() == Role.STAFF, now, changed.authzVersion() + (securityChanged ? 0 : 1),
                    changed.version(), changed.lastLoginAt(), changed.createdAt(), now);
        }
        AccountProfile oldProfile = accounts.findProfile(accountId);
        AccountProfile profile = new AccountProfile(accountId, firstName.trim(), lastName.trim(), phone, oldProfile.createdAt(), now);
        UserAccount saved = accounts.update(changed, profile, expectedVersion);
        if (securityChanged || newPassword != null) {
            sessions.revokeAllForAccount(accountId, "account_security_changed", now);
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("actorAccountId", actorId.toString());
        details.put("targetAccountId", accountId.toString());
        details.put("previousRole", current.role().name());
        details.put("newRole", role.name());
        details.put("previousStatus", current.status().name());
        details.put("newStatus", status.name());
        details.put("profileChanged", String.valueOf(!profile.equals(oldProfile)));
        audit.record("admin_account_updated", accountId, source, details);
        if (securityChanged) {
            audit.record("admin_authorization_changed", accountId, source, details);
        }
        if (newPassword != null) {
            audit.record("admin_password_replaced", accountId, source, Map.of(
                    "actorAccountId", actorId.toString(), "targetAccountId", accountId.toString()));
        }
        return view(saved);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or (hasRole('PATIENT') and #ownerId.toString() == authentication.name)")
    public void assertPatientOwnership(UUID ownerId) {
        // The executable authorization rule is the annotation; scheduling modules can call this boundary later.
    }
}
