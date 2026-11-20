package com.hospital.appointment.identity.application;

public interface TokenHasher {
    String hash(String token);
}
