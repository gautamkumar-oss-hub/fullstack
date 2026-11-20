package com.hospital.appointment.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record AccountProfile(
        UUID accountId,
        String firstName,
        String lastName,
        String phone,
        Instant createdAt,
        Instant updatedAt) {
}
