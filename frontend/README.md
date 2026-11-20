# Hospital appointment SPA

React, TypeScript, Material UI, React Router, TanStack Query, React Hook Form, and Zod implement the public overview, identity workflows, and Phase 3 directory/scheduling administration.

## Routes

- `/` — public system overview and scheduling-configuration limits.
- `/login` and `/mfa` — password login and staff/administrator six-digit email OTP verification.
- `/register`, `/verify`, and `/recover` — patient registration, email verification, and password recovery.
- `/account/password` — authenticated password changes, including forced staff password changes.
- `/account` — account details; administrators can also create and update staff/patient accounts.
- `/workspace` — role-appropriate admin, staff, or patient landing page.
- `/admin/directory` — specialization and doctor create/update/deactivation (admin).
- `/admin/schedules` — recurring weekly availability and date-specific overrides (admin).
- `/admin/configuration` — optimistic booking horizon, hold, and patient cutoff configuration (admin).
- `/scheduling/availability` — active-doctor fixed-slot availability and non-enforcing conflict-preview notice (admin/staff).

The account API remains authoritative for roles, status, and authorization. Hiding administrator controls is not a security boundary. Doctors never receive account or login capabilities. Availability is informational only and does not reserve a slot; allocation conflict enforcement awaits Phase 5.

## Session and request model

The access token is held only in the module-local [identity service](src/features/identity/service.ts); it is never written to local storage or session storage. A full page reload restores the session through the HTTP-only refresh cookie. Concurrent authenticated 401 responses share one refresh request; 403 responses never trigger refresh. Logout and password changes clear local session state, and logout clears React Query account data.

Requests include credentials. Mutations lazily obtain a CSRF token from `/api/v1/auth/csrf` when needed and send it in `X-XSRF-TOKEN`. The refresh and readable CSRF cookies use SameSite Strict; production requires Secure cookies and HTTPS. Use an exact configured browser origin, including its port. The development Vite server proxies API requests to `VITE_API_TARGET`, defaulting to `http://localhost:8080`.

MFA means email one-time passwords, not authenticator-app TOTP. A development code is displayed only if the API explicitly returns it in the isolated `dev,identity-dev` diagnostics mode. Normal profiles do not expose codes.

## Commands

Run these from the frontend directory:

```sh
npm ci
npm run dev
npm test
npm run lint
npm run build
```

With the development API running, `npm run sync:api` updates the committed OpenAPI document and generated TypeScript definitions together. `npm run generate:api:file` regenerates types from the committed document; CI checks that this produces no diff.

## Tests

Vitest and Testing Library cover session restoration and cleanup, in-memory token behavior, shared refresh, 401 versus 403 behavior, MFA and forced-password routing, patient workflows, role permissions, directory/configuration submission, optimistic conflict errors, schedule validation, accessible loading/empty states, local/UTC availability, and honest Phase 5 notices. HTTP is mocked at the fetch boundary; integration with a live API requires the backend and PostgreSQL.

Lint currently retains non-blocking warnings for React Hook Form's compiler compatibility and the authentication provider's mixed component/hook export. Vite also reports a non-blocking large-chunk warning for the current production bundle.

See the [root development guide](../docs/development.md) for backend, SMTP, keys, cookies, bootstrap, and API-contract instructions.
