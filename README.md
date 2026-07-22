# User Management Gateway

Spring Boot facade for registration, login, current-profile retrieval and
profile update. It calls authentication-service and user-profile-service; it
does not own location lookup.

> Beta status: not beta-ready. UMG-01 makes the gateway build reproducibly and
> UMG-03 bounds downstream calls, UMG-04 validates requests and returns safe
> errors, UMG-05 establishes the browser session boundary, and UMG-09 hardens
> the runtime image, but the
> remaining beta-readiness findings are still open. See
> [the audit](docs/BETA_READINESS_AUDIT.md) and
> [workstream summary](docs/USER_MANAGEMENT_BETA_READINESS.md).

## Start here

Requirements: Java 17, Maven 3.9, and Docker when verifying the container.
The gateway can start alone, but registration, login and profile requests need
authentication-service on port `8084` and user-profile-service on port `8085`
unless their URLs are overridden.

```bash
mvn -B clean verify
mvn spring-boot:run
```

After startup, `curl --fail http://localhost:8083/actuator/health` checks the
process. See the [operations guide](docs/OPERATIONS.md) for configuration,
readiness semantics, container checks and troubleshooting. The
[observability contract](docs/OBSERVABILITY.md) documents safe metrics,
correlation propagation, dashboard panels and initial alert thresholds.

## API

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | None | Bootstrap the readable CSRF cookie and return its header/token pair |
| `POST` | `/api/auth/register` | CSRF | Create an account/profile and establish an HttpOnly cookie session |
| `POST` | `/api/auth/login` | CSRF | Authenticate and establish an HttpOnly cookie session |
| `POST` | `/api/auth/refresh` | Refresh cookie + CSRF | Rotate access and refresh cookies |
| `POST` | `/api/auth/logout` | Access cookie + CSRF | Revoke and clear the browser session |
| `GET` | `/api/auth/profile` | Access cookie | Read the current user's profile |
| `PUT` | `/api/auth/profile` | Access cookie + CSRF | Replace the current user's profile |

The complete payload fields, examples and response envelope are in the
[API reference](docs/API.md). Runtime OpenAPI is available at `/v3/api-docs`
and Swagger UI at `/swagger-ui/index.html` outside the production profile.
Profile operations use the `browserSession` cookie scheme and derive ownership
from the server-held access token; there is no email or user-ID selector.
Browser JavaScript never receives an access or refresh token and must send
credentialed requests plus the CSRF header returned by the bootstrap route.

## Configuration

| Variable | Local default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8083` | HTTP port |
| `AUTHENTICATION_SERVICE_URL` | `http://localhost:8084` | Authentication API |
| `AUTHENTICATION_SERVICE_TOKEN` | none; required | Minimum 32-byte service identity shared only with authentication-service |
| `USER_PROFILE_SERVICE_URL` | `http://localhost:8085` | Profile API |
| `GATEWAY_REQUEST_MAXIMUM_BODY_BYTES` | `65536` | Positive maximum request-body size for write routes |
| `GATEWAY_ACCESS_COOKIE_NAME` | `jsc-access-local` | Local HTTP access-cookie name; production fixes the `__Host-` name |
| `GATEWAY_REFRESH_COOKIE_NAME` | `jsc-refresh-local` | Local HTTP refresh-cookie name; production fixes the `__Host-` name |
| `GATEWAY_CSRF_COOKIE_NAME` | `jsc-csrf-local` | Local readable CSRF-cookie name; production fixes the `__Host-` name |
| `GATEWAY_SECURE_COOKIES` | `false` | Local HTTP only; production forces `true` |
| `GATEWAY_ALLOWED_ORIGINS` | `http://localhost:4200` | Exact credentialed browser origins; wildcards are rejected |
| `GATEWAY_ACCESS_MAXIMUM_SECONDS` | `900` | Maximum access-cookie lifetime |
| `GATEWAY_REFRESH_MAXIMUM_SECONDS` | `604800` | Refresh-cookie lifetime |
| `GATEWAY_REFRESH_CONCURRENCY_SECONDS` | `5` | Same-token refresh coalescing window |
| `GATEWAY_REFRESH_CONCURRENCY_MAXIMUM` | `1000` | Maximum bounded refresh entries |
| `GATEWAY_AUTH_RATE_MAXIMUM` | `20` | Authentication requests per direct peer/window |
| `GATEWAY_AUTH_RATE_GLOBAL_MAXIMUM` | `1000` | Authentication requests globally/window |
| `GATEWAY_AUTH_RATE_WINDOW_SECONDS` | `60` | Authentication rate-limit window |
| `GATEWAY_AUTH_RATE_MAXIMUM_CLIENTS` | `10000` | Maximum bounded direct-peer counters |
| `DOWNSTREAM_CONNECT_TIMEOUT_MS` | `500` | Connection deadline for each downstream call |
| `DOWNSTREAM_READ_TIMEOUT_MS` | `2000` | Response-read deadline for each downstream call |
| `DOWNSTREAM_RETRY_MAX_ATTEMPTS` | `2` | Maximum attempts for idempotent `GET` calls; `POST` is always attempted once |
| `DOWNSTREAM_CIRCUIT_FAILURE_THRESHOLD` | `3` | Consecutive transport/5xx failures before opening a dependency circuit |
| `DOWNSTREAM_CIRCUIT_OPEN_DURATION_MS` | `30000` | Fail-fast period for an open circuit |
| `DOWNSTREAM_BULKHEAD_MAX_CONCURRENT` | `32` | Maximum concurrent calls to each dependency |
| `APP_LOG_LEVEL` | `INFO` | Application log level |

All resilience values and the request-body limit must be positive or startup
fails. `AUTHENTICATION_SERVICE_TOKEN` must contain at least 32 bytes; the
gateway injects it only into its server-side authentication-service client and
replaces any same-named inbound value. Supply the identical value to
authentication-service through the runtime secret manager. This service has no
JWT signing key or database credential of its own. Session cookies, bearer tokens and downstream
secrets must never be committed or placed in command-line arguments, URLs or
logs.

## Downstream failure behaviour

Every generated-client operation has a bounded connection and response-read
deadline. Only idempotent `GET` calls receive the configured retry budget;
registration, login and profile writes are never retried automatically. Each
dependency has its own circuit breaker and concurrency bulkhead. Transport and
downstream 5xx failures return a stable `503 Service Unavailable` response
without exposing internal exception details. Correlation IDs continue across
all attempts, while request credentials, bearer tokens and response bodies are
not logged. The Spring Web logger remains at `INFO` even if Spring debug mode
is enabled because its debug representation includes request DTOs.

JSON write requests are limited to 64 KiB by default and validated before a
downstream call. Failure responses include a stable, versioned `error` object;
field violations identify only the field and constraint code and never echo
the rejected value, password, downstream body or exception cause.

## Verification

```bash
mvn -B clean verify
./scripts/test-dependency-report-policy.sh
./scripts/verify-container.sh
```

`mvn verify` generates the authentication and profile clients from the reviewed
contracts under `src/main/openapi`; no sibling checkout or `libs/*.jar` is
required. Generated sources stay under `target/` and must not be committed.
See [the contract update procedure](src/main/openapi/README.md) when either
downstream API changes.

The first command runs the unit, web, generated-contract and documentation
contract tests and exports `target/openapi.json`. The second proves the
dependency-report policy rejects malformed reports, Critical/High findings and
invalid risk exceptions. The third builds the image,
starts it with a read-only root filesystem, and verifies its configured user,
runtime identity, health check and healthy state. It requires a running Docker
daemon. See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch workflow.

## Container security

The multi-stage image pins its Maven 3.9.16/Java 17.0.19 build and runtime bases
by digest. Fixed Alpine security updates that post-date the runtime digest are
also installed at exact versions. The build stage runs the complete
`mvn -B clean verify`; the runtime contains only the application JAR and
base-runtime tools, runs as fixed UID/GID `10001:10001`, and declares a
readiness health check that follows `SERVER_PORT`. The verification script also
starts the image with a read-only root filesystem and confirms the
configured/runtime user and healthy state.

CI scans the verified runtime dependency set and rebuilt image with Trivy. The
dependency scan uploads a complete machine-readable JSON report retained for 30 days;
Critical or High Java, operating-system or other library findings fail the
relevant job. Trivy caches its vulnerability and Java databases through the
Action and refreshes them from Aqua's public OCI database mirrors when needed;
no advisory credential is required. Scanner and artifact Actions are pinned to
full commit SHAs. See the [dependency security policy](docs/DEPENDENCY_SECURITY.md)
for local reproduction and the time-bounded risk-acceptance process.

Update Actions and both base-image digests only in a reviewed dependency pull
request, then rerun the clean build, policy tests and container test. UMG-09
does not claim the runtime is generally hardened for production deployment;
deployment resource limits and platform policy remain environment ownership.

Spring Boot 3.5.16 and springdoc 2.8.17 are deliberately paired as supported,
compatible runtime dependencies. The upgrade removed the Critical/High Java
findings exposed when the image gate was first enabled.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).
