package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.config.ApplicationProperties;
import com.hospital.appointment.scheduling.domain.ClinicLocalTimePolicy;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.AvailabilitySlot;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Doctor;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.OverrideType;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.TimeInterval;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityService {
    private static final int MAXIMUM_QUERY_DAYS = 31;
    private final DirectoryService directory;
    private final ScheduleRepository schedules;
    private final ConfigurationRepository configurations;
    private final ActiveAllocationExclusion allocations;
    private final Clock clock;
    private final ZoneId clinicZone;

    public AvailabilityService(DirectoryService directory, ScheduleRepository schedules, ConfigurationRepository configurations,
                               ActiveAllocationExclusion allocations, Clock clock, ApplicationProperties properties) {
        this.directory = directory;
        this.schedules = schedules;
        this.configurations = configurations;
        this.allocations = allocations;
        this.clock = clock;
        this.clinicZone = properties.clinicZoneId();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<AvailabilitySlot> availability(UUID doctorId, LocalDate from, LocalDate to) {
        Doctor doctor = directory.activeDoctor(doctorId);
        LocalDate today = LocalDate.now(clock.withZone(clinicZone));
        int horizon = configurations.current().bookingHorizonDays();
        if (from.isAfter(to) || from.isBefore(today) || to.isAfter(today.plusDays(horizon))
                || to.toEpochDay() - from.toEpochDay() >= MAXIMUM_QUERY_DAYS) {
            throw SchedulingException.invalid("Choose a range of at most 31 days between today and the current booking horizon.");
        }
        Instant now = Instant.now(clock);
        Instant horizonEnd = ClinicLocalTimePolicy.exact(to.plusDays(1).atStartOfDay(), clinicZone).toInstant();
        List<AvailabilitySlot> slots = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<int[]> open = new ArrayList<>();
            List<int[]> closures = new ArrayList<>();
            List<ScheduleOverride> overrides = schedules.overrides(doctorId, date, date);
            boolean hasOpenOverride = overrides.stream().anyMatch(override -> override.type() == OverrideType.OPEN);
            if (!hasOpenOverride) {
                for (WeeklyAvailability rule : schedules.weeklyRules(doctorId, date, date)) {
                    if (rule.weekday() == date.getDayOfWeek()) open.add(new int[]{seconds(rule.startLocal()), seconds(rule.endLocal())});
                }
            }
            for (ScheduleOverride override : overrides) {
                if (override.type() == OverrideType.OPEN) {
                    open.add(new int[]{seconds(override.startLocal()), seconds(override.endLocal())});
                } else {
                    closures.add(new int[]{secondsOrZero(override.startLocal()), secondsOrEnd(override.endLocal())});
                }
            }
            for (int[] closure : closures) open = subtract(open, closure[0], closure[1]);
            for (int[] interval : open) {
                for (int second = interval[0]; second + doctor.slotDurationMinutes() * 60 <= interval[1]; second += doctor.slotDurationMinutes() * 60) {
                    Instant start = ClinicLocalTimePolicy.exact(LocalDateTime.of(date, LocalTime.ofSecondOfDay(second)), clinicZone).toInstant();
                    Instant end = ClinicLocalTimePolicy.exact(LocalDateTime.of(date, LocalTime.ofSecondOfDay(second + doctor.slotDurationMinutes() * 60)), clinicZone).toInstant();
                    if (start.isAfter(now) && !end.isAfter(horizonEnd)) slots.add(new AvailabilitySlot(doctorId, start, end, clinicZone.getId()));
                }
            }
        }
        List<TimeInterval> excluded = allocations.activeIntervals(doctorId, from.atStartOfDay(clinicZone).toInstant(), horizonEnd);
        return slots.stream().filter(slot -> excluded.stream().noneMatch(interval -> slot.startAt().isBefore(interval.end())
                && interval.start().isBefore(slot.endAt()))).toList();
    }

    private static List<int[]> subtract(List<int[]> source, int removeStart, int removeEnd) {
        List<int[]> result = new ArrayList<>();
        for (int[] interval : source) {
            if (removeStart >= interval[1] || removeEnd <= interval[0]) {
                result.add(interval);
                continue;
            }
            if (interval[0] < removeStart) result.add(new int[]{interval[0], Math.min(removeStart, interval[1])});
            if (removeEnd < interval[1]) result.add(new int[]{Math.max(removeEnd, interval[0]), interval[1]});
        }
        return result;
    }

    private static int seconds(LocalTime time) {
        return time.toSecondOfDay();
    }

    private static int secondsOrZero(LocalTime time) {
        return time == null ? 0 : time.toSecondOfDay();
    }

    private static int secondsOrEnd(LocalTime time) {
        return time == null ? 86400 : time.toSecondOfDay();
    }
}
