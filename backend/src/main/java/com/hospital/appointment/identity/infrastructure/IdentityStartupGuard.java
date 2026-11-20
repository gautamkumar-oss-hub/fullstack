package com.hospital.appointment.identity.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class IdentityStartupGuard implements ApplicationRunner {
    private static final Set<String> LOOPBACK_ORIGINS = Set.of("http://localhost:5173", "http://127.0.0.1:5173");
    private final IdentityProperties properties;
    private final Environment environment;

    public IdentityStartupGuard(IdentityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean development = environment.matchesProfiles("dev");
        if (environment.matchesProfiles("identity-dev") && !development) {
            throw new IllegalStateException("The identity-dev profile requires the dev profile.");
        }
        String emailProvider = environment.getProperty("application.identity.email.provider", "");
        if (!development && !"smtp".equals(emailProvider)) {
            throw new IllegalStateException("Configure the SMTP identity email provider outside development.");
        }
        if ("smtp".equals(emailProvider)) {
            String from = environment.getProperty("application.identity.email.from", "");
            String host = environment.getProperty("spring.mail.host", "");
            if (from.isBlank() || host.isBlank()) {
                throw new IllegalStateException("SMTP identity email requires a from address and spring.mail.host.");
            }
        }
        if (!properties.accessTokenLifetime().equals(Duration.ofMinutes(10))) {
            throw new IllegalStateException("Identity access-token lifetime must be exactly ten minutes.");
        }
        if (properties.refreshIdleLifetime().compareTo(Duration.ofDays(7)) > 0
                || properties.refreshAbsoluteLifetime().compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalStateException("Refresh lifetimes cannot exceed seven-day idle and thirty-day absolute bounds.");
        }
        if (!development && (!properties.cookieSecure() || properties.mfaBypass())) {
            throw new IllegalStateException("Secure cookies and mandatory MFA are required outside the dev profile.");
        }
        if (properties.mfaBypass() && (!development || !environment.matchesProfiles("identity-dev")
                || !"123456".equals(properties.exposedMfaCode()))) {
            throw new IllegalStateException("MFA bypass requires both dev and identity-dev profiles and the isolated fixed test code.");
        }
        if (!properties.cookieSecure() && (!development || !properties.allowedOrigins().stream().allMatch(LOOPBACK_ORIGINS::contains))) {
            throw new IllegalStateException("Insecure identity cookies are restricted to loopback development origins.");
        }
        if (properties.bootstrap().enabled()) {
            if (properties.bootstrap().email() == null || properties.bootstrap().email().isBlank()
                    || properties.bootstrap().password() == null
                    || properties.bootstrap().password().length() < 16) {
                throw new IllegalStateException("Bootstrap requires an email and a password of at least sixteen characters.");
            }
            if (!development && properties.bootstrap().password().startsWith("local-")) {
                throw new IllegalStateException("A local bootstrap password cannot be used outside development.");
            }
        }
    }
}
