package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.AccountStatus;
import com.hospital.appointment.identity.domain.Role;

import java.util.UUID;

public record AccountView(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Role role,
        AccountStatus status,
        boolean emailVerified,
        boolean forcePasswordChange,
        long authzVersion,
        long version) {
}
