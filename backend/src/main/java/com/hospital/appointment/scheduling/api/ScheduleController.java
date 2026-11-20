package com.hospital.appointment.scheduling.api;

import com.hospital.appointment.scheduling.application.AvailabilityService;
import com.hospital.appointment.scheduling.application.ScheduleService;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ConflictPreview;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors/{doctorId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Scheduling")
class ScheduleController {
    private final ScheduleService schedules;
    private final AvailabilityService availability;
    private final String clinicTimeZone;

    ScheduleController(ScheduleService schedules, AvailabilityService availability, com.hospital.appointment.config.ApplicationProperties properties) {
        this.schedules = schedules;
        this.availability = availability;
        this.clinicTimeZone = properties.clinicZoneId().getId();
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    List<SchedulingApi.WeeklyResponse> schedule(@PathVariable UUID doctorId) {
        return schedules.weeklyRules(doctorId).stream().map(ScheduleController::weekly).toList();
    }

    @PostMapping("/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.WeeklyResponse createWeekly(@PathVariable UUID doctorId, @Valid @RequestBody SchedulingApi.WeeklyRequest request, HttpServletRequest http) {
        return weekly(schedules.createWeekly(DirectoryController.actor(http).accountId(), doctorId, request.weekday(), request.startLocal(),
                request.endLocal(), request.effectiveFrom(), request.effectiveTo(), DirectoryController.source(http)));
    }

    @PatchMapping("/schedule/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.WeeklyResponse updateWeekly(@PathVariable UUID doctorId, @PathVariable UUID id, @Valid @RequestBody SchedulingApi.VersionedWeeklyRequest request,
                                              HttpServletRequest http) {
        WeeklyAvailability existing = schedules.weeklyRules(doctorId).stream().filter(rule -> rule.id().equals(id)).findFirst()
                .orElseThrow(() -> com.hospital.appointment.scheduling.application.SchedulingException.notFound("weekly rule"));
        return weekly(schedules.updateWeekly(DirectoryController.actor(http).accountId(), existing.id(), request.weekday(), request.startLocal(),
                request.endLocal(), request.effectiveFrom(), request.effectiveTo(), request.version(), DirectoryController.source(http)));
    }

    @GetMapping("/overrides")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    List<SchedulingApi.OverrideResponse> overrides(@PathVariable UUID doctorId) {
        return schedules.overrides(doctorId).stream().map(ScheduleController::override).toList();
    }

    @PostMapping("/overrides")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.OverrideResponse createOverride(@PathVariable UUID doctorId, @Valid @RequestBody SchedulingApi.OverrideRequest request, HttpServletRequest http) {
        return override(schedules.createOverride(DirectoryController.actor(http).accountId(), doctorId, request.date(), request.type(),
                request.startLocal(), request.endLocal(), request.reason(), DirectoryController.source(http)));
    }

    @PatchMapping("/overrides/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SchedulingApi.OverrideResponse updateOverride(@PathVariable UUID doctorId, @PathVariable UUID id, @Valid @RequestBody SchedulingApi.VersionedOverrideRequest request,
                                                  HttpServletRequest http) {
        ScheduleOverride existing = schedules.overrides(doctorId).stream().filter(rule -> rule.id().equals(id)).findFirst()
                .orElseThrow(() -> com.hospital.appointment.scheduling.application.SchedulingException.notFound("schedule override"));
        return override(schedules.updateOverride(DirectoryController.actor(http).accountId(), existing.id(), request.date(), request.type(),
                request.startLocal(), request.endLocal(), request.reason(), request.version(), DirectoryController.source(http)));
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    SchedulingApi.AvailabilityResponse availability(@PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var slots = availability.availability(doctorId, from, to).stream()
                .map(slot -> new SchedulingApi.AvailabilitySlot(slot.startAt(), slot.endAt())).toList();
        return new SchedulingApi.AvailabilityResponse(doctorId, clinicTimeZone, slots,
                "Availability is informational only and does not reserve a slot.");
    }

    @GetMapping("/schedule-conflict-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    SchedulingApi.ConflictPreviewResponse conflictPreview(@PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ConflictPreview result = schedules.preview(doctorId, from, to);
        return new SchedulingApi.ConflictPreviewResponse(result.doctorId(), result.from(), result.to(), result.allocationChecksEnforced(), result.message());
    }

    static SchedulingApi.WeeklyResponse weekly(WeeklyAvailability value) {
        return new SchedulingApi.WeeklyResponse(value.id(), value.doctorId(), value.weekday(), value.startLocal(), value.endLocal(),
                value.effectiveFrom(), value.effectiveTo(), value.version());
    }

    static SchedulingApi.OverrideResponse override(ScheduleOverride value) {
        return new SchedulingApi.OverrideResponse(value.id(), value.doctorId(), value.date(), value.type(), value.startLocal(), value.endLocal(), value.reason(), value.version());
    }
}
