package com.hospital.appointment.identity.domain;

/** Doctor is deliberately not an identity role; clinical records belong to a later phase. */
public enum Role {
    ADMIN,
    STAFF,
    PATIENT
}
