# User management path: beta-readiness workstream

Audit date: 18 July 2026

Status: **Baseline prepared; not beta-ready.** No repository in this workstream
may be described as beta-ready until every item in the Definition of Done is
demonstrated and all Critical/High findings are closed or, where the Definition
permits, explicitly accepted.

## Scope

Fully audited:

- `job-seeker-copilot-client`
- `user-management-gateway`
- `authentication-service`
- `user-profile-service`
- `location-gateway`
- `postcode-io-gateway`
- build and contract tooling needed by this path

Not audited beyond transitive/build impact: job finding, documents, reporting,
payments, AI providers, landing-page application/backend, root infrastructure,
AWS/Amplify and production deployment.

## Verified architecture

```mermaid
flowchart TD
    B[Browser / Angular] -->|same-origin /api| S[Express SSR / BFF]
    S -->|register, login, profile + Bearer| U[User Management Gateway]
    U -->|generated client: register/login/me| A[Authentication Service]
    U -->|generated client: X-User-Id| P[User Profile Service]
    S -->|postcode and advertised search| L[Location Gateway]
    L -->|generated client: postcode| G[Postcode.io Gateway]
    G -->|LIVE| E[api.postcodes.io]
    G -.->|FIXTURE; generated client| D[System Data Service]
```

Key corrections to the assumed diagram:

- Express SSR is a runtime BFF between Angular and both public gateways.
- Location is a separate client/BFF path; user-management-gateway does not call
  location-gateway.
- `system-data-service` is an unapproved compile/runtime transitive dependency
  of postcode fixture mode.
- Build-time generated clients form additional coupling edges.

## Build architecture

`build-tools` is **not required** by the selected path: none of the five Maven
services declares it as parent or dependency. No `build-tools` repository will
be created. Reproducibility is blocked instead by `systemPath` JARs and
generated TypeScript trees:

| Consumer | Missing clean-clone input |
|---|---|
| Client | Generated user-management, location and out-of-scope API sources/contracts |
| User-management gateway | Authentication and user-profile client JARs |
| Location gateway | Postcode gateway client JAR |
| Postcode.io gateway | System-data-service client JAR |

Generated binary JARs are deliberately not committed.

## Current functionality

| Capability | State | Evidence/constraint |
|---|---|---|
| Registration | Partial | Auth account and profile are created synchronously; partial failure is not compensated. |
| Login | Present | BCrypt comparison and signed access token; enumeration/rate controls absent. |
| Logout | Client-only | Clears browser state; no server revocation. |
| Token expiry | Present | 24-hour JWT expiry. |
| Refresh/revocation | Absent | No refresh token, rotation, blacklist/session or logout endpoint. |
| Profile create/read/update | Present | Gateway derives ID from JWT, downstream directly trusts `X-User-Id`. |
| Password change/reset | Absent | Beta requirement/deferral must be decided. |
| Account deletion/export | Absent | Privacy/retention decision required. |
| Postcode lookup | Present | Full/outcode provider calls; error mapping/resilience incomplete. |
| Location name search | Broken/absent | Client calls `/api/locations`; service does not expose it. |
| Duplicate registration | Present | Authentication service returns conflict, with enumeration concerns. |
| Session expiration UX | Partial | Some 401 paths log out; no central guard/refresh/startup validation. |

## Baseline evidence

| Repository | Build/test baseline | Secret baseline | Status |
|---|---|---|---|
| Client | 28 tests and current-tree production build pass; lint fails (76 errors); clean build blocked; npm audit reports 4 High and 3 Low chains | No confirmed client credential; local caches/generated output excluded | Blocked |
| User-management gateway | 17 tests pass only with local untracked client JARs | No Gitleaks finding in legacy history | Blocked |
| Authentication service | All 16 tests pass in approved host networking | Public root `.env` credential finding and hard-coded signing config require rotation/sanitisation | Blocked |
| User-profile service | 22 tests pass | No legacy-history Gitleaks finding | Blocked |
| Location gateway | 4 tests pass only with local untracked client JAR | No legacy-history Gitleaks finding | Blocked |
| Postcode.io gateway | 3 tests pass only with local untracked client JAR | No legacy-history Gitleaks finding | Blocked |

Gitleaks 8.30.1 was run in a network-disabled, read-only, capability-free
container. Targeted checks supplement it for hard-coded JWT/provider secrets,
private keys, `.env`, personal email fixtures and generated/binary artefacts.
Reports never reproduce secret values.

The sanitised current trees and complete imported histories pass Gitleaks. Nine
authentication-history findings were individually reviewed as synthetic README
examples or a test fixture and suppressed only by exact commit/file/rule/line
fingerprints. The known public-root `.env` finding is not imported. Rotation of
any real credential or signing value that was deployed or reused remains
[AUTH-01](https://github.com/jobseekercopilot/authentication-service/issues/1).

Backend CI currently produces Maven dependency inventories but does not yet
provide a dependable vulnerability gate. This is recorded explicitly in each
backend backlog rather than treating `dependency:tree` as a security scan. The
first authentication OWASP scan reported 10 affected dependency records,
including 17 Critical and 38 High entries before triage, plus two feed-record
processing errors; [AUTH-11](https://github.com/jobseekercopilot/authentication-service/issues/11)
owns upgrade, reachability/false-positive review and a reliable CI gate.

## Critical and high findings

Critical/P0:

1. Clean builds depend on generated sources or untracked `systemPath` JARs.
2. Authentication signing/public-history credentials require sanitisation and
   owner-controlled rotation.
3. User-profile-service trusts a forgeable `X-User-Id` header.
4. Postcode fixture mode depends on unapproved `system-data-service` tooling.

High/P1 themes:

- no refresh/revocation/logout design, weak password validation and no brute
  force/account-enumeration controls;
- partial registration and no bounded downstream resilience;
- development databases/consoles and automatic schema update;
- missing location search, unstable error mapping and provider resilience;
- bearer token/profile PII in browser local storage;
- incomplete negative/security/contract/E2E tests;
- no beta dashboard/alerts, migration/restore or rollback evidence.

Repository-level evidence, exact files, acceptance criteria, dependencies and
effort are in each `docs/BETA_READINESS_AUDIT.md` and its matching GitHub issue.

## Definition of Done: controlled private beta

- [ ] All six repositories build from fresh private clones.
- [ ] CI passes on `develop`.
- [ ] No real secrets or generated build artefacts are tracked.
- [ ] No unresolved Critical or High security finding remains.
- [ ] Dependency scans contain no unaccepted Critical or High issue.
- [ ] Registration, authentication, profile and location flows work together.
- [ ] Negative and unauthorised scenarios are tested.
- [ ] Cross-user data access is prevented and tested.
- [ ] Production configuration is separate from local configuration.
- [ ] Database schemas and migrations are reproducible.
- [ ] External provider failures are handled safely.
- [ ] Logs contain no passwords, tokens or unnecessary personal data.
- [ ] Health and readiness checks exist.
- [ ] Minimum beta monitoring and alert requirements are documented.
- [ ] Every repository has accurate setup and operational documentation.
- [ ] The complete browser journey passes in automated integration/E2E.
- [ ] A clean-environment deployment and smoke test has succeeded.
- [ ] Remaining Medium and Low risks are documented and accepted.
- [ ] A rollback procedure is documented and exercised.
- [ ] The project has no open item marked `Beta blocker` for this workstream.

“Done” means ready for a controlled private beta. It does not guarantee the
absence of every defect or security risk.

## Recommended implementation order

1. `feature/reproducible-api-client-builds` — replace binary/generated local
   dependencies and make every clean clone compile.
2. Rotate credentials; implement JWT/session and service-identity boundaries.
3. Add production migrations/profile ownership and lifecycle/privacy controls.
4. Complete location validation, resilience, search decision and provider tests.
5. Add full browser E2E, accessibility, telemetry, alerts, deployment smoke and
   rollback runbook.

## Links

- [Private Job Seeker Copilot Project](https://github.com/users/jobseekercopilot/projects/1)
- [Parent epic: User management path ready for private beta](https://github.com/jobseekercopilot/user-management-gateway/issues/12)
- [Client audit backlog](https://github.com/jobseekercopilot/job-seeker-copilot-client/issues)
- [User-management gateway audit backlog](https://github.com/jobseekercopilot/user-management-gateway/issues)
- [Authentication audit backlog](https://github.com/jobseekercopilot/authentication-service/issues)
- [User-profile audit backlog](https://github.com/jobseekercopilot/user-profile-service/issues)
- [Location audit backlog](https://github.com/jobseekercopilot/location-gateway/issues)
- [Postcode.io audit backlog](https://github.com/jobseekercopilot/postcode-io-gateway/issues)

The epic has all 58 canonical findings attached as native cross-repository
sub-issues. The private Project contains those findings plus the epic, with
Status, Priority, Workstream, Service, Beta blocker, Effort and Type populated.
It remains open until the full Definition of Done is demonstrated.
