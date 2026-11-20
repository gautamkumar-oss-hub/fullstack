package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.scheduling.domain.DirectoryRecords.ClinicConfiguration;

public interface ConfigurationRepository {
    ClinicConfiguration current();
    ClinicConfiguration update(int bookingHorizonDays, int proposalHoldHours, int patientChangeCutoffHours, long expectedVersion);
}
