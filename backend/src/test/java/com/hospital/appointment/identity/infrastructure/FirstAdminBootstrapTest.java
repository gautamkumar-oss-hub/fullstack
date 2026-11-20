package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.AccountService;
import com.hospital.appointment.identity.application.SecurityAudit;
import com.hospital.appointment.identity.domain.Role;
import com.hospital.appointment.identity.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirstAdminBootstrapTest {
    @Test
    void bootstrapAuditsActorAndTargetWithoutPasswordMaterial() {
        IdentityProperties properties = new IdentityProperties("issuer", "audience", "key", null, null,
                Duration.ofMinutes(10), Duration.ofDays(7), Duration.ofDays(30), Duration.ofHours(24), Duration.ofMinutes(15),
                Duration.ofMinutes(5), 5, Duration.ofMinutes(1), true, false, "",
                new IdentityProperties.Bootstrap(true, "first-admin@example.test", "Bootstrap-secret-2026",
                        "First", "Admin"), List.of("http://localhost:5173"));
        AccountService accounts = Mockito.mock(AccountService.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        SecurityAudit audit = Mockito.mock(SecurityAudit.class);
        UUID adminId = UUID.randomUUID();
        Instant now = Instant.now(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        UserAccount admin = new UserAccount(adminId, properties.bootstrap().email(), properties.bootstrap().email(),
                "test-only-hash", Role.ADMIN, com.hospital.appointment.identity.domain.AccountStatus.ACTIVE, true, now,
                false, now, 1, 0, null, now, now);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(accounts.create(anyString(), anyString(), eq(Role.ADMIN), anyString(), anyString(), any(), eq(true))).thenReturn(admin);

        new FirstAdminBootstrap(properties, accounts, jdbc, audit).run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq("first_admin_bootstrapped"), eq(adminId), eq("local_bootstrap"), details.capture());
        assertThat(details.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "actor", "system_bootstrap", "targetAccountId", adminId.toString()));
        assertThat(details.getValue().toString()).doesNotContain(properties.bootstrap().password(), admin.passwordHash());
    }
}
