package com.hospital.appointment.scheduling.infrastructure;

import com.hospital.appointment.scheduling.application.ConfigurationRepository;
import com.hospital.appointment.scheduling.application.SchedulingException;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ClinicConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcConfigurationRepository implements ConfigurationRepository {
    private final JdbcTemplate jdbc;
    private final String clinicTimeZone;

    JdbcConfigurationRepository(JdbcTemplate jdbc, com.hospital.appointment.config.ApplicationProperties properties) {
        this.jdbc = jdbc;
        this.clinicTimeZone = properties.clinicZoneId().getId();
    }

    @Override
    public ClinicConfiguration current() {
        return jdbc.queryForObject("SELECT booking_horizon_days, proposal_hold_hours, patient_change_cutoff_hours, version "
                + "FROM clinic_configuration WHERE singleton = true", (result, row) -> new ClinicConfiguration(
                        clinicTimeZone, result.getInt("booking_horizon_days"), result.getInt("proposal_hold_hours"),
                        result.getInt("patient_change_cutoff_hours"), result.getLong("version")));
    }

    @Override
    public ClinicConfiguration update(int bookingHorizonDays, int proposalHoldHours, int patientChangeCutoffHours, long expectedVersion) {
        int changed = jdbc.update("UPDATE clinic_configuration SET booking_horizon_days = ?, proposal_hold_hours = ?, "
                        + "patient_change_cutoff_hours = ?, version = version + 1, updated_at = clock_timestamp() "
                        + "WHERE singleton = true AND version = ?", bookingHorizonDays, proposalHoldHours, patientChangeCutoffHours, expectedVersion);
        if (changed != 1) throw SchedulingException.conflict();
        return current();
    }
}
