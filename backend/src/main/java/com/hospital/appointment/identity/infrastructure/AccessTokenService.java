package com.hospital.appointment.identity.infrastructure;

import com.hospital.appointment.identity.domain.UserAccount;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccessTokenService {
    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;

    AccessTokenService(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(UserAccount account) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.id().toString())
                .audience(java.util.List.of(properties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenLifetime()))
                .id(UUID.randomUUID().toString())
                .claim("role", account.role().name())
                .claim("authz_version", account.authzVersion())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
