package com.hospital.appointment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.DateTimeException;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "application")
public record ApplicationProperties(
        @NotBlank String name,
        @NotBlank String clinicTimeZone,
        @Positive int defaultBookingHorizonDays,
        @Positive int defaultProposalHoldHours,
        @Positive int defaultPatientChangeCutoffHours) {

    public ZoneId clinicZoneId() {
        try {
            return ZoneId.of(clinicTimeZone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("application.clinic-time-zone must be a valid IANA zone", exception);
        }
    }
}
