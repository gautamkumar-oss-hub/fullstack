# Hospital Appointment Platform

Single-facility appointment management MVP: Spring Boot modular monolith, PostgreSQL, and a strict React TypeScript SPA.

**Current scope: Phase 3 — admin directory and scheduling, including identity/security and the public foundation overview.** Patient booking and allocation workflows are not implemented.

## Implemented

- Patient registration, email verification, login, password recovery, and logout.
- Six-digit email one-time-password MFA for staff and administrators; this is not TOTP.
- Ten-minute RS256 access tokens held in frontend memory, rotating opaque refresh sessions in an HTTP-only cookie, and CSRF protection.
- Forced staff password changes, account suspension and role/status invalidation, and optimistic-locking account administration.
- Public system information and JWKS; protected account APIs and administrator metrics.
- Specialization and doctor administration with exactly one specialization and a doctor-specific fixed slot duration. Doctors are directory resources, not accounts or authentication principals.
- Optimistic, history-preserving directory changes; validated recurring weekly availability and date-specific OPEN/CLOSED/LEAVE overrides.
- Clinic-local fixed-slot availability with UTC instants, strict DST handling, current booking-horizon enforcement, and staff/admin access.
- Validated, optimistic, audited clinic configuration for the booking horizon, proposal hold, and patient change cutoff.
- Role-aware admin/staff/patient landing shells and accessible responsive administration workflows using generated OpenAPI types.
- Flyway-managed PostgreSQL persistence, exclusion constraints, and non-secret identity/scheduling audit records.

Patient appointment requests, proposals, holds, allocations, confirmations, notifications, business dashboards, and deployment remain outside Phase 3. Conflict preview explicitly reports that allocation enforcement awaits Phase 5; no allocation tables or reservation behavior are introduced.

## Project map

- [Backend](backend)
- [Frontend](frontend)
- [OpenAPI contract](docs/openapi.json)
- [Setup, security, and verification guide](docs/development.md)
- [Frontend guide](frontend/README.md)
- [CI checks](.github/workflows/ci.yml)

## Verification

The backend suite includes real PostgreSQL/Testcontainers coverage, migration and UTC checks, identity security regressions, and committed-versus-served OpenAPI consistency. The frontend suite covers authentication workflows, role-aware routing, session restoration, shared refresh, and browser-token storage boundaries. See the [development guide](docs/development.md) for commands and current non-blocking diagnostics.

This is an educational/demo MVP for synthetic data. It is not a regulatory-compliance certification or a production-readiness claim.
