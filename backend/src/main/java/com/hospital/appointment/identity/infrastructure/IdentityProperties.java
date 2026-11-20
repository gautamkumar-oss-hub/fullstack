package com.hospital.appointment.identity.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "application.identity")
public record IdentityProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String keyId,
        String privateKey,
        String publicKey,
        @NotNull Duration accessTokenLifetime,
        @NotNull Duration refreshIdleLifetime,
        @NotNull Duration refreshAbsoluteLifetime,
        @NotNull Duration verificationLifetime,
        @NotNull Duration recoveryLifetime,
        @NotNull Duration mfaLifetime,
        @Min(3) @Max(10) int maximumChallengeAttempts,
        @NotNull Duration resendInterval,
        boolean cookieSecure,
        boolean mfaBypass,
        String exposedMfaCode,
        @Valid @NotNull Bootstrap bootstrap,
        @NotNull List<String> allowedOrigins) {

    public record Bootstrap(boolean enabled, String email, String password, String firstName, String lastName) {
    }
}
