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
| [UMG-04](https://github.com/jobseekercopilot/user-management-gateway/issues/4) | Enforce contract validation and safe errors | Controller bodies lack `@Valid`; checks are `contains("@")` and four-character passwords; generic catches return `ex.getMessage()`. | **High / P1 security/API:** malformed input and internal details reach clients with inconsistent schemas. | Add size/content-type limits and Bean Validation; normalise email consistently; map errors to a versioned schema without causes; test malformed JSON and boundaries. | AUTH-02. | Yes | M |
| [UMG-05](https://github.com/jobseekercopilot/user-management-gateway/issues/5) | Establish the public gateway security baseline | No security filter chain, rate limiting, explicit CORS policy or security headers exist; auth is manual per profile method. | **High / P1 security:** brute force and future accidental endpoint exposure are unbounded. | Deny by default, explicitly permit register/login/health, authenticate protected routes, define CORS and headers, rate-limit auth paths and add security tests. | Agreed browser/session design. | Yes | L |
| [UMG-06](https://github.com/jobseekercopilot/user-management-gateway/issues/6) | Make OpenAPI express real authentication and ownership | Authorization is an optional header parameter and ignored `email` query parameters remain; DTOs duplicate downstream models and unknown fields are discarded. | **Medium / P2 API:** generated consumers encode misleading contracts and schema drift is hidden. | Define bearer security schemes and stable error schemas, remove dead parameters, validate compatibility and add contract tests. | UMG-01. | No | M |
| [UMG-07](https://github.com/jobseekercopilot/user-management-gateway/issues/7) | Add real integration and negative security tests | Existing service tests mock generated APIs; there is no multi-service registration/profile test, cross-user test, timeout test or browser path. | **High / P1 testing:** orchestration and ownership guarantees are unproven. | Add container/fixture integration plus contract tests covering success, duplicates, invalid/expired token, cross-user access and partial failures. | AUTH/PROFILE test fixtures. | Yes | L |
| [UMG-08](https://github.com/jobseekercopilot/user-management-gateway/issues/8) | Add service-level telemetry and readiness | Correlation IDs and basic health exist, but no dependency readiness, auth outcome metrics, latency histograms, alerts or trace propagation proof exists. | **Medium / P1 observability:** beta failures cannot be diagnosed or alerted reliably. | Add redacted metrics and dependency health, document dashboards/alerts and test correlation propagation end-to-end. | Monitoring stack decision. | Yes | M |
| [UMG-09](https://github.com/jobseekercopilot/user-management-gateway/issues/9) | Harden the container and stop skipping tests | Docker runs `mvn ... -DskipTests`, uses mutable/root images and has no health check; before UMG-01 it also copied `libs`. | **Medium / P1 devops:** the image still bypasses verification and lacks a hardened runtime baseline. | Run verify in CI/build, pin images, use non-root runtime, health check and image scan. | UMG-01. | Yes | M |
| [UMG-10](https://github.com/jobseekercopilot/user-management-gateway/issues/10) | Correct operational and API documentation | README examples use obsolete profile fields/response keys and overstate WireMock/comprehensive coverage. | **Medium / P1 documentation:** operators and client developers receive inaccurate instructions. | Document actual endpoints, contracts, environment, secrets, health/readiness, troubleshooting and branch workflow; validate every command from a clean clone. | UMG-01. | Yes | S |
| [UMG-11](https://github.com/jobseekercopilot/user-management-gateway/issues/11) | Establish reliable dependency vulnerability scanning | CI emits `mvn dependency:tree` but performs no vulnerability analysis; the initial local OWASP database update requires a dependable cache/feed configuration. | **High / P1 dependency:** known vulnerable libraries can enter the beta path without a reliable blocking signal. | Select a proprietary-compatible Maven scanner, configure authenticated/cached advisory data, publish a machine-readable report, fail on unaccepted Critical/High findings and document the risk-acceptance process. | Platform CI and advisory-feed decision. | Yes | M |

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
  prevent generated authentication DTOs from rendering credentials. UMG-08
  still owns metrics, dashboards, alerts and end-to-end trace propagation.
