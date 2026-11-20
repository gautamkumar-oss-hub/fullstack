CREATE TABLE identity_account (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL,
    normalized_email varchar(320) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('ADMIN', 'STAFF', 'PATIENT')),
    status varchar(24) NOT NULL CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED')),
    email_verified boolean NOT NULL DEFAULT false,
    email_verified_at timestamptz,
    force_password_change boolean NOT NULL DEFAULT false,
    password_changed_at timestamptz NOT NULL,
    authz_version bigint NOT NULL DEFAULT 1 CHECK (authz_version > 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    last_login_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (email_verified_at IS NULL OR email_verified = true)
);

CREATE TABLE identity_profile (
    account_id uuid PRIMARY KEY REFERENCES identity_account(id) ON DELETE CASCADE,
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    phone varchar(40),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE identity_refresh_session (
    id uuid PRIMARY KEY,
    family_id uuid NOT NULL,
    family_created_at timestamptz NOT NULL,
    account_id uuid NOT NULL REFERENCES identity_account(id) ON DELETE CASCADE,
    token_hash char(64) NOT NULL UNIQUE,
    parent_id uuid REFERENCES identity_refresh_session(id),
    created_at timestamptz NOT NULL,
    last_used_at timestamptz NOT NULL,
    idle_expires_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    rotated_at timestamptz,
    revoked_at timestamptz,
    revoke_reason varchar(80)
);
CREATE INDEX identity_refresh_account_idx ON identity_refresh_session(account_id);
CREATE INDEX identity_refresh_family_idx ON identity_refresh_session(family_id);
CREATE INDEX identity_refresh_cleanup_idx ON identity_refresh_session(absolute_expires_at);

CREATE TABLE identity_challenge (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES identity_account(id) ON DELETE CASCADE,
    challenge_type varchar(32) NOT NULL CHECK (challenge_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RECOVERY', 'MFA')),
    secret_hash char(64) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    send_count integer NOT NULL DEFAULT 1 CHECK (send_count > 0),
    next_resend_at timestamptz
);
CREATE INDEX identity_challenge_account_idx ON identity_challenge(account_id, challenge_type);
CREATE INDEX identity_challenge_cleanup_idx ON identity_challenge(expires_at);

CREATE TABLE identity_security_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type varchar(80) NOT NULL,
    account_id uuid,
    occurred_at timestamptz NOT NULL,
    source_address varchar(100),
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    CHECK (jsonb_typeof(details) = 'object')
);
CREATE INDEX identity_security_audit_account_idx ON identity_security_audit(account_id, occurred_at);
