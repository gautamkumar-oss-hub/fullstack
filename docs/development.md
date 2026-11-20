# Platform development guide

## Prerequisites

Use the existing Java 25, Maven 3.9+, Node.js 26, npm, Docker Engine, and Docker Compose installation. Do not run the application as root or mount the Docker socket into containers.

Run commands from the repository root unless a command specifies another directory.

## Local startup

1. Copy `.env.example` to `.env` and replace the local PostgreSQL password. This file is ignored by Git.
2. Start only the project-scoped database: `docker compose up -d postgres`.
3. Check readiness: `docker compose ps` and `docker compose logs postgres`.
4. For isolated local identity diagnostics, run `backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev,identity-dev`.
5. Run the web application: `npm --prefix frontend run dev`.
6. Open http://localhost:5173. API documentation is at http://localhost:8080/swagger-ui.html and health at http://localhost:8080/actuator/health.

The default clinic zone is `UTC`; set `CLINIC_TIME_ZONE` to the clinic's approved IANA zone before scheduling work. All database sessions use UTC. V3 seeds booking horizon, proposal hold, and patient change cutoff values of 90 days, 24 hours, and 24 hours respectively. Administrators can change those numeric limits through the validated, optimistic configuration API/UI; the clinic zone remains a deployment setting.

## Phase 3 directory and scheduling

The scheduling module uses explicit JDBC repositories and transactional application services. Doctors have exactly one specialization and a fixed 5–180-minute slot duration in five-minute increments. Doctor records deliberately have no account/login fields, roles, or identity-account foreign key. Deactivation is history-preserving; inactive specializations or doctors cannot be used for new schedule operations.

Versioned endpoints are:

- `GET/POST /api/v1/directory/specializations`; `PATCH/DELETE /api/v1/directory/specializations/{id}?version=…`.
- `GET/POST /api/v1/directory/doctors`; `PATCH/DELETE /api/v1/directory/doctors/{id}?version=…`.
- `GET/POST /api/v1/doctors/{doctorId}/schedule`; `PATCH /api/v1/doctors/{doctorId}/schedule/{id}` with a version in the request body.
- `GET/POST /api/v1/doctors/{doctorId}/overrides`; `PATCH /api/v1/doctors/{doctorId}/overrides/{id}` with a version in the request body.
- `GET /api/v1/doctors/{doctorId}/availability?from=…&to=…` and `GET /api/v1/doctors/{doctorId}/schedule-conflict-preview?from=…&to=…` (admin/staff).
- Authenticated `GET /api/v1/configuration`; admin `PATCH /api/v1/configuration` with the current version.

Directory reads default to active records and permit authenticated roles. Inactive directory reads require admin/staff; all directory, schedule, and configuration mutations require admin. Staff and admin can read schedules, availability, and conflict previews. Anonymous requests still pass through the identity CSRF/authentication filters.

Weekly rules use weekdays 1–7 in persistence, inclusive effective dates, and clinic-local half-open time intervals. OPEN overrides must have bounded times and replace that date's recurring open periods; disjoint OPEN intervals combine. CLOSED and LEAVE remove time and can be full-day or bounded. Inverted/overlapping rules, overlong effective periods, inactive resources, and invalid local times are rejected. PostgreSQL GiST exclusion constraints backstop application checks against concurrent overlaps.

Availability uses the current doctor duration, schedule effective periods, overrides, clinic-local clock, and persisted booking horizon. Queries are limited to 31 inclusive days, cannot start in the past, and cannot extend beyond the horizon. Local-to-UTC conversion rejects DST gaps and ambiguous repeated times rather than silently adjusting them. Each response identifies the clinic zone and returns UTC slot instants. Slots do not reserve anything.

The Phase 5 `ActiveAllocationExclusion` port is the only allocation integration boundary. The Phase 3 adapter returns no intervals and marks preview `allocationChecksEnforced=false`. It creates no allocation tables and does not claim conflict enforcement. Scheduling audits include IDs, configuration versions, and non-secret state metadata, never credentials, tokens, or challenge codes.

The SPA routes `/workspace`, `/admin/directory`, `/admin/schedules`, `/admin/configuration`, and `/scheduling/availability` expose role-appropriate workflows. Existing `/account`, account administration, `/account/password`, forced-password handling, and identity transport remain intact.

## Local email and MFA diagnostics

MFA is a six-digit email one-time password (OTP), not authenticator-app TOTP. Patients use password login after email verification; staff and administrators also verify an email OTP. Staff accounts created by an administrator must change their initial password after signing in.

Plain `dev` defaults to the SMTP provider and requires working mail configuration. For local diagnostics, use both `dev` and `identity-dev`. This selects the no-delivery development email sender, uses the fixed local code `123456`, and returns it in the `developmentCode` response field. No external email is sent. These diagnostics require both profiles; never enable them outside local development. Normal profiles never expose development codes and require the SMTP provider.

Challenge defaults are five minutes for MFA, fifteen minutes for recovery, and 24 hours for email verification. Resend is throttled to one minute, with at most five attempts. Registration, login, and recovery responses avoid confirming whether an arbitrary email is registered.

### Account-state and audit policy

Email verification and password recovery never clear suspension. An outstanding code remains unusable and unconsumed after suspension; an administrator must resolve the account state and a new challenge must be requested. Verification cannot activate an already-active account or any suspended account.

Password recovery **may activate a pending-verification account**. This is an explicit policy decision: successful recovery proves control of the registered mailbox, so it sets the password and completes email verification in one operation. It never changes a suspended account to active and never replaces its password. All new passwords, including administrator replacement passwords, must be 12–128 characters with at least one letter and one number.

Security audits record administrator account creation and updates, role/status authorization changes, replacement passwords, first-admin bootstrap, and failed MFA/verification/recovery attempts. Events include the acting account ID and target account ID where applicable, plus non-secret reason codes or state transitions. They do not include submitted passwords, access or refresh tokens, one-time codes, or credential/token hashes.

## SMTP outside development

Set `IDENTITY_EMAIL_PROVIDER=smtp`, a nonblank `IDENTITY_EMAIL_FROM`, and `SPRING_MAIL_HOST`. For a typical authenticated submission service on port 587, also configure:

```sh
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<smtp-user>
SPRING_MAIL_PASSWORD=<smtp-secret>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true
```

Use the settings required by the actual mail provider. Host/from validation does not prove that delivery is authenticated or encrypted. Store SMTP credentials in a secret manager and test delivery with synthetic accounts.

## RSA signing keys

Outside `dev`, set both `IDENTITY_PRIVATE_KEY_PEM` and `IDENTITY_PUBLIC_KEY_PEM` to a matching persistent RSA key pair. One generation option is:

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out identity-private.pem
openssl pkey -in identity-private.pem -pubout -out identity-public.pem
```

Load the complete PKCS#8 private PEM and X.509 public PEM from the deployment secret manager. Preserve their BEGIN/END lines and encode newlines as required by the environment-variable mechanism. Never commit the private key. Change `IDENTITY_KEY_ID` when rotating keys and plan active-token/session rotation accordingly.

With no configured private key, `dev` generates an ephemeral RSA 3072-bit pair. Restarting development changes its signing key, so previously issued access tokens no longer validate. Only public keys are exposed at `/.well-known/jwks.json`.

Access tokens are ten-minute RS256 JWTs. `IDENTITY_ISSUER` and `IDENTITY_AUDIENCE` must match token issuance and validation, and `IDENTITY_KEY_ID` identifies the signing key. Role and authorization-version claims are checked against current account state; suspension, role changes, and password changes revoke or invalidate relevant sessions.

## Refresh, CSRF, and browser origins

The SPA holds access tokens only in memory, never in local or session storage. Opaque refresh tokens are hashed in PostgreSQL, rotated on refresh, limited to seven days idle and 30 days absolute, and reused sessions revoke their family. The HTTP-only `hospital_refresh` cookie is scoped to `/api/v1/auth`.

The readable `XSRF-TOKEN` cookie is sent back in `X-XSRF-TOKEN` for mutations. Both cookies use SameSite Strict. Outside development, Secure cookies are mandatory, so use HTTPS. The development profile permits insecure cookies only with the default loopback origins.

`ALLOWED_ORIGINS` is a comma-separated exact-origin allow-list for credentialed CORS and unsafe-request Origin validation. Include scheme, hostname, and port; do not use a wildcard with credentials. The defaults are `http://localhost:5173` and `http://127.0.0.1:5173`. Prefer one canonical browser origin, and deploy the SPA/API with compatible HTTPS origins and reverse-proxy cookie behavior. Cross-site cookie flows are not supported by this Strict-cookie design.

## One-time administrator bootstrap

Administrator accounts are not created by the account-administration UI. For initial setup, set:

```sh
IDENTITY_BOOTSTRAP_ENABLED=true
IDENTITY_BOOTSTRAP_EMAIL=<admin-email>
IDENTITY_BOOTSTRAP_PASSWORD=<unique-secret-of-at-least-16-characters>
IDENTITY_BOOTSTRAP_FIRST_NAME=<first-name>
IDENTITY_BOOTSTRAP_LAST_NAME=<last-name>
```

Start the API once. Bootstrap uses a database lock, runs only if no administrator exists, fails if that email belongs to a non-administrator, and records bootstrap metadata. Then set `IDENTITY_BOOTSTRAP_ENABLED=false` and remove the bootstrap credentials. Do not use a reusable or example password. Administrators can subsequently create staff or patient accounts in the SPA.

## Verification

- Backend: `backend/mvnw -f backend/pom.xml verify` (requires Docker for PostgreSQL Testcontainers).
- Frontend: `npm --prefix frontend ci`, then `npm --prefix frontend test`, `npm --prefix frontend run lint`, and `npm --prefix frontend run build`.
- API contract: start the local API and run `npm --prefix frontend run sync:api` to update the runtime-generated OpenAPI document and TypeScript types together.
- Compose: `POSTGRES_PASSWORD=validation-only docker compose config --quiet`.

The backend suite covers real PostgreSQL/Testcontainers migrations, empty-schema and existing-V2 upgrades, V3 constraints/indexes, UTC/DST behavior, scheduling and identity permissions, optimistic concurrency, deactivation history, conflict-preview honesty, and committed-versus-served OpenAPI consistency.

The committed OpenAPI file is generated from the running API. `ApplicationIntegrationTest` compares the served document with `docs/openapi.json`, excluding only the environment-specific generated server URL, and separately checks response statuses, bearer security, managed-role enums, required response fields, password patterns, and the absence of a synthetic CSRF query parameter. Thus Maven verify and CI detect schema drift and semantic regressions. `generate:api:file` regenerates types from the committed contract in CI, followed by a generated-file diff check. Review and commit both generated files after an intentional contract change.

Current non-blocking frontend diagnostics are React Hook Form compiler compatibility, mixed provider/hook exports, and Vite's large main-chunk warning. Re-run `npm --prefix frontend audit` for current dependency information rather than relying on a historical advisory count.

## Database lifecycle

Flyway is the sole schema authority; Hibernate validates mappings and never creates the schema. V1 provides the foundation and `btree_gist`; V2 adds identity accounts, profiles, challenges, refresh sessions, and identity audit records. V3 adds specialization/doctor directory tables, weekly availability, schedule overrides, and singleton clinic configuration. It includes case-normalized specialization uniqueness, specialization foreign-key protection, slot/check constraints, optimistic versions, lookup indexes, and GiST overlap exclusions. Do not edit the schema manually. Allocation and later business tables belong to their owning later modules.

Stop the database without deleting data: `docker compose stop postgres`. Destructive volume removal is intentionally not part of the standard workflow.

## Phase boundary

Phase 3 provides identity/security, the clinical directory, schedules, informational availability, and clinic configuration. Patient appointment requests/preferences, proposals, holds, allocations, confirmations, cancellations, completion, no-show, rescheduling, notification workflows/outbox, business dashboards, and deployment are not implemented. No regulatory-compliance or production-readiness claim is made; this remains an educational/demo MVP for synthetic data.
