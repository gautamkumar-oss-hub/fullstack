package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.scheduling.domain.DirectoryRecords.Doctor;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Specialization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectoryRepository {
    Specialization insertSpecialization(Specialization value);
    Specialization updateSpecialization(Specialization value, long expectedVersion);
    Optional<Specialization> specialization(UUID id);
    List<Specialization> specializations(boolean activeOnly);
    Doctor insertDoctor(Doctor value);
    Doctor updateDoctor(Doctor value, long expectedVersion);
    Optional<Doctor> doctor(UUID id);
    List<Doctor> doctors(boolean activeOnly);
}
