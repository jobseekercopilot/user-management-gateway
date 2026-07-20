# User Management Gateway

Spring Boot facade for registration, login, current-profile retrieval and
profile update. It calls authentication-service and user-profile-service; it
does not own location lookup.

> Beta status: not beta-ready. UMG-01 makes the gateway build reproducibly and
> UMG-03 bounds downstream calls and UMG-09 hardens the runtime image, but the
> remaining beta-readiness findings are still open. See
> [the audit](docs/BETA_READINESS_AUDIT.md) and
> [workstream summary](docs/USER_MANAGEMENT_BETA_READINESS.md).

## Requirements and configuration

- Java 17 and Maven 3.9
- authentication-service and user-profile-service

| Variable | Local default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8083` | HTTP port |
| `AUTHENTICATION_SERVICE_URL` | `http://localhost:8084` | Authentication API |
| `USER_PROFILE_SERVICE_URL` | `http://localhost:8085` | Profile API |
| `DOWNSTREAM_CONNECT_TIMEOUT_MS` | `500` | Connection deadline for each downstream call |
| `DOWNSTREAM_READ_TIMEOUT_MS` | `2000` | Response-read deadline for each downstream call |
| `DOWNSTREAM_RETRY_MAX_ATTEMPTS` | `2` | Maximum attempts for idempotent `GET` calls; `POST` is always attempted once |
| `DOWNSTREAM_CIRCUIT_FAILURE_THRESHOLD` | `3` | Consecutive transport/5xx failures before opening a dependency circuit |
| `DOWNSTREAM_CIRCUIT_OPEN_DURATION_MS` | `30000` | Fail-fast period for an open circuit |
| `DOWNSTREAM_BULKHEAD_MAX_CONCURRENT` | `32` | Maximum concurrent calls to each dependency |
| `APP_LOG_LEVEL` | `INFO` | Application log level |

All resilience values must be positive or startup fails. No secret belongs in
source or a command-line argument.

## API and health

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/profile` with bearer token
- `PUT /api/auth/profile` with bearer token
- `/v3/api-docs`, `/swagger-ui/index.html`, `/actuator/health`
- `/actuator/health/readiness` includes the observed authentication and profile
  circuit states; it does not probe or create downstream traffic

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

## Build, test and run

```bash
mvn -B verify
mvn spring-boot:run
docker build -t user-management-gateway .
./scripts/verify-container.sh
```

`mvn verify` generates the authentication and profile clients from the reviewed
contracts under `src/main/openapi`; no sibling checkout or `libs/*.jar` is
required. Generated sources stay under `target/` and must not be committed.
See [the contract update procedure](src/main/openapi/README.md) when either
downstream API changes.

## Container security and operation

The multi-stage image pins its Maven 3.9.16/Java 17.0.19 build and runtime bases
by digest. Fixed Alpine security updates that post-date the runtime digest are
also installed at exact versions. The build stage runs the complete
`mvn -B clean verify`; the runtime contains only the application JAR and
base-runtime tools, runs as fixed UID/GID `10001:10001`, and declares a
readiness health check that follows `SERVER_PORT`. The verification script also
starts the image with a read-only root filesystem and confirms the
configured/runtime user and healthy state.

CI rebuilds and verifies the image, then scans operating-system and Java
packages with Trivy. Critical or High findings fail the job. The Trivy Action is
pinned to a full commit SHA; update it and both base-image digests only in a
reviewed dependency pull request, rerun the clean build/container test and
record any accepted finding in the relevant dependency-security issue. UMG-09
does not claim the runtime is generally hardened for production deployment;
deployment resource limits and platform policy remain environment ownership.

Spring Boot 3.5.16 and springdoc 2.8.17 are deliberately paired as supported,
compatible runtime dependencies. The upgrade removed the Critical/High Java
findings exposed when the image gate was first enabled.

## Branch workflow and troubleshooting

Use `feature/* → develop`; `main` will be added later as a release branch. For
generated-client failures, validate the versioned OpenAPI inputs and rerun
`mvn -B clean verify`. For runtime 503 responses, use the correlation ID and
check `/actuator/health/readiness` plus authentication/profile health. An
`OPEN` dependency remains fail-fast for the configured open duration. Do not
log request credentials or tokens.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).
