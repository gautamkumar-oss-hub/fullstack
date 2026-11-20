package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {
    WeeklyAvailability insertWeekly(WeeklyAvailability value);
    WeeklyAvailability updateWeekly(WeeklyAvailability value, long expectedVersion);
    Optional<WeeklyAvailability> weekly(UUID id);
    List<WeeklyAvailability> weeklyRules(UUID doctorId);
    List<WeeklyAvailability> weeklyRules(UUID doctorId, LocalDate from, LocalDate to);
    ScheduleOverride insertOverride(ScheduleOverride value);
    ScheduleOverride updateOverride(ScheduleOverride value, long expectedVersion);
    Optional<ScheduleOverride> override(UUID id);
    List<ScheduleOverride> overrides(UUID doctorId);
    List<ScheduleOverride> overrides(UUID doctorId, LocalDate from, LocalDate to);
}
