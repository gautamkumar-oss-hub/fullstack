package com.hospital.appointment.identity;

import com.hospital.appointment.identity.infrastructure.IdentityProperties;
import com.hospital.appointment.identity.infrastructure.IdentityStartupGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityStartupGuardTest {
    @Test
    void acceptsValidProductionSmtpConfiguration() {
        assertThatCode(() -> guard(properties(true, false), productionEnvironment()).run(null)).doesNotThrowAnyException();
    }

    @Test
    void requiresDevelopmentEmailToBeIsolatedFromNormalProfiles() {
        MockEnvironment environment = productionEnvironment().withProperty("application.identity.email.provider", "development");
        assertThatThrownBy(() -> guard(properties(true, false), environment).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SMTP");
    }

    @Test
    void requiresCompleteSmtpConfiguration() {
        MockEnvironment environment = productionEnvironment().withProperty("spring.mail.host", "");
        assertThatThrownBy(() -> guard(properties(true, false), environment).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("spring.mail.host");
    }

    @Test
    void refusesInsecureCookiesAndMfaBypassOutsideDevelopment() {
        assertThatThrownBy(() -> guard(properties(false, false), productionEnvironment()).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Secure cookies");
        assertThatThrownBy(() -> guard(properties(true, true), productionEnvironment()).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Secure cookies");
    }

    @Test
    void refusesMisconfiguredMfaBypassAndInsecureRemoteOrigins() {
        MockEnvironment development = new MockEnvironment().withProperty("spring.profiles.active", "dev,identity-dev");
        assertThatCode(() -> guard(properties(false, false), development).run(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard(properties(true, true), development).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MFA bypass");
        IdentityProperties remote = new IdentityProperties("issuer", "audience", "key", null, null,
                Duration.ofMinutes(10), Duration.ofDays(7), Duration.ofDays(30), Duration.ofHours(24), Duration.ofMinutes(15),
                Duration.ofMinutes(5), 5, Duration.ofMinutes(1), false, false, "",
                new IdentityProperties.Bootstrap(false, null, null, "System", "Administrator"), List.of("https://example.test"));
        assertThatThrownBy(() -> guard(remote, new MockEnvironment().withProperty("spring.profiles.active", "dev")).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("loopback");
    }

    @Test
    void refusesInvalidLifetimesAndIncompleteBootstrap() {
        IdentityProperties longAccess = new IdentityProperties("issuer", "audience", "key", null, null,
                Duration.ofMinutes(11), Duration.ofDays(7), Duration.ofDays(30), Duration.ofHours(24), Duration.ofMinutes(15),
                Duration.ofMinutes(5), 5, Duration.ofMinutes(1), true, false, "",
                new IdentityProperties.Bootstrap(false, null, null, "System", "Administrator"), List.of("http://localhost:5173"));
        assertThatThrownBy(() -> guard(longAccess, productionEnvironment()).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ten minutes");
        IdentityProperties bootstrap = new IdentityProperties("issuer", "audience", "key", null, null,
                Duration.ofMinutes(10), Duration.ofDays(7), Duration.ofDays(30), Duration.ofHours(24), Duration.ofMinutes(15),
                Duration.ofMinutes(5), 5, Duration.ofMinutes(1), true, false, "",
                new IdentityProperties.Bootstrap(true, "", "short", "System", "Administrator"), List.of("http://localhost:5173"));
        assertThatThrownBy(() -> guard(bootstrap, productionEnvironment()).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Bootstrap");
    }

    @Test
    void requiresIdentityDevelopmentProfileToAlsoUseDevelopment() {
        MockEnvironment environment = productionEnvironment().withProperty("spring.profiles.active", "identity-dev");
        assertThatThrownBy(() -> guard(properties(true, false), environment).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires the dev");
    }

    private static IdentityStartupGuard guard(IdentityProperties properties, MockEnvironment environment) {
        return new IdentityStartupGuard(properties, environment);
    }

    private static MockEnvironment productionEnvironment() {
        return new MockEnvironment().withProperty("application.identity.email.provider", "smtp")
                .withProperty("application.identity.email.from", "no-reply@example.test")
                .withProperty("spring.mail.host", "smtp.example.test");
    }

    private static IdentityProperties properties(boolean secure, boolean bypass) {
        return new IdentityProperties("issuer", "audience", "key", null, null, Duration.ofMinutes(10), Duration.ofDays(7),
                Duration.ofDays(30), Duration.ofHours(24), Duration.ofMinutes(15), Duration.ofMinutes(5), 5, Duration.ofMinutes(1),
                secure, bypass, "", new IdentityProperties.Bootstrap(false, null, null, "System", "Administrator"), List.of("http://localhost:5173"));
    }
}
