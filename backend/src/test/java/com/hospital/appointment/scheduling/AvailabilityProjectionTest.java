package com.hospital.appointment.scheduling;

import com.hospital.appointment.scheduling.application.ActiveAllocationExclusion;
import com.hospital.appointment.scheduling.application.AvailabilityService;
import com.hospital.appointment.scheduling.application.ConfigurationRepository;
import com.hospital.appointment.scheduling.application.DirectoryRepository;
import com.hospital.appointment.scheduling.application.ScheduleRepository;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.AvailabilitySlot;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ClinicConfiguration;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Doctor;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.OverrideType;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Specialization;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"application.clinic-time-zone=America/New_York", "application.identity.bootstrap.enabled=false"})
@org.springframework.test.context.ActiveProfiles({"dev", "identity-dev"})
@Testcontainers
class AvailabilityProjectionTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @org.junit.jupiter.api.BeforeEach void authenticateStaff() {
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "staff", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STAFF")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    @org.junit.jupiter.api.AfterEach void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    @Autowired AvailabilityService availability;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DirectoryRepository directoryRepository;
    @MockitoBean ScheduleRepository scheduleRepository;
    @MockitoBean ConfigurationRepository configurationRepository;
    @MockitoBean ActiveAllocationExclusion allocationExclusion;

    @Test
    void generatesFixedSlotsSubtractsLeaveAndConvertsToUtc() {
        UUID doctor = UUID.randomUUID();
        UUID specialization = UUID.randomUUID();
        LocalDate monday = LocalDate.parse("2027-07-19");
        when(directoryRepository.doctor(doctor)).thenReturn(Optional.of(new Doctor(doctor, specialization, "Test", "Doctor", 30, true, null, 0)));
        when(directoryRepository.specialization(specialization)).thenReturn(Optional.of(new Specialization(specialization, "Test", true, null, 0)));
        when(configurationRepository.current()).thenReturn(new ClinicConfiguration("America/New_York", 365, 24, 24, 0));
        when(scheduleRepository.weeklyRules(doctor, monday, monday)).thenReturn(List.of(new WeeklyAvailability(UUID.randomUUID(), doctor,
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), monday, monday, 0)));
        when(scheduleRepository.overrides(doctor, monday, monday)).thenReturn(List.of(new ScheduleOverride(UUID.randomUUID(), doctor, monday,
                OverrideType.LEAVE, LocalTime.of(9, 30), LocalTime.of(10, 0), "Synthetic leave", 0)));
        when(allocationExclusion.activeIntervals(any(), any(), any())).thenReturn(List.of());
        List<AvailabilitySlot> result = availability.availability(doctor, monday, monday);
        assertThat(result).hasSize(3);
        assertThat(result.getFirst().startAt()).isEqualTo(Instant.parse("2027-07-19T13:00:00Z"));
        assertThat(result.getFirst().endAt()).isEqualTo(Instant.parse("2027-07-19T13:30:00Z"));
        assertThat(result.get(1).startAt()).isEqualTo(Instant.parse("2027-07-19T14:00:00Z"));
        assertThat(result).allSatisfy(slot -> {
            assertThat(slot.doctorId()).isEqualTo(doctor);
            assertThat(slot.clinicTimeZone()).isEqualTo("America/New_York");
            assertThat(java.time.Duration.between(slot.startAt(), slot.endAt()).toMinutes()).isEqualTo(30);
        });
    }

    @Test
    void subtractsActiveIntervalsOnlyThroughTheAllocationPort() {
        UUID doctor = UUID.randomUUID();
        UUID specialization = UUID.randomUUID();
        LocalDate monday = LocalDate.parse("2027-07-19");
        when(directoryRepository.doctor(doctor)).thenReturn(Optional.of(new Doctor(doctor, specialization, "Test", "Doctor", 60, true, null, 0)));
        when(directoryRepository.specialization(specialization)).thenReturn(Optional.of(new Specialization(specialization, "Test", true, null, 0)));
        when(configurationRepository.current()).thenReturn(new ClinicConfiguration("America/New_York", 365, 24, 24, 0));
        when(scheduleRepository.weeklyRules(doctor, monday, monday)).thenReturn(List.of(new WeeklyAvailability(UUID.randomUUID(), doctor,
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), monday, monday, 0)));
        when(scheduleRepository.overrides(doctor, monday, monday)).thenReturn(List.of());
        when(allocationExclusion.activeIntervals(any(), any(), any())).thenReturn(List.of(new com.hospital.appointment.scheduling.domain.DirectoryRecords.TimeInterval(
                Instant.parse("2027-07-19T13:00:00Z"), Instant.parse("2027-07-19T14:00:00Z"))));
        assertThat(availability.availability(doctor, monday, monday)).isEmpty();
    }

    @Test
    void appliesCurrentHorizonAndBoundedRange() {
        UUID doctor = UUID.randomUUID();
        UUID specialization = UUID.randomUUID();
        when(directoryRepository.doctor(doctor)).thenReturn(Optional.of(new Doctor(doctor, specialization, "Test", "Doctor", 30, true, null, 0)));
        when(directoryRepository.specialization(specialization)).thenReturn(Optional.of(new Specialization(specialization, "Test", true, null, 0)));
        when(configurationRepository.current()).thenReturn(new ClinicConfiguration("America/New_York", 10, 24, 24, 0));
        LocalDate today = LocalDate.now(Clock.systemUTC().withZone(ZoneId.of("America/New_York")));
        assertThatThrownBy(() -> availability.availability(doctor, today, today.plusDays(31))).hasMessageContaining("31 days");
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() -> availability.availability(doctor, today.plusDays(10), today.plusDays(10)));
        assertThatThrownBy(() -> availability.availability(doctor, today.plusDays(11), today.plusDays(11))).hasMessageContaining("booking horizon");
        assertThatThrownBy(() -> availability.availability(doctor, today.minusDays(1), today)).hasMessageContaining("booking horizon");
    }
}
