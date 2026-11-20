package com.hospital.appointment.identity.application;

import com.hospital.appointment.identity.domain.Role;

import java.util.UUID;

public record AuthenticatedAccount(UUID accountId, Role role, long authzVersion) {
}
