package com.hospital.appointment.scheduling.domain;

import com.hospital.appointment.scheduling.application.SchedulingException;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.OverrideType;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulePolicyTest {
    private static final UUID DOCTOR = UUID.randomUUID();

    @Test
    void rejectsDstGapInsteadOfAdjustingIt() {
        assertThatThrownBy(() -> ClinicLocalTimePolicy.exact(LocalDateTime.parse("2026-03-08T02:30"), ZoneId.of("America/New_York")))
                .isInstanceOf(SchedulingException.class).hasMessageContaining("gap");
    }

    @Test
    void rejectsAmbiguousRepeatedLocalTime() {
        assertThatThrownBy(() -> ClinicLocalTimePolicy.exact(LocalDateTime.parse("2026-11-01T01:30"), ZoneId.of("America/New_York")))
                .isInstanceOf(SchedulingException.class).hasMessageContaining("ambiguous");
    }

    @Test
    void convertsUnambiguousClinicLocalTimeToUtc() {
        assertThat(ClinicLocalTimePolicy.exact(LocalDateTime.parse("2026-07-15T09:00"), ZoneId.of("America/New_York")).toInstant())
                .isEqualTo("2026-07-15T13:00:00Z");
    }

    @Test
    void rejectsInvertedWeeklyAndEffectiveRanges() {
        WeeklyAvailability inverted = weekly(LocalTime.NOON, LocalTime.of(9, 0), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-02"));
        assertThatThrownBy(() -> SchedulePolicy.validateWeekly(inverted)).hasMessageContaining("start before");
        WeeklyAvailability dates = weekly(LocalTime.of(9, 0), LocalTime.NOON, LocalDate.parse("2026-07-03"), LocalDate.parse("2026-07-02"));
        assertThatThrownBy(() -> SchedulePolicy.validateWeekly(dates)).hasMessageContaining("effective");
    }

    @Test
    void permitsAdjacentRulesAndRejectsOverlappingEffectiveRules() {
        WeeklyAvailability first = weekly(LocalTime.of(9, 0), LocalTime.NOON, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
        WeeklyAvailability adjacent = weekly(LocalTime.NOON, LocalTime.of(13, 0), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
        SchedulePolicy.ensureNoOverlap(adjacent, List.of(first));
        WeeklyAvailability overlapping = weekly(LocalTime.of(11, 0), LocalTime.of(13, 0), LocalDate.parse("2026-07-15"), LocalDate.parse("2026-08-01"));
        assertThatThrownBy(() -> SchedulePolicy.ensureNoOverlap(overlapping, List.of(first))).hasMessageContaining("overlaps");
    }

    @Test
    void overrideRequiresCompleteOrderedBoundsAndOpenBounds() {
        assertThatThrownBy(() -> SchedulePolicy.validateOverride(override(OverrideType.OPEN, null, null))).hasMessageContaining("requires");
        assertThatThrownBy(() -> SchedulePolicy.validateOverride(override(OverrideType.CLOSED, LocalTime.NOON, LocalTime.of(9, 0))))
                .hasMessageContaining("start before");
        assertThatThrownBy(() -> SchedulePolicy.validateOverride(override(OverrideType.LEAVE, LocalTime.of(9, 0), null))).hasMessageContaining("Both");
    }

    @Test
    void fullDayAndBoundedOverridesConflictOnlyWhenTheyOverlap() {
        ScheduleOverride wholeDay = override(OverrideType.CLOSED, null, null);
        assertThatThrownBy(() -> SchedulePolicy.ensureNoOverlap(override(OverrideType.LEAVE, LocalTime.of(9, 0), LocalTime.NOON), List.of(wholeDay)))
                .hasMessageContaining("overlaps");
        ScheduleOverride laterDate = new ScheduleOverride(UUID.randomUUID(), DOCTOR, LocalDate.parse("2026-07-02"), OverrideType.OPEN, LocalTime.of(9, 0), LocalTime.NOON, null, 0);
        SchedulePolicy.ensureNoOverlap(laterDate, List.of(wholeDay));
    }

    private static WeeklyAvailability weekly(LocalTime start, LocalTime end, LocalDate from, LocalDate to) {
        return new WeeklyAvailability(UUID.randomUUID(), DOCTOR, DayOfWeek.MONDAY, start, end, from, to, 0);
    }

    private static ScheduleOverride override(OverrideType type, LocalTime start, LocalTime end) {
        return new ScheduleOverride(UUID.randomUUID(), DOCTOR, LocalDate.parse("2026-07-01"), type, start, end, null, 0);
    }
}
