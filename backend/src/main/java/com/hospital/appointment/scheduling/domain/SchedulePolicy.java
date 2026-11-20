package com.hospital.appointment.scheduling.domain;

import com.hospital.appointment.scheduling.application.SchedulingException;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.OverrideType;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;

import java.time.LocalTime;
import java.util.List;

public final class SchedulePolicy {
    private SchedulePolicy() {
    }

    public static void validateWeekly(WeeklyAvailability value) {
        if (!value.startLocal().isBefore(value.endLocal())) {
            throw SchedulingException.invalid("Weekly availability must have a start before its end.");
        }
        if (value.effectiveFrom().isAfter(value.effectiveTo())) {
            throw SchedulingException.invalid("The effective start date must not be after its end date.");
        }
    }

    public static void validateOverride(ScheduleOverride value) {
        boolean bounded = value.startLocal() != null || value.endLocal() != null;
        if (bounded && (value.startLocal() == null || value.endLocal() == null)) {
            throw SchedulingException.invalid("Both local override times are required for a bounded override.");
        }
        if (bounded && !value.startLocal().isBefore(value.endLocal())) {
            throw SchedulingException.invalid("An override must have a start before its end.");
        }
        if (value.type() == OverrideType.OPEN && !bounded) {
            throw SchedulingException.invalid("An open override requires a local start and end.");
        }
        if (value.reason() != null && (value.reason().isBlank() || value.reason().length() > 500)) {
            throw SchedulingException.invalid("The override reason must contain 1 to 500 characters.");
        }
    }

    public static void ensureNoOverlap(WeeklyAvailability candidate, List<WeeklyAvailability> existing) {
        validateWeekly(candidate);
        for (WeeklyAvailability rule : existing) {
            if (rule.id().equals(candidate.id()) || rule.weekday() != candidate.weekday()
                    || candidate.effectiveFrom().isAfter(rule.effectiveTo()) || candidate.effectiveTo().isBefore(rule.effectiveFrom())) {
                continue;
            }
            if (candidate.startLocal().isBefore(rule.endLocal()) && rule.startLocal().isBefore(candidate.endLocal())) {
                throw SchedulingException.duplicate("This weekly availability overlaps another rule for the same weekday and effective period.");
            }
        }
    }

    public static void ensureNoOverlap(ScheduleOverride candidate, List<ScheduleOverride> existing) {
        validateOverride(candidate);
        for (ScheduleOverride rule : existing) {
            if (rule.id().equals(candidate.id()) || !rule.date().equals(candidate.date())) continue;
            if (overlap(candidate.startLocal(), candidate.endLocal(), rule.startLocal(), rule.endLocal())) {
                throw SchedulingException.duplicate("This override overlaps another override on the same clinic date.");
            }
        }
    }

    private static boolean overlap(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        int firstStart = aStart == null ? 0 : aStart.toSecondOfDay();
        int firstEnd = aEnd == null ? 86400 : aEnd.toSecondOfDay();
        int secondStart = bStart == null ? 0 : bStart.toSecondOfDay();
        int secondEnd = bEnd == null ? 86400 : bEnd.toSecondOfDay();
        return firstStart < secondEnd && secondStart < firstEnd;
    }
}
