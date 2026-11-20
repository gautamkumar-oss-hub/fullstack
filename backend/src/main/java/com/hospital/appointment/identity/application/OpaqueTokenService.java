package com.hospital.appointment.identity.application;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class OpaqueTokenService {
    private final SecureRandom secureRandom = new SecureRandom();

    public String newRefreshToken() {
        return random(48);
    }

    public String newCsrfToken() {
        return random(32);
    }

    public String newOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String random(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
