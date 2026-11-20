package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.AccountStatus;
import com.hospital.appointment.identity.domain.IdentityChallenge;
import com.hospital.appointment.identity.domain.IdentityChallenge.ChallengeType;
import com.hospital.appointment.identity.domain.RefreshSession;
import com.hospital.appointment.identity.domain.Role;
import com.hospital.appointment.identity.domain.UserAccount;
import com.hospital.appointment.identity.infrastructure.AccessTokenService;
import com.hospital.appointment.identity.infrastructure.IdentityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    public static final String REFRESH_COOKIE = "hospital_refresh";
    private final AccountService accountService;
    private final SessionRepository sessions;
    private final ChallengeRepository challenges;
    private final PasswordHasher passwords;
    private final TokenHasher tokenHasher;
    private final OpaqueTokenService tokens;
    private final AccessTokenService accessTokens;
    private final IdentityProperties properties;
    private final IdentityEmailSender emailSender;
    private final SecurityAudit audit;
    private final Clock clock;
    private final String dummyHash;
    private final DevelopmentCodeService developmentCodes;

    AuthService(AccountService accountService, SessionRepository sessions, ChallengeRepository challenges,
                PasswordHasher passwords, TokenHasher tokenHasher, OpaqueTokenService tokens, AccessTokenService accessTokens,
                IdentityProperties properties, IdentityEmailSender emailSender, SecurityAudit audit, Clock clock,
                DevelopmentCodeService developmentCodes, @Value("${application.identity.dummy-password-hash}") String dummyHash) {
        this.accountService = accountService;
        this.sessions = sessions;
        this.challenges = challenges;
        this.passwords = passwords;
        this.tokenHasher = tokenHasher;
        this.tokens = tokens;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.emailSender = emailSender;
        this.audit = audit;
        this.clock = clock;
        this.developmentCodes = developmentCodes;
        this.dummyHash = dummyHash;
    }

    @Transactional
    public ChallengeReceipt register(String email, String password, String firstName, String lastName, String phone, String source) {
        if (accountService.findByEmail(email) != null) {
            audit.record("registration_duplicate", null, source);
            return new ChallengeReceipt(null);
        }
        UserAccount account = accountService.create(email, password, Role.PATIENT, firstName, lastName, phone, false);
        String developmentCode = createChallenge(account, ChallengeType.EMAIL_VERIFICATION, properties.verificationLifetime());
        audit.record("patient_registered", account.id(), source);
        return new ChallengeReceipt(developmentCode);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public LoginResult login(String email, String password, String source) {
        UserAccount account = accountService.findByEmail(email);
        if (account == null || !passwords.matches(password, account == null ? dummyHash : account.passwordHash())) {
            audit.record("login_failed", account == null ? null : account.id(), source);
            throw IdentityException.unauthorized();
        }
        if (!account.canAuthenticate()) {
            audit.record("login_unavailable", account.id(), source);
            throw IdentityException.unauthorized();
        }
        if (account.role() != Role.PATIENT && !properties.mfaBypass()) {
            String developmentCode = createChallenge(account, ChallengeType.MFA, properties.mfaLifetime());
            audit.record("mfa_challenge_created", account.id(), source);
            return new LoginResult(null, true, developmentCode);
        }
        AuthSession session = issueSession(account);
        accountService.markLogin(account);
        audit.record("login_succeeded", account.id(), source);
        return new LoginResult(session, false, null);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public AuthSession verifyMfa(String email, String code, String source) {
        UserAccount account = accountService.findByEmail(email);
        if (account == null || !account.canAuthenticate() || account.role() == Role.PATIENT) {
            auditFailure("mfa_failed", account, source, "account_unavailable", ChallengeType.MFA);
            throw IdentityException.unauthorized();
        }
        IdentityChallenge challenge;
        try {
            challenge = findUsableChallenge(account, code, ChallengeType.MFA);
            consume(challenge, code);
        } catch (IdentityException exception) {
            auditFailure("mfa_failed", account, source, exception.code(), ChallengeType.MFA);
            throw exception;
        }
        AuthSession session = issueSession(account);
        accountService.markLogin(account);
        audit.record("mfa_succeeded", account.id(), source);
        return session;
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public AuthSession refresh(String rawToken, String source) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            throw IdentityException.unauthorized();
        }
        RefreshSession current = sessions.findByTokenHash(tokenHasher.hash(rawToken)).orElseThrow(IdentityException::unauthorized);
        Instant now = Instant.now(clock);
        if (current.reusableAt(now)) {
            sessions.revokeFamily(current.familyId(), "refresh_token_reuse", now);
            audit.record("refresh_reuse_detected", current.accountId(), source, Map.of("familyId", current.familyId().toString()));
            throw IdentityException.unauthorized();
        }
        if (!current.idleExpiresAt().isAfter(now) || !current.absoluteExpiresAt().isAfter(now)) {
            sessions.revokeFamily(current.familyId(), "refresh_expired", now);
            throw IdentityException.unauthorized();
        }
        UserAccount account = accountService.required(current.accountId());
        if (!account.canAuthenticate()) {
            sessions.revokeAllForAccount(account.id(), "account_unavailable", now);
            throw IdentityException.unauthorized();
        }
        if (account.forcePasswordChange()) {
            return new AuthSession(accessTokens.issue(account), properties.accessTokenLifetime().toSeconds(),
                    accountService.view(account), rawToken, tokens.newCsrfToken(), false);
        }
        String rawReplacement = tokens.newRefreshToken();
        RefreshSession replacement = new RefreshSession(UUID.randomUUID(), current.familyId(), current.familyCreatedAt(), account.id(), tokenHasher.hash(rawReplacement),
                current.id(), now, now, now.plus(properties.refreshIdleLifetime()), current.absoluteExpiresAt(), null, null, null);
        if (!sessions.rotate(current.id(), current.lastUsedAt(), replacement, now)) {
            sessions.revokeFamily(current.familyId(), "concurrent_refresh_reuse", now);
            audit.record("refresh_reuse_detected", account.id(), source);
            throw IdentityException.unauthorized();
        }
        audit.record("refresh_rotated", account.id(), source);
        return new AuthSession(accessTokens.issue(account), properties.accessTokenLifetime().toSeconds(), accountService.view(account),
                rawReplacement, tokens.newCsrfToken(), false);
    }

    @Transactional
    public void logout(String rawToken, String source) {
        if (rawToken != null && !rawToken.isBlank() && rawToken.length() <= 200) {
            sessions.findByTokenHash(tokenHasher.hash(rawToken)).ifPresent(session -> {
                sessions.revokeFamily(session.familyId(), "logout", Instant.now(clock));
                audit.record("logout", session.accountId(), source);
            });
        }
    }

    @Transactional
    public ChallengeReceipt requestVerification(String email, String source) {
        UserAccount account = accountService.findByEmail(email);
        if (account == null || account.emailVerified() || account.status() == AccountStatus.SUSPENDED
                || resendThrottled(account, ChallengeType.EMAIL_VERIFICATION)) {
            return new ChallengeReceipt(null);
        }
        String code = createChallenge(account, ChallengeType.EMAIL_VERIFICATION, properties.verificationLifetime());
        audit.record("verification_requested", account.id(), source);
        return new ChallengeReceipt(code);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public void verifyEmail(String email, String code, String source) {
        UserAccount current = accountService.findByEmail(email);
        if (current == null) {
            auditFailure("verification_failed", null, source, "invalid_challenge", ChallengeType.EMAIL_VERIFICATION);
            throw new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired.");
        }
        if (current.status() == AccountStatus.SUSPENDED) {
            auditFailure("verification_failed", current, source, "account_suspended", ChallengeType.EMAIL_VERIFICATION);
            throw IdentityException.unauthorized();
        }
        try {
            IdentityChallenge challenge = findUsableChallenge(current, code, ChallengeType.EMAIL_VERIFICATION);
            consume(challenge, code);
            Instant now = Instant.now(clock);
            AccountStatus resultingStatus = current.status() == AccountStatus.PENDING_VERIFICATION
                    ? AccountStatus.ACTIVE : current.status();
            UserAccount changed = new UserAccount(current.id(), current.email(), current.normalizedEmail(), current.passwordHash(), current.role(),
                    resultingStatus, true, current.emailVerifiedAt() == null ? now : current.emailVerifiedAt(),
                    current.forcePasswordChange(), current.passwordChangedAt(), current.authzVersion() + 1,
                    current.version(), current.lastLoginAt(), current.createdAt(), now);
            accountService.updateVerification(changed, current.version());
        } catch (IdentityException exception) {
            auditFailure("verification_failed", current, source, exception.code(), ChallengeType.EMAIL_VERIFICATION);
            throw exception;
        }
        audit.record("email_verified", current.id(), source);
    }

    @Transactional
    public ChallengeReceipt requestRecovery(String email, String source) {
        UserAccount account = accountService.findByEmail(email);
        if (account == null || account.status() == AccountStatus.SUSPENDED || resendThrottled(account, ChallengeType.PASSWORD_RECOVERY)) {
            return new ChallengeReceipt(null);
        }
        String code = createChallenge(account, ChallengeType.PASSWORD_RECOVERY, properties.recoveryLifetime());
        audit.record("recovery_requested", account.id(), source);
        return new ChallengeReceipt(code);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public void resetPassword(String email, String code, String newPassword, String source) {
        UserAccount current = accountService.findByEmail(email);
        if (current == null) {
            auditFailure("recovery_failed", null, source, "invalid_challenge", ChallengeType.PASSWORD_RECOVERY);
            throw new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired.");
        }
        if (current.status() == AccountStatus.SUSPENDED) {
            auditFailure("recovery_failed", current, source, "account_suspended", ChallengeType.PASSWORD_RECOVERY);
            throw IdentityException.unauthorized();
        }
        try {
            IdentityChallenge challenge = findUsableChallenge(current, code, ChallengeType.PASSWORD_RECOVERY);
            consume(challenge, code);
            if (current.status() == AccountStatus.SUSPENDED) {
                throw IdentityException.unauthorized();
            }
            Instant now = Instant.now(clock);
            // A recovery code proves mailbox control, so recovery completes pending verification; suspension is never cleared.
            AccountStatus resultingStatus = current.status() == AccountStatus.PENDING_VERIFICATION
                    ? AccountStatus.ACTIVE : current.status();
            UserAccount changed = new UserAccount(current.id(), current.email(), current.normalizedEmail(), passwords.hash(newPassword), current.role(),
                    resultingStatus, true, current.emailVerifiedAt() == null ? now : current.emailVerifiedAt(), false, now,
                    current.authzVersion() + 1, current.version(), current.lastLoginAt(), current.createdAt(), now);
            accountService.updatePassword(changed, current.version());
            sessions.revokeAllForAccount(current.id(), "password_recovery", now);
            challenges.invalidate(current.id(), ChallengeType.MFA, now);
        } catch (IdentityException exception) {
            auditFailure("recovery_failed", current, source, exception.code(), ChallengeType.PASSWORD_RECOVERY);
            throw exception;
        }
        audit.record("password_recovered", current.id(), source);
    }

    @Transactional
    public void changePassword(UUID accountId, String currentPassword, String newPassword, String source) {
        UserAccount current = accountService.required(accountId);
        if (!passwords.matches(currentPassword, current.passwordHash()) || !current.canAuthenticate()) {
            throw IdentityException.unauthorized();
        }
        Instant now = Instant.now(clock);
        UserAccount changed = new UserAccount(current.id(), current.email(), current.normalizedEmail(), passwords.hash(newPassword), current.role(),
                current.status(), current.emailVerified(), current.emailVerifiedAt(), false, now, current.authzVersion() + 1,
                current.version(), current.lastLoginAt(), current.createdAt(), now);
        accountService.updatePassword(changed, current.version());
        sessions.revokeAllForAccount(accountId, "password_changed", now);
        audit.record("password_changed", accountId, source);
    }

    private AuthSession issueSession(UserAccount account) {
        Instant now = Instant.now(clock);
        String refresh = tokens.newRefreshToken();
        UUID familyId = UUID.randomUUID();
        sessions.insert(new RefreshSession(UUID.randomUUID(), familyId, now, account.id(), tokenHasher.hash(refresh), null, now, now,
                now.plus(properties.refreshIdleLifetime()), now.plus(properties.refreshAbsoluteLifetime()), null, null, null));
        return new AuthSession(accessTokens.issue(account), properties.accessTokenLifetime().toSeconds(), accountService.view(account),
                refresh, tokens.newCsrfToken(), true);
    }

    private boolean resendThrottled(UserAccount account, ChallengeType type) {
        Instant now = Instant.now(clock);
        return challenges.latest(account.id(), type)
                .filter(challenge -> challenge.consumedAt() == null && challenge.nextResendAt() != null && challenge.nextResendAt().isAfter(now))
                .isPresent();
    }

    private String createChallenge(UserAccount account, ChallengeType type, Duration lifetime) {
        Instant now = Instant.now(clock);
        challenges.latest(account.id(), type).filter(challenge -> challenge.consumedAt() == null && challenge.nextResendAt() != null
                && challenge.nextResendAt().isAfter(now)).ifPresent(challenge -> {
            throw new IdentityException(HttpStatus.TOO_MANY_REQUESTS, "resend_throttled", "Please wait before requesting another code.");
        });
        challenges.invalidate(account.id(), type, now);
        String code = developmentCodes.code() != null ? developmentCodes.code() : tokens.newOtp();
        challenges.insert(new IdentityChallenge(UUID.randomUUID(), account.id(), type, tokenHasher.hash(code), now, now.plus(lifetime),
                null, 0, 1, now.plus(properties.resendInterval())));
        switch (type) {
            case EMAIL_VERIFICATION -> emailSender.sendVerification(account, code);
            case PASSWORD_RECOVERY -> emailSender.sendRecovery(account, code);
            case MFA -> emailSender.sendMfa(account, code);
        }
        return developmentCodes.code();
    }

    private IdentityChallenge findUsableChallenge(UserAccount account, String code, ChallengeType type) {
        if (account == null || code == null || !code.matches("[0-9]{6}")) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired.");
        }
        IdentityChallenge challenge = challenges.latest(account.id(), type)
                .filter(candidate -> candidate.consumedAt() == null)
                .orElseThrow(() -> new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired."));
        Instant now = Instant.now(clock);
        if (!challenge.expiresAt().isAfter(now) || challenge.attempts() >= properties.maximumChallengeAttempts()) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired.");
        }
        if (!challenge.secretHash().equals(tokenHasher.hash(code))) {
            challenges.recordAttempt(challenge.id(), challenge.attempts(), now, properties.maximumChallengeAttempts());
            throw new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired.");
        }
        return challenge;
    }

    private void consume(IdentityChallenge challenge, String code) {
        Instant now = Instant.now(clock);
        if (challenge.consumedAt() != null || !challenge.expiresAt().isAfter(now) || challenge.attempts() >= properties.maximumChallengeAttempts()) {
            throw new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired.");
        }
        // A matching hash is consumed atomically; replay after a successful request always fails.
        if (!challenges.consume(challenge.id(), tokenHasher.hash(code), now, properties.maximumChallengeAttempts())) {
            challenges.recordAttempt(challenge.id(), challenge.attempts(), now, properties.maximumChallengeAttempts());
            throw new IdentityException(HttpStatus.BAD_REQUEST, "invalid_challenge", "The verification code is invalid or expired.");
        }
    }

    private void auditFailure(String eventType, UserAccount account, String source, String reason, ChallengeType type) {
        audit.record(eventType, account == null ? null : account.id(), source,
                Map.of("reason", reason, "challengeType", type.name()));
    }

    public record LoginResult(AuthSession session, boolean mfaRequired, String developmentCode) {
    }

    public record ChallengeReceipt(String developmentCode) {
    }
}
