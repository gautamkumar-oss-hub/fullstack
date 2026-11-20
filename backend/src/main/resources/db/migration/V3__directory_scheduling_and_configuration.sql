CREATE TABLE specialization (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    normalized_name varchar(120) NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT true,
    deactivated_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (btrim(name) <> ''),
    CHECK (active = (deactivated_at IS NULL))
);

CREATE TABLE doctor (
    id uuid PRIMARY KEY,
    specialization_id uuid NOT NULL REFERENCES specialization(id) ON DELETE RESTRICT,
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    slot_duration_minutes integer NOT NULL CHECK (slot_duration_minutes BETWEEN 5 AND 180 AND slot_duration_minutes % 5 = 0),
    active boolean NOT NULL DEFAULT true,
    deactivated_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (btrim(first_name) <> ''),
    CHECK (btrim(last_name) <> ''),
    CHECK (active = (deactivated_at IS NULL))
);
CREATE INDEX doctor_specialization_active_idx ON doctor(specialization_id, active);
CREATE INDEX doctor_active_name_idx ON doctor(active, last_name, first_name);

CREATE TABLE doctor_weekly_availability (
    id uuid PRIMARY KEY,
    doctor_id uuid NOT NULL REFERENCES doctor(id) ON DELETE RESTRICT,
    weekday smallint NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_local time NOT NULL,
    end_local time NOT NULL,
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (start_local < end_local),
    CHECK (effective_from <= effective_to),
    CONSTRAINT doctor_weekly_no_overlap EXCLUDE USING gist (
        doctor_id WITH =,
        weekday WITH =,
        daterange(effective_from, effective_to, '[]') WITH &&,
        int4range(EXTRACT(HOUR FROM start_local)::integer * 60 + EXTRACT(MINUTE FROM start_local)::integer,
                  EXTRACT(HOUR FROM end_local)::integer * 60 + EXTRACT(MINUTE FROM end_local)::integer, '[)') WITH &&
    )
);
CREATE INDEX doctor_weekly_lookup_idx ON doctor_weekly_availability(doctor_id, effective_from, effective_to);

CREATE TABLE doctor_schedule_override (
    id uuid PRIMARY KEY,
    doctor_id uuid NOT NULL REFERENCES doctor(id) ON DELETE RESTRICT,
    override_date date NOT NULL,
    override_type varchar(8) NOT NULL CHECK (override_type IN ('OPEN', 'CLOSED', 'LEAVE')),
    start_local time,
    end_local time,
    reason varchar(500),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK ((start_local IS NULL) = (end_local IS NULL)),
    CHECK (start_local IS NULL OR start_local < end_local),
    CHECK (reason IS NULL OR btrim(reason) <> ''),
    CONSTRAINT doctor_override_no_overlap EXCLUDE USING gist (
        doctor_id WITH =,
        override_date WITH =,
        int4range(COALESCE(EXTRACT(HOUR FROM start_local)::integer * 60 + EXTRACT(MINUTE FROM start_local)::integer, 0),
                  COALESCE(EXTRACT(HOUR FROM end_local)::integer * 60 + EXTRACT(MINUTE FROM end_local)::integer, 1440), '[)') WITH &&
    )
);
CREATE INDEX doctor_override_lookup_idx ON doctor_schedule_override(doctor_id, override_date);

CREATE TABLE clinic_configuration (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    booking_horizon_days integer NOT NULL CHECK (booking_horizon_days BETWEEN 1 AND 365),
    proposal_hold_hours integer NOT NULL CHECK (proposal_hold_hours BETWEEN 1 AND 168),
    patient_change_cutoff_hours integer NOT NULL CHECK (patient_change_cutoff_hours BETWEEN 1 AND 720),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL
);

INSERT INTO clinic_configuration (booking_horizon_days, proposal_hold_hours, patient_change_cutoff_hours, updated_at)
VALUES (90, 24, 24, clock_timestamp());
