package com.hospital.appointment.scheduling.application;

import com.hospital.appointment.identity.application.SecurityAudit;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Doctor;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Specialization;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DirectoryService {
    private final DirectoryRepository repository;
    private final SecurityAudit audit;
    private final Clock clock;

    public DirectoryService(DirectoryRepository repository, SecurityAudit audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or #activeOnly")
    public List<Specialization> specializations(boolean activeOnly) {
        return repository.specializations(activeOnly);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or #activeOnly")
    public List<Doctor> doctors(boolean activeOnly) {
        return repository.doctors(activeOnly);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Specialization createSpecialization(UUID actor, String name, String source) {
        String normalized = name(name);
        requireUniqueSpecialization(normalized, null);
        Specialization value = repository.insertSpecialization(new Specialization(UUID.randomUUID(), normalized, true, null, 0));
        audit.record("specialization_created", actor, source, Map.of("specializationId", value.id().toString()));
        return value;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Specialization updateSpecialization(UUID actor, UUID id, String name, long version, String source) {
        Specialization existing = specialization(id);
        String normalized = name(name);
        requireUniqueSpecialization(normalized, id);
        Specialization result = repository.updateSpecialization(new Specialization(id, normalized, existing.active(), existing.deactivatedAt(), version), version);
        audit.record("specialization_updated", actor, source, Map.of("specializationId", id.toString()));
        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Specialization deactivateSpecialization(UUID actor, UUID id, long version, String source) {
        Specialization existing = specialization(id);
        if (existing.version() != version) throw SchedulingException.conflict();
        if (!existing.active()) return existing;
        if (!repository.doctors(true).stream().filter(doctor -> doctor.specializationId().equals(id)).toList().isEmpty()) {
            throw SchedulingException.duplicate("Deactivate this specialization's doctors before deactivating it.");
        }
        Specialization result = repository.updateSpecialization(new Specialization(id, existing.name(), false, Instant.now(clock), version), version);
        audit.record("specialization_deactivated", actor, source, Map.of("specializationId", id.toString()));
        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Doctor createDoctor(UUID actor, UUID specializationId, String firstName, String lastName, int duration, String source) {
        activeSpecialization(specializationId);
        String first = name(firstName);
        String last = name(lastName);
        duration(duration);
        Doctor result = repository.insertDoctor(new Doctor(UUID.randomUUID(), specializationId, first, last, duration, true, null, 0));
        audit.record("doctor_created", actor, source, Map.of("doctorId", result.id().toString(), "specializationId", specializationId.toString()));
        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Doctor updateDoctor(UUID actor, UUID id, UUID specializationId, String firstName, String lastName, int duration, long version, String source) {
        Doctor existing = doctor(id);
        activeSpecialization(specializationId);
        Doctor result = repository.updateDoctor(new Doctor(id, specializationId, name(firstName), name(lastName), duration,
                existing.active(), existing.deactivatedAt(), version), version);
        audit.record("doctor_updated", actor, source, Map.of("doctorId", id.toString(), "specializationId", specializationId.toString()));
        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Doctor deactivateDoctor(UUID actor, UUID id, long version, String source) {
        Doctor existing = doctor(id);
        if (existing.version() != version) throw SchedulingException.conflict();
        if (!existing.active()) return existing;
        Doctor result = repository.updateDoctor(new Doctor(id, existing.specializationId(), existing.firstName(), existing.lastName(),
                existing.slotDurationMinutes(), false, Instant.now(clock), version), version);
        audit.record("doctor_deactivated", actor, source, Map.of("doctorId", id.toString()));
        return result;
    }

    @Transactional(readOnly = true)
    public Doctor activeDoctor(UUID id) {
        Doctor value = doctor(id);
        if (!value.active()) throw SchedulingException.invalid("The doctor is inactive.");
        if (!specialization(value.specializationId()).active()) throw SchedulingException.invalid("The doctor's specialization is inactive.");
        return value;
    }

    private Specialization specialization(UUID id) {
        return repository.specialization(id).orElseThrow(() -> SchedulingException.notFound("specialization"));
    }

    private Doctor doctor(UUID id) {
        return repository.doctor(id).orElseThrow(() -> SchedulingException.notFound("doctor"));
    }

    private void activeSpecialization(UUID id) {
        if (!specialization(id).active()) throw SchedulingException.invalid("The specialization is inactive.");
    }

    private void requireUniqueSpecialization(String name, UUID excluded) {
        boolean duplicate = repository.specializations(false).stream()
                .anyMatch(value -> !value.id().equals(excluded) && value.name().equalsIgnoreCase(name));
        if (duplicate) throw SchedulingException.duplicate("A specialization with this name already exists.");
    }

    private static String name(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 100) throw SchedulingException.invalid("Names must contain 1 to 100 characters.");
        return normalized;
    }

    private static void duration(int minutes) {
        if (minutes < 5 || minutes > 180 || minutes % 5 != 0) throw SchedulingException.invalid("Slot duration must be 5 to 180 minutes in five-minute increments.");
    }
}
