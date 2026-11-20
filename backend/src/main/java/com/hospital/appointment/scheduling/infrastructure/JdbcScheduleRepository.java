package com.hospital.appointment.scheduling.infrastructure;

import com.hospital.appointment.scheduling.application.ScheduleRepository;
import com.hospital.appointment.scheduling.application.SchedulingException;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.OverrideType;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.ScheduleOverride;
import com.hospital.appointment.scheduling.domain.DirectoryRecords.WeeklyAvailability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcScheduleRepository implements ScheduleRepository {
    private final JdbcTemplate jdbc;

    JdbcScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WeeklyAvailability insertWeekly(WeeklyAvailability value) {
        jdbc.update("INSERT INTO doctor_weekly_availability (id, doctor_id, weekday, start_local, end_local, effective_from, effective_to, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, clock_timestamp(), clock_timestamp())",
                value.id(), value.doctorId(), value.weekday().getValue(), Time.valueOf(value.startLocal()), Time.valueOf(value.endLocal()),
                value.effectiveFrom(), value.effectiveTo());
        return weekly(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public WeeklyAvailability updateWeekly(WeeklyAvailability value, long expectedVersion) {
        int changed = jdbc.update("UPDATE doctor_weekly_availability SET weekday = ?, start_local = ?, end_local = ?, effective_from = ?, "
                        + "effective_to = ?, version = version + 1, updated_at = clock_timestamp() WHERE id = ? AND version = ?",
                value.weekday().getValue(), Time.valueOf(value.startLocal()), Time.valueOf(value.endLocal()), value.effectiveFrom(),
                value.effectiveTo(), value.id(), expectedVersion);
        if (changed != 1) throw SchedulingException.conflict();
        return weekly(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public Optional<WeeklyAvailability> weekly(UUID id) {
        return jdbc.query("SELECT id, doctor_id, weekday, start_local, end_local, effective_from, effective_to, version "
                + "FROM doctor_weekly_availability WHERE id = ?", (result, row) -> mapWeekly(result), id).stream().findFirst();
    }

    @Override
    public List<WeeklyAvailability> weeklyRules(UUID doctorId) {
        return jdbc.query("SELECT id, doctor_id, weekday, start_local, end_local, effective_from, effective_to, version "
                + "FROM doctor_weekly_availability WHERE doctor_id = ? ORDER BY weekday, start_local, effective_from", (result, row) -> mapWeekly(result), doctorId);
    }

    @Override
    public List<WeeklyAvailability> weeklyRules(UUID doctorId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT id, doctor_id, weekday, start_local, end_local, effective_from, effective_to, version "
                + "FROM doctor_weekly_availability WHERE doctor_id = ? AND effective_from <= ? AND effective_to >= ? "
                + "ORDER BY weekday, start_local", (result, row) -> mapWeekly(result), doctorId, to, from);
    }

    @Override
    public ScheduleOverride insertOverride(ScheduleOverride value) {
        jdbc.update("INSERT INTO doctor_schedule_override (id, doctor_id, override_date, override_type, start_local, end_local, reason, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, clock_timestamp(), clock_timestamp())",
                value.id(), value.doctorId(), value.date(), value.type().name(), time(value.startLocal()), time(value.endLocal()), value.reason());
        return override(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public ScheduleOverride updateOverride(ScheduleOverride value, long expectedVersion) {
        int changed = jdbc.update("UPDATE doctor_schedule_override SET override_date = ?, override_type = ?, start_local = ?, end_local = ?, "
                        + "reason = ?, version = version + 1, updated_at = clock_timestamp() WHERE id = ? AND version = ?",
                value.date(), value.type().name(), time(value.startLocal()), time(value.endLocal()), value.reason(), value.id(), expectedVersion);
        if (changed != 1) throw SchedulingException.conflict();
        return override(value.id()).orElseThrow(SchedulingException::conflict);
    }

    @Override
    public Optional<ScheduleOverride> override(UUID id) {
        return jdbc.query("SELECT id, doctor_id, override_date, override_type, start_local, end_local, reason, version "
                + "FROM doctor_schedule_override WHERE id = ?", (result, row) -> mapOverride(result), id).stream().findFirst();
    }

    @Override
    public List<ScheduleOverride> overrides(UUID doctorId) {
        return jdbc.query("SELECT id, doctor_id, override_date, override_type, start_local, end_local, reason, version "
                + "FROM doctor_schedule_override WHERE doctor_id = ? ORDER BY override_date, start_local NULLS FIRST", (result, row) -> mapOverride(result), doctorId);
    }

    @Override
    public List<ScheduleOverride> overrides(UUID doctorId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT id, doctor_id, override_date, override_type, start_local, end_local, reason, version "
                + "FROM doctor_schedule_override WHERE doctor_id = ? AND override_date BETWEEN ? AND ? ORDER BY override_date, start_local NULLS FIRST",
                (result, row) -> mapOverride(result), doctorId, from, to);
    }

    private static WeeklyAvailability mapWeekly(ResultSet result) throws SQLException {
        return new WeeklyAvailability(result.getObject("id", UUID.class), result.getObject("doctor_id", UUID.class),
                DayOfWeek.of(result.getInt("weekday")), result.getTime("start_local").toLocalTime(), result.getTime("end_local").toLocalTime(),
                result.getObject("effective_from", LocalDate.class), result.getObject("effective_to", LocalDate.class), result.getLong("version"));
    }

    private static ScheduleOverride mapOverride(ResultSet result) throws SQLException {
        Time start = result.getTime("start_local");
        Time end = result.getTime("end_local");
        return new ScheduleOverride(result.getObject("id", UUID.class), result.getObject("doctor_id", UUID.class),
                result.getObject("override_date", LocalDate.class), OverrideType.valueOf(result.getString("override_type")),
                start == null ? null : start.toLocalTime(), end == null ? null : end.toLocalTime(), result.getString("reason"), result.getLong("version"));
    }

    private static Time time(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }
}
