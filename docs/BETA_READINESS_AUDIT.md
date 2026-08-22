# Beta-readiness audit: user-management gateway

Audit date: 18 July 2026

Status: **Not beta-ready.** The facade has a reproducible build, bounded
downstream calls and working mocked tests, but its distributed transaction and
other open beta-readiness findings still block a controlled beta.

## Verified role and baseline

At runtime this service calls authentication-service for register, login and
`/me`, then calls user-profile-service with the authenticated user ID. It does
not call location-gateway. The original audit found that `mvn -B verify` passed
17 tests only because two untracked binary clients existed under `libs/`.
UMG-01 now generates those clients from versioned consumer contracts during
`generate-sources`; a clean `mvn -B clean verify` passes the same 17 tests
without sibling repositories or local binaries. The service remains **not
beta-ready** while the other findings below are open.

## Findings

| ID | Finding | Evidence | Risk and severity | Recommended solution and acceptance criteria | Dependencies | Beta blocker | Effort |
|---|---|---|---|---|---|---|---|
| [UMG-01](https://github.com/jobseekercopilot/user-management-gateway/issues/1) | Replace `systemPath` client JARs | `pom.xml` references authentication and profile clients under `libs/`; the directory is untracked and intentionally excluded. | **Critical / P0 build:** fresh clones and CI cannot compile. | Publish versioned clients from contracts to an approved package repository or generate them deterministically during the build; remove `systemPath`; prove a fresh-clone `mvn -B verify`. | Authentication/profile contracts and package policy. | Yes | L |
| [UMG-02](https://github.com/jobseekercopilot/user-management-gateway/issues/2) | Make registration failure-safe and retryable | `UserManagementService.register` creates auth account, logs in, loads `/me`, then writes profile with no compensation/idempotency. | **High / P1 reliability/data:** profile failure leaves a valid account while the client receives 500; retry becomes duplicate registration. | Define an idempotency key/state machine or compensation; return stable outcomes; test every partial failure and retry. | Authentication/profile API changes. | Yes | L |
| [UMG-03](https://github.com/jobseekercopilot/user-management-gateway/issues/3) | Add bounded downstream resilience | Generated clients have no configured connect/read deadlines, retry budget, circuit breaker or bulkhead. | **High / P1 reliability:** a synchronous chain can exhaust gateway resources and amplify outages. | Add per-operation timeouts, safe retry rules, circuit breaking and dependency readiness; test slow, unavailable and partial downstreams. | Generated-client configuration. | Yes | M |
| [UMG-04](https://github.com/jobseekercopilot/user-management-gateway/issues/4) | Enforce contract validation and safe errors | **Remediated:** Bean Validation covers request/nested DTO boundaries; bodies and media types are bounded; failures use a versioned safe schema. | **High / P1 security/API, mitigated:** malformed input and internal details no longer reach downstreams or clients. | Keep the error schema backward compatible and align constraints when downstream contracts change. | AUTH-02 complete. | No | M |
| [UMG-05](https://github.com/jobseekercopilot/user-management-gateway/issues/5) | Establish the public gateway security baseline | **Remediated:** UMG is the cookie session boundary with deny-by-default routing, CSRF, exact credentialed origins, bounded auth rates and safe security headers/errors. | **High / P1 security, mitigated in repository:** browser token custody, brute force and accidental route exposure now have explicit controls; deployment TLS/proxy and multi-instance coordination remain platform validation. | Retain negative security/configuration tests and validate the documented production profile before beta deployment. | AUTH-13 and PROFILE-01 complete; CLIENT-02/03 must adopt the contract. | No | L |
| [UMG-06](https://github.com/jobseekercopilot/user-management-gateway/issues/6) | Make OpenAPI express real authentication and ownership | **Remediated:** protected operations use HttpOnly cookie schemes, ownership selectors/dead parameters and token response fields are absent, stable error envelopes are documented, and unknown request fields fail closed. | The public generated contract matches the UMG-05 browser boundary. | Retain OpenAPI/unknown-field contract tests and coordinate reviewed public schema changes with consumers. | UMG-01 and UMG-05 complete. | No | M |
| [UMG-07](https://github.com/jobseekercopilot/user-management-gateway/issues/7) | Add real integration and negative security tests | Existing service tests mock generated APIs; there is no multi-service registration/profile test, cross-user test, timeout test or browser path. | **High / P1 testing:** orchestration and ownership guarantees are unproven. | Add container/fixture integration plus contract tests covering success, duplicates, invalid/expired token, cross-user access and partial failures. | AUTH/PROFILE test fixtures. | Yes | L |
| [UMG-08](https://github.com/jobseekercopilot/user-management-gateway/issues/8) | Add service-level telemetry and readiness | **Remediated:** bounded operation/auth outcome metrics and latency histograms, redacted aggregate health, dependency readiness, safe correlation validation/propagation tests, and a dashboard/alert contract are present. A production exporter and alert routing remain platform-owned. | **Medium / P1 observability:** repository controls are complete; target-environment alert delivery remains a beta-readiness validation item. | Connect the vendor-neutral meter registry to the approved private platform and prove dashboard/alert delivery in the beta environment. | Monitoring platform owner; no paid or production change is made here. | Yes | M |
| [UMG-09](https://github.com/jobseekercopilot/user-management-gateway/issues/9) | Harden the container and stop skipping tests | Docker runs `mvn ... -DskipTests`, uses mutable/root images and has no health check; before UMG-01 it also copied `libs`. | **Medium / P1 devops:** the image still bypasses verification and lacks a hardened runtime baseline. | Run verify in CI/build, pin images, use non-root runtime, health check and image scan. | UMG-01. | Yes | M |
| [UMG-10](https://github.com/jobseekercopilot/user-management-gateway/issues/10) | Correct operational and API documentation | README examples use obsolete profile fields/response keys and overstate WireMock/comprehensive coverage. | **Medium / P1 documentation:** operators and client developers receive inaccurate instructions. | Document actual endpoints, contracts, environment, secrets, health/readiness, troubleshooting and branch workflow; validate every command from a clean clone. | UMG-01. | Yes | S |
| [UMG-11](https://github.com/jobseekercopilot/user-management-gateway/issues/11) | Establish reliable dependency vulnerability scanning | CI emits `mvn dependency:tree` but performs no vulnerability analysis; the initial local OWASP database update requires a dependable cache/feed configuration. | **High / P1 dependency:** known vulnerable libraries can enter the beta path without a reliable blocking signal. | Select a proprietary-compatible Maven scanner, configure authenticated/cached advisory data, publish a machine-readable report, fail on unaccepted Critical/High findings and document the risk-acceptance process. | Platform CI and advisory-feed decision. | Yes | M |

## UMG-06 remediation evidence

- Runtime OpenAPI defines HttpOnly `browserSession`/`browserRefresh` cookie
  schemes; CSRF bootstrap/register/login remain unauthenticated but write
  routes still require CSRF.
- Ignored email selectors are removed and the raw authorization header is
  hidden from generated parameters. Ownership is described as server-derived.
- All documented failure statuses reference the versioned `GatewayResponse` /
  `ApiError` envelope and tests assert its stable schema fields.
- Unknown JSON properties fail before service calls instead of being silently
  discarded. Public/downstream DTO ownership and compatibility steps are
  documented and contract-tested with the UMG-05 runtime boundary.

## UMG-05 remediation evidence

- Browser responses never contain access/refresh tokens; HttpOnly, SameSite
  cookies are Secure `__Host-` cookies in the production profile and use an
  explicit separate localhost-only HTTP configuration.
- Every state-changing route requires a random double-submit CSRF value.
  Missing/mismatched values use a stable redacted `403` response.
- Protected profile identity comes only from the access cookie. Browser
  `Authorization` and `X-User-Id` headers have no authority; the end-user
  bearer is forwarded only server-to-server and profile-service validates it.
- CORS permits configured exact origins with credentials; wildcard/malformed
  origins and invalid cookie/rate bounds fail startup.
- Register/login/refresh have bounded direct-peer and global rate limits with
  stable `429`/`Retry-After`; forwarded addresses are not trusted.
- Refresh rotation is coalesced by a bounded, expiring digest-keyed in-memory
  result. Multi-instance deployment requires approved shared coordination.
- Security integration tests cover CSRF, cookie flags, token non-exposure,
  header forgery, invalid sessions, rotation/failure, logout, CORS and headers.
- An isolated local run against merged authentication-service and
  user-profile-service revisions exercised registration, profile ownership,
  refresh rotation, logout and post-logout rejection. UMG-07 still owns making
  that cross-service/browser evidence repeatable in the existing project E2E
  automation and CI.

## UMG-01 remediation evidence


- `pom.xml` has no `system` scope, `systemPath` or `includeSystemScope` setting.
- Authentication and profile consumer contracts are versioned under
  `src/main/openapi` with their reviewed source revisions.
- OpenAPI Generator 7.5.0 is pinned and emits both RestTemplate clients beneath
  `target/generated-sources`; generated sources and JARs remain ignored.
- `mvn -B clean verify` passes 17 tests from a clean checkout.
- The Docker build no longer copies a local `libs` directory. Container test,
  privilege, image pinning and health work remain scoped to UMG-09.

## UMG-03 remediation evidence

- Every generated authentication/profile client call has configurable,
  positive connect and read timeouts.
- Only idempotent `GET` requests use the bounded retry budget; unsafe writes
  are attempted once.
- Authentication and profile calls have independent circuit breakers and
  semaphore bulkheads. Open circuits reject calls without contacting the
  dependency.
- `/actuator/health/readiness` reports observed dependency circuit state
  without generating health-check traffic.
- Tests cover slow/unavailable responses, retry safety, open-circuit rejection,
  bulkhead saturation, partial registration failure, timeout wiring, invalid
  configuration and safe 503 responses.
- Downstream exception details, credentials, bearer tokens and response bodies
  are not logged. The Spring Web logger is pinned above debug to
  prevent generated authentication DTOs from rendering credentials.
- UMG-08 adds low-cardinality operation and authentication outcomes, latency
  histograms, redacted aggregate health, bounded dependency readiness details,
  correlation-ID validation and inbound-to-downstream propagation tests. The
  dashboard and initial alert rules are documented in `docs/OBSERVABILITY.md`;
  connecting an approved private exporter and testing alert delivery remain
  environment-owner work rather than an application repository change.

## UMG-04 remediation evidence

- Registration enforces the AUTH-02 15–128 Unicode-code-point password policy,
  trims identity fields and never changes password input. Login remains
  compatible with existing credentials while bounding credential size.
- Bean Validation recursively bounds profile collections, text, commute and
  geographic coordinates before any downstream call.
- JSON write bodies default to a 65,536-byte maximum, require
  `application/json`, and reject malformed input with stable status/code pairs.
  Invalid non-positive size configuration fails startup.
- The version 1 error schema exposes only safe messages, field names and
  constraint codes. Downstream bodies, rejected values, passwords and exception
  causes are neither returned nor written to application logs.
- Web and service tests cover Unicode boundaries, whitespace behaviour,
  malformed JSON, unsupported media, oversized bodies, safe downstream mapping
  and unexpected exceptions.

## UMG-09 remediation evidence

- Build and runtime base images are pinned by digest; their resolved toolchain
  is Maven 3.9.16 and Java 17.0.19.
- The container build runs `mvn -B clean verify` instead of skipping tests and
  does not copy binary clients or generated output from the host.
- The minimal runtime installs no additional package, runs as fixed UID/GID
  `10001:10001`, and declares a readiness health check.
- `scripts/verify-container.sh` verifies build success, image metadata, a
  read-only-root runtime, the runtime identity and transition to healthy.
- CI scans the built image with a full-SHA-pinned Trivy Action and fails for
  Critical or High operating-system/library vulnerabilities. UMG-11 still owns
  the broader Maven advisory-feed, report and risk-acceptance workflow.
- Enabling the gate exposed 4 Critical and 35 High findings in the previous
  Spring Boot 3.2.0/April runtime baseline. Spring Boot 3.5.16, compatible
  springdoc 2.8.17, the refreshed runtime digest and exact fixed Alpine
  packages reduce the blocking image result to zero without suppressions.
