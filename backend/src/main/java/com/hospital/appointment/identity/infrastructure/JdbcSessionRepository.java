package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.SessionRepository;
import com.hospital.appointment.identity.domain.RefreshSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcSessionRepository implements SessionRepository {
    private static final String COLUMNS = "id, family_id, family_created_at, account_id, token_hash, parent_id, created_at, last_used_at, "
            + "idle_expires_at, absolute_expires_at, rotated_at, revoked_at, revoke_reason";
    private final JdbcTemplate jdbc;

    JdbcSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RefreshSession> findByTokenHash(String tokenHash) {
        return jdbc.query("SELECT " + COLUMNS + " FROM identity_refresh_session WHERE token_hash = ?", this::map, tokenHash)
                .stream().findFirst();
    }

    @Override
    public void insert(RefreshSession session) {
        jdbc.update("INSERT INTO identity_refresh_session (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                session.id(), session.familyId(), timestamp(session.familyCreatedAt()), session.accountId(), session.tokenHash(), session.parentId(), timestamp(session.createdAt()),
                timestamp(session.lastUsedAt()), timestamp(session.idleExpiresAt()), timestamp(session.absoluteExpiresAt()),
                timestamp(session.rotatedAt()), timestamp(session.revokedAt()), session.revokeReason());
    }

    @Override
    public boolean rotate(UUID expectedId, Instant expectedLastUsedAt, RefreshSession replacement, Instant now) {
        int changed = jdbc.update("UPDATE identity_refresh_session SET rotated_at = ?, last_used_at = ? "
                        + "WHERE id = ? AND last_used_at = ? AND rotated_at IS NULL AND revoked_at IS NULL "
                        + "AND idle_expires_at > ? AND absolute_expires_at > ?",
                timestamp(now), timestamp(now), expectedId, timestamp(expectedLastUsedAt), timestamp(now), timestamp(now));
        if (changed == 1) {
            insert(replacement);
            return true;
        }
        return false;
    }

    @Override
    public void revokeFamily(UUID familyId, String reason, Instant now) {
        jdbc.update("UPDATE identity_refresh_session SET revoked_at = COALESCE(revoked_at, ?), revoke_reason = ? "
                + "WHERE family_id = ? AND revoked_at IS NULL", timestamp(now), reason, familyId);
    }

    @Override
    public void revokeAllForAccount(UUID accountId, String reason, Instant now) {
        jdbc.update("UPDATE identity_refresh_session SET revoked_at = COALESCE(revoked_at, ?), revoke_reason = ? "
                + "WHERE account_id = ? AND revoked_at IS NULL", timestamp(now), reason, accountId);
    }

    private RefreshSession map(ResultSet result, int row) throws SQLException {
        return new RefreshSession(result.getObject("id", UUID.class), result.getObject("family_id", UUID.class),
                instant(result, "family_created_at"), result.getObject("account_id", UUID.class), result.getString("token_hash"), result.getObject("parent_id", UUID.class),
                instant(result, "created_at"), instant(result, "last_used_at"), instant(result, "idle_expires_at"),
                instant(result, "absolute_expires_at"), instant(result, "rotated_at"), instant(result, "revoked_at"),
                result.getString("revoke_reason"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
