package com.hospital.appointment.scheduling.domain;

import com.hospital.appointment.scheduling.application.SchedulingException;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Strict conversion: neither silent DST-gap adjustment nor arbitrary offset choice is allowed. */
public final class ClinicLocalTimePolicy {
    private ClinicLocalTimePolicy() {
    }

    public static ZonedDateTime exact(LocalDateTime local, ZoneId zone) {
        List<java.time.ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            throw SchedulingException.invalid("The local time falls in a daylight-saving gap and does not exist.");
        }
        if (offsets.size() != 1) {
            throw SchedulingException.invalid("The local time is ambiguous because of a daylight-saving transition.");
        }
        try {
            return ZonedDateTime.ofStrict(local, offsets.getFirst(), zone);
        } catch (DateTimeException exception) {
            throw SchedulingException.invalid("The clinic-local time is invalid.");
        }
    }
}
