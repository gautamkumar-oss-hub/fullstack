package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.ChallengeRepository;
import com.hospital.appointment.identity.domain.IdentityChallenge;
import com.hospital.appointment.identity.domain.IdentityChallenge.ChallengeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcChallengeRepository implements ChallengeRepository {
    private static final String COLUMNS = "id, account_id, challenge_type, secret_hash, created_at, expires_at, consumed_at, attempts, send_count, next_resend_at";
    private final JdbcTemplate jdbc;

    JdbcChallengeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void invalidate(UUID accountId, ChallengeType type, Instant now) {
        jdbc.update("UPDATE identity_challenge SET consumed_at = ? WHERE account_id = ? AND challenge_type = ? AND consumed_at IS NULL",
                timestamp(now), accountId, type.name());
    }

    @Override
    public void insert(IdentityChallenge challenge) {
        jdbc.update("INSERT INTO identity_challenge (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                challenge.id(), challenge.accountId(), challenge.type().name(), challenge.secretHash(), timestamp(challenge.createdAt()),
                timestamp(challenge.expiresAt()), timestamp(challenge.consumedAt()), challenge.attempts(), challenge.sendCount(), timestamp(challenge.nextResendAt()));
    }

    @Override
    public Optional<IdentityChallenge> latest(UUID accountId, ChallengeType type) {
        return jdbc.query("SELECT " + COLUMNS + " FROM identity_challenge WHERE account_id = ? AND challenge_type = ? "
                + "ORDER BY created_at DESC LIMIT 1", this::map, accountId, type.name()).stream().findFirst();
    }

    @Override
    public boolean consume(UUID id, String expectedHash, Instant now, int maximumAttempts) {
        return jdbc.update("UPDATE identity_challenge SET consumed_at = ? WHERE id = ? AND secret_hash = ? "
                + "AND consumed_at IS NULL AND expires_at > ? AND attempts < ?", timestamp(now), id, expectedHash, timestamp(now), maximumAttempts) == 1;
    }

    @Override
    public void recordAttempt(UUID id, int expectedAttempts, Instant now, int maximumAttempts) {
        jdbc.update("UPDATE identity_challenge SET attempts = attempts + 1 WHERE id = ? AND attempts = ? "
                + "AND consumed_at IS NULL AND expires_at > ? AND attempts < ?", id, expectedAttempts, timestamp(now), maximumAttempts);
    }

    private IdentityChallenge map(ResultSet result, int row) throws SQLException {
        return new IdentityChallenge(result.getObject("id", UUID.class), result.getObject("account_id", UUID.class),
                ChallengeType.valueOf(result.getString("challenge_type")), result.getString("secret_hash"), instant(result, "created_at"),
                instant(result, "expires_at"), instant(result, "consumed_at"), result.getInt("attempts"), result.getInt("send_count"),
                instant(result, "next_resend_at"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
