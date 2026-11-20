package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.AccountRepository;
import com.hospital.appointment.identity.application.IdentityException;
import com.hospital.appointment.identity.domain.AccountProfile;
import com.hospital.appointment.identity.domain.AccountStatus;
import com.hospital.appointment.identity.domain.Role;
import com.hospital.appointment.identity.domain.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAccountRepository implements AccountRepository {
    private static final String COLUMNS = "id, email, normalized_email, password_hash, role, status, email_verified, "
            + "email_verified_at, force_password_change, password_changed_at, authz_version, version, last_login_at, created_at, updated_at";
    private static final RowMapper<UserAccount> ACCOUNT_MAPPER = JdbcAccountRepository::mapAccount;
    private final JdbcTemplate jdbc;

    JdbcAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM identity_account WHERE id = ?", ACCOUNT_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
        return jdbc.query("SELECT " + COLUMNS + " FROM identity_account WHERE normalized_email = ?", ACCOUNT_MAPPER, normalizedEmail)
                .stream().findFirst();
    }

    @Override
    public AccountProfile findProfile(UUID accountId) {
        return jdbc.queryForObject("SELECT account_id, first_name, last_name, phone, created_at, updated_at "
                        + "FROM identity_profile WHERE account_id = ?", (result, row) -> new AccountProfile(
                        result.getObject("account_id", UUID.class), result.getString("first_name"), result.getString("last_name"),
                        result.getString("phone"), instant(result, "created_at"), instant(result, "updated_at")), accountId);
    }

    @Override
    public UserAccount insert(UserAccount account, AccountProfile profile) {
        jdbc.update("INSERT INTO identity_account (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                account.id(), account.email(), account.normalizedEmail(), account.passwordHash(), account.role().name(), account.status().name(),
                account.emailVerified(), timestamp(account.emailVerifiedAt()), account.forcePasswordChange(), timestamp(account.passwordChangedAt()),
                account.authzVersion(), account.version(), timestamp(account.lastLoginAt()), timestamp(account.createdAt()), timestamp(account.updatedAt()));
        jdbc.update("INSERT INTO identity_profile (account_id, first_name, last_name, phone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                account.id(), profile.firstName(), profile.lastName(), profile.phone(), timestamp(profile.createdAt()), timestamp(profile.updatedAt()));
        return account;
    }

    @Override
    public UserAccount update(UserAccount account, AccountProfile profile, long expectedVersion) {
        int changed = jdbc.update("UPDATE identity_account SET email = ?, normalized_email = ?, password_hash = ?, role = ?, status = ?, "
                        + "email_verified = ?, email_verified_at = ?, force_password_change = ?, password_changed_at = ?, authz_version = ?, "
                        + "version = version + 1, last_login_at = ?, updated_at = ? WHERE id = ? AND version = ?",
                account.email(), account.normalizedEmail(), account.passwordHash(), account.role().name(), account.status().name(),
                account.emailVerified(), timestamp(account.emailVerifiedAt()), account.forcePasswordChange(), timestamp(account.passwordChangedAt()),
                account.authzVersion(), timestamp(account.lastLoginAt()), timestamp(account.updatedAt()), account.id(), expectedVersion);
        if (changed != 1) {
            throw IdentityException.conflict();
        }
        jdbc.update("UPDATE identity_profile SET first_name = ?, last_name = ?, phone = ?, updated_at = ? WHERE account_id = ?",
                profile.firstName(), profile.lastName(), profile.phone(), timestamp(profile.updatedAt()), account.id());
        return findById(account.id()).orElseThrow(IdentityException::conflict);
    }

    @Override
    public void markLogin(UUID accountId, Instant at) {
        jdbc.update("UPDATE identity_account SET last_login_at = ?, updated_at = ? WHERE id = ?", timestamp(at), timestamp(at), accountId);
    }

    private static UserAccount mapAccount(ResultSet result, int row) throws SQLException {
        return new UserAccount(result.getObject("id", UUID.class), result.getString("email"), result.getString("normalized_email"),
                result.getString("password_hash"), Role.valueOf(result.getString("role")), AccountStatus.valueOf(result.getString("status")),
                result.getBoolean("email_verified"), instant(result, "email_verified_at"), result.getBoolean("force_password_change"),
                instant(result, "password_changed_at"), result.getLong("authz_version"), result.getLong("version"),
                instant(result, "last_login_at"), instant(result, "created_at"), instant(result, "updated_at"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
