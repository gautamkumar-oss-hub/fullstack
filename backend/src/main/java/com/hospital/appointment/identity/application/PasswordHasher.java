package com.hospital.appointment.identity.application;

public interface PasswordHasher {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedHash);
}
