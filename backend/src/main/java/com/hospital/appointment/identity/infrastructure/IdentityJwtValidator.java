package com.hospital.appointment.identity.infrastructure;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Duration;
import java.util.Set;

class IdentityJwtValidator implements OAuth2TokenValidator<Jwt> {
    private final OAuth2TokenValidator<Jwt> defaults;
    private final IdentityProperties properties;

    IdentityJwtValidator(IdentityProperties properties) {
        this.properties = properties;
        this.defaults = new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(properties.issuer()));
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        OAuth2TokenValidatorResult result = defaults.validate(jwt);
        if (result.hasErrors()) {
            return result;
        }
        boolean valid = properties.audience().equals(jwt.getAudience().stream().findFirst().orElse(null))
                && jwt.getAudience().size() == 1 && jwt.getId() != null && !jwt.getId().isBlank()
                && jwt.getClaimAsString("role") != null && Set.of("ADMIN", "STAFF", "PATIENT").contains(jwt.getClaimAsString("role"))
                && jwt.getClaim("authz_version") instanceof Number;
        return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required identity claims are missing.", null));
    }
}
