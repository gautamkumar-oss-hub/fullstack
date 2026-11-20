package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.SecurityAudit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcSecurityAudit implements SecurityAudit {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper json;

    JdbcSecurityAudit(JdbcTemplate jdbc, Clock clock, tools.jackson.databind.ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
    }

    @Override
    public void record(String eventType, UUID accountId, String sourceAddress, Map<String, String> details) {
        try {
            String detailsJson = json.writeValueAsString(details == null ? Map.of() : details);
            jdbc.update("INSERT INTO identity_security_audit (event_type, account_id, occurred_at, source_address, details) "
                    + "VALUES (?, ?, ?, ?, ?::jsonb)", eventType, accountId, Timestamp.from(Instant.now(clock)), sourceAddress, detailsJson);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write the security audit event", exception);
        }
    }
}
