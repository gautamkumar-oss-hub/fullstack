CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Authentication is implemented in Phase 2. The clinic zone and workflow defaults
-- are immutable deployment settings, exposed by the typed application configuration.
CREATE TABLE application_metadata (
    metadata_key varchar(100) PRIMARY KEY,
    description varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO application_metadata (metadata_key, description)
VALUES ('schema', 'Single-facility appointment platform; all timestamps use UTC-backed timestamptz.');
