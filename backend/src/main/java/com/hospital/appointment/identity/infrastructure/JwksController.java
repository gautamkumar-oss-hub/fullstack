package com.hospital.appointment.identity.infrastructure;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class JwksController {
    private final JWKSet jwks;

    JwksController(JWKSet publicIdentityJwks) {
        this.jwks = publicIdentityJwks;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> keys() {
        return jwks.toJSONObject(true);
    }
}
