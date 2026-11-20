package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.application.AccountService;
import com.hospital.appointment.identity.application.SecurityAudit;
import com.hospital.appointment.identity.domain.Role;
import com.hospital.appointment.identity.domain.UserAccount;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(10)
class FirstAdminBootstrap implements ApplicationRunner {
    private final IdentityProperties properties;
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final SecurityAudit audit;

    FirstAdminBootstrap(IdentityProperties properties, AccountService accounts, JdbcTemplate jdbc, SecurityAudit audit) {
        this.properties = properties;
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.bootstrap().enabled()) {
            return;
        }
        jdbc.execute("LOCK TABLE identity_account IN SHARE ROW EXCLUSIVE MODE");
        Integer administrators = jdbc.queryForObject("SELECT count(*) FROM identity_account WHERE role = 'ADMIN'", Integer.class);
        if (administrators == null || administrators == 0) {
            if (accounts.findByEmail(properties.bootstrap().email()) != null) {
                throw new IllegalStateException("Bootstrap email is already used by a non-administrator account.");
            }
            UserAccount admin = accounts.create(properties.bootstrap().email(), properties.bootstrap().password(), Role.ADMIN,
                    properties.bootstrap().firstName(), properties.bootstrap().lastName(), null, true);
            audit.record("first_admin_bootstrapped", admin.id(), "local_bootstrap",
                    Map.of("actor", "system_bootstrap", "targetAccountId", admin.id().toString()));
            jdbc.update("INSERT INTO application_metadata (metadata_key, description) VALUES (?, ?) ON CONFLICT (metadata_key) DO NOTHING",
                    "first_admin_bootstrapped", "Initial administrator account " + admin.id() + "; bootstrap can now be disabled.");
        }
    }
}
