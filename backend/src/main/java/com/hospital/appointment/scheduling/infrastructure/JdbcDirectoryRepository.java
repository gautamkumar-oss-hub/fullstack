package com.hospital.appointment.scheduling.infrastructure;

import com.hospital.appointment.scheduling.application.DirectoryRepository;
import com.hospital.appointment.scheduling.application.SchedulingException;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Doctor;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.Specialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcDirectoryRepository implements DirectoryRepository {
    private final JdbcTemplate jdbc;

    JdbcDirectoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Specialization insertSpecialization(Specialization value) {
        jdbc.update("INSERT INTO specialization (id, name, normalized_name, active, deactivated_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, lower(?), ?, ?, 0, clock_timestamp(), clock_timestamp())",
                value.id(), value.name(), value.name(), value.active(), timestamp(value.deactivatedAt()));
        return specialization(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public Specialization updateSpecialization(Specialization value, long expectedVersion) {
        int changed = jdbc.update("UPDATE specialization SET name = ?, normalized_name = lower(?), active = ?, deactivated_at = ?, "
                        + "version = version + 1, updated_at = clock_timestamp() WHERE id = ? AND version = ?",
                value.name(), value.name(), value.active(), timestamp(value.deactivatedAt()), value.id(), expectedVersion);
        if (changed != 1) throw SchedulingException.conflict();
        return specialization(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public Optional<Specialization> specialization(UUID id) {
        return jdbc.query("SELECT id, name, active, deactivated_at, version FROM specialization WHERE id = ?",
                (result, row) -> mapSpecialization(result), id).stream().findFirst();
    }

    @Override
    public List<Specialization> specializations(boolean activeOnly) {
        return jdbc.query("SELECT id, name, active, deactivated_at, version FROM specialization "
                + (activeOnly ? "WHERE active " : "") + "ORDER BY name", (result, row) -> mapSpecialization(result));
    }

    @Override
    public Doctor insertDoctor(Doctor value) {
        jdbc.update("INSERT INTO doctor (id, specialization_id, first_name, last_name, slot_duration_minutes, active, deactivated_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, clock_timestamp(), clock_timestamp())",
                value.id(), value.specializationId(), value.firstName(), value.lastName(), value.slotDurationMinutes(), value.active(), timestamp(value.deactivatedAt()));
        return doctor(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public Doctor updateDoctor(Doctor value, long expectedVersion) {
        int changed = jdbc.update("UPDATE doctor SET specialization_id = ?, first_name = ?, last_name = ?, slot_duration_minutes = ?, active = ?, "
                        + "deactivated_at = ?, version = version + 1, updated_at = clock_timestamp() WHERE id = ? AND version = ?",
                value.specializationId(), value.firstName(), value.lastName(), value.slotDurationMinutes(), value.active(), timestamp(value.deactivatedAt()), value.id(), expectedVersion);
        if (changed != 1) throw SchedulingException.conflict();
        return doctor(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public Optional<Doctor> doctor(UUID id) {
        return jdbc.query("SELECT id, specialization_id, first_name, last_name, slot_duration_minutes, active, deactivated_at, version "
                + "FROM doctor WHERE id = ?", (result, row) -> mapDoctor(result), id).stream().findFirst();
    }

    @Override
    public List<Doctor> doctors(boolean activeOnly) {
        return jdbc.query("SELECT id, specialization_id, first_name, last_name, slot_duration_minutes, active, deactivated_at, version "
                + "FROM doctor " + (activeOnly ? "WHERE active " : "") + "ORDER BY last_name, first_name", (result, row) -> mapDoctor(result));
    }

    private static Specialization mapSpecialization(ResultSet result) throws SQLException {
        return new Specialization(result.getObject("id", UUID.class), result.getString("name"), result.getBoolean("active"),
                instant(result, "deactivated_at"), result.getLong("version"));
    }

    private static Doctor mapDoctor(ResultSet result) throws SQLException {
        return new Doctor(result.getObject("id", UUID.class), result.getObject("specialization_id", UUID.class),
                result.getString("first_name"), result.getString("last_name"), result.getInt("slot_duration_minutes"),
                result.getBoolean("active"), instant(result, "deactivated_at"), result.getLong("version"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
