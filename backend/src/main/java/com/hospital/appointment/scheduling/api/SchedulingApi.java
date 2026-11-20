package com.hospital.appointment.scheduling.api;

import com.hospital.appointment.scheduling.domain.DirectoryRecords.OverrideType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

final class SchedulingApi {
    private SchedulingApi() {
    }

    record SpecializationRequest(@NotBlank @Size(max = 100) String name) {
    }

    record DoctorRequest(@NotNull UUID specializationId, @NotBlank @Size(max = 100) String firstName,
                         @NotBlank @Size(max = 100) String lastName, @Min(5) @Max(180) int slotDurationMinutes) {
    }

    record VersionRequest(@NotNull @Min(0) Long version) {
    }

    record SpecializationResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
                                  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
                                  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
                                  @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record DoctorResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID specializationId,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int slotDurationMinutes,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record WeeklyRequest(@NotNull DayOfWeek weekday, @NotNull @Schema(type = "string", format = "time", example = "09:00:00") LocalTime startLocal,
                         @NotNull @Schema(type = "string", format = "time", example = "12:00:00") LocalTime endLocal,
                         @NotNull LocalDate effectiveFrom, @NotNull LocalDate effectiveTo) {
    }

    record VersionedWeeklyRequest(@NotNull DayOfWeek weekday, @NotNull @Schema(type = "string", format = "time", example = "09:00:00") LocalTime startLocal,
                                  @NotNull @Schema(type = "string", format = "time", example = "12:00:00") LocalTime endLocal,
                                  @NotNull LocalDate effectiveFrom, @NotNull LocalDate effectiveTo, @NotNull @Min(0) Long version) {
    }

    record WeeklyResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID doctorId,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DayOfWeek weekday,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime startLocal,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalTime endLocal,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveFrom,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate effectiveTo,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record OverrideRequest(@NotNull LocalDate date, @NotNull OverrideType type,
                           @Schema(type = "string", format = "time", example = "09:00:00") LocalTime startLocal,
                           @Schema(type = "string", format = "time", example = "12:00:00") LocalTime endLocal,
                           @Size(max = 500) String reason) {
    }

    record VersionedOverrideRequest(@NotNull LocalDate date, @NotNull OverrideType type,
                                    @Schema(type = "string", format = "time", example = "09:00:00") LocalTime startLocal,
                                    @Schema(type = "string", format = "time", example = "12:00:00") LocalTime endLocal,
                                    @Size(max = 500) String reason, @NotNull @Min(0) Long version) {
    }

    record OverrideResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
                            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID doctorId,
                            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
                            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OverrideType type,
                            LocalTime startLocal, LocalTime endLocal, String reason,
                            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record AvailabilityResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID doctorId,
                                @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clinicTimeZone,
                                @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AvailabilitySlot> slots,
                                @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bookingNotice) {
    }

    record AvailabilitySlot(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startAt,
                            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant endAt) {
    }

    record ConflictPreviewResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID doctorId,
                                   @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate from,
                                   @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate to,
                                   @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allocationChecksEnforced,
                                   @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
    }

    record ConfigurationResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clinicTimeZone,
                                 @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int bookingHorizonDays,
                                 @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int proposalHoldHours,
                                 @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int patientChangeCutoffHours,
                                 @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
    }

    record ConfigurationRequest(@Min(1) @Max(365) int bookingHorizonDays, @Min(1) @Max(168) int proposalHoldHours,
                                @Min(1) @Max(720) int patientChangeCutoffHours, @NotNull @Min(0) Long version) {
    }
}
