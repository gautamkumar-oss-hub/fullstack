package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.config.ApplicationProperties;
import com.hospital.appointment.identity.application.SecurityAudit;
import com.hospital.appointment.scheduling.domain.ClinicLocalTimePolicy;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ConflictPreview;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.OverrideType;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;
import com.hospital.appointment.scheduling.domain.SchedulePolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScheduleService {
    private final ScheduleRepository repository;
    private final DirectoryService directory;
    private final ActiveAllocationExclusion allocations;
    private final SecurityAudit audit;
    private final ZoneId clinicZone;

    public ScheduleService(ScheduleRepository repository, DirectoryService directory, ActiveAllocationExclusion allocations,
                           SecurityAudit audit, ApplicationProperties properties) {
        this.repository = repository;
        this.directory = directory;
        this.allocations = allocations;
        this.audit = audit;
        this.clinicZone = properties.clinicZoneId();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<WeeklyAvailability> weeklyRules(UUID doctorId) {
        directory.activeDoctor(doctorId);
        return repository.weeklyRules(doctorId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<ScheduleOverride> overrides(UUID doctorId) {
        directory.activeDoctor(doctorId);
        return repository.overrides(doctorId);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WeeklyAvailability createWeekly(UUID actor, UUID doctorId, DayOfWeek weekday, LocalTime start, LocalTime end,
                                           LocalDate from, LocalDate to, String source) {
        directory.activeDoctor(doctorId);
        if (from.toEpochDay() - to.toEpochDay() < -366) throw SchedulingException.invalid("An effective period cannot exceed 367 days.");
        WeeklyAvailability candidate = new WeeklyAvailability(UUID.randomUUID(), doctorId, weekday, start, end, from, to, 0);
        SchedulePolicy.ensureNoOverlap(candidate, repository.weeklyRules(doctorId));
        validateLocalTimes(candidate);
        WeeklyAvailability result = repository.insertWeekly(candidate);
        audit.record("weekly_availability_created", actor, source, Map.of("doctorId", doctorId.toString(), "ruleId", result.id().toString()));
        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WeeklyAvailability updateWeekly(UUID actor, UUID id, DayOfWeek weekday, LocalTime start, LocalTime end,
                                           LocalDate from, LocalDate to, long version, String source) {
        WeeklyAvailability existing = repository.weekly(id).orElseThrow(() -> SchedulingException.notFound("weekly rule"));
        directory.activeDoctor(existing.doctorId());
        if (from.toEpochDay() - to.toEpochDay() < -366) throw SchedulingException.invalid("An effective period cannot exceed 367 days.");
        WeeklyAvailability candidate = new WeeklyAvailability(id, existing.doctorId(), weekday, start, end, from, to, version);
        SchedulePolicy.ensureNoOverlap(candidate, repository.weeklyRules(existing.doctorId()));
        validateLocalTimes(candidate);
        WeeklyAvailability result = repository.updateWeekly(candidate, version);
        audit.record("weekly_availability_updated", actor, source, Map.of("doctorId", existing.doctorId().toString(), "ruleId", id.toString()));
        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleOverride createOverride(UUID actor, UUID doctorId, LocalDate date, OverrideType type, LocalTime start,
                                           LocalTime end, String reason, String source) {
        directory.activeDoctor(doctorId);
        ScheduleOverride candidate = new ScheduleOverride(UUID.randomUUID(), doctorId, date, type, start, end, normalizeReason(reason), 0);
        SchedulePolicy.ensureNoOverlap(candidate, repository.overrides(doctorId));
        validateLocalTimes(candidate);
        ScheduleOverride result = repository.insertOverride(candidate);
        audit.record("schedule_override_created", actor, source, Map.of("doctorId", doctorId.toString(), "overrideId", result.id().toString(), "type", type.name()));
        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleOverride updateOverride(UUID actor, UUID id, LocalDate date, OverrideType type, LocalTime start,
                                           LocalTime end, String reason, long version, String source) {
        ScheduleOverride existing = repository.override(id).orElseThrow(() -> SchedulingException.notFound("schedule override"));
        directory.activeDoctor(existing.doctorId());
        ScheduleOverride candidate = new ScheduleOverride(id, existing.doctorId(), date, type, start, end, normalizeReason(reason), version);
        SchedulePolicy.ensureNoOverlap(candidate, repository.overrides(existing.doctorId()));
        validateLocalTimes(candidate);
        ScheduleOverride result = repository.updateOverride(candidate, version);
        audit.record("schedule_override_updated", actor, source, Map.of("doctorId", existing.doctorId().toString(), "overrideId", id.toString(), "type", type.name()));
        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ConflictPreview preview(UUID doctorId, LocalDate from, LocalDate to) {
        directory.activeDoctor(doctorId);
        if (from.isAfter(to) || to.toEpochDay() - from.toEpochDay() >= 31) {
            throw SchedulingException.invalid("Choose a valid preview range of at most 31 days.");
        }
        return allocations.preview(doctorId, from, to);
    }

    private void validateLocalTimes(WeeklyAvailability rule) {
        for (LocalDate date = rule.effectiveFrom(); !date.isAfter(rule.effectiveTo()); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == rule.weekday()) {
                validateLocalRange(date, rule.startLocal(), rule.endLocal());
            }
        }
    }

    private void validateLocalTimes(ScheduleOverride override) {
        if (override.startLocal() != null) validateLocalRange(override.date(), override.startLocal(), override.endLocal());
    }

    private void validateLocalRange(LocalDate date, LocalTime start, LocalTime end) {
        ClinicLocalTimePolicy.exact(LocalDateTime.of(date, start), clinicZone);
        ClinicLocalTimePolicy.exact(LocalDateTime.of(date, end), clinicZone);
    }

    private static String normalizeReason(String reason) {
        return reason == null ? null : reason.trim();
    }
}
