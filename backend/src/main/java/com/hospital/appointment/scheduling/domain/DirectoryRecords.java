package com.hospital.appointment.scheduling.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Persistence-neutral directory and clinic-local schedule records. */
public final class DirectoryRecords {
    private DirectoryRecords() {
    }

    public record Specialization(UUID id, String name, boolean active, Instant deactivatedAt, long version) {
    }

    /** Doctors deliberately have no account identifier or authentication role. */
    public record Doctor(UUID id, UUID specializationId, String firstName, String lastName,
                         int slotDurationMinutes, boolean active, Instant deactivatedAt, long version) {
    }

    public record WeeklyAvailability(UUID id, UUID doctorId, DayOfWeek weekday, LocalTime startLocal,
                                     LocalTime endLocal, LocalDate effectiveFrom, LocalDate effectiveTo, long version) {
    }

    public enum OverrideType { OPEN, CLOSED, LEAVE }

    public record ScheduleOverride(UUID id, UUID doctorId, LocalDate date, OverrideType type,
                                   LocalTime startLocal, LocalTime endLocal, String reason, long version) {
    }

    public record ClinicConfiguration(String clinicTimeZone, int bookingHorizonDays, int proposalHoldHours,
                                      int patientChangeCutoffHours, long version) {
    }

    public record TimeInterval(Instant start, Instant end) {
        public TimeInterval {
            if (start == null || end == null || !start.isBefore(end)) {
                throw new IllegalArgumentException("An interval must have a start before its end.");
            }
        }
    }

    public record AvailabilitySlot(UUID doctorId, Instant startAt, Instant endAt, String clinicTimeZone) {
    }

    public record ConflictPreview(UUID doctorId, LocalDate from, LocalDate to, boolean allocationChecksEnforced,
                                  String message) {
    }
}
