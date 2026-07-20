# User Management Gateway

Spring Boot facade for registration, login, current-profile retrieval and
profile update. It calls authentication-service and user-profile-service; it
does not own location lookup.

> Beta status: not beta-ready. UMG-01 makes the gateway build reproducibly,
> but the remaining beta-readiness findings are still open. See
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
| `APP_LOG_LEVEL` | `INFO` | Application log level |

No secret belongs in source or a command-line argument.

## API and health

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/profile` with bearer token
- `PUT /api/auth/profile` with bearer token
- `/v3/api-docs`, `/swagger-ui/index.html`, `/actuator/health`

## Build, test and run

```bash
mvn -B verify
mvn spring-boot:run
docker build -t user-management-gateway .
```

`mvn verify` generates the authentication and profile clients from the reviewed
contracts under `src/main/openapi`; no sibling checkout or `libs/*.jar` is
required. Generated sources stay under `target/` and must not be committed.
See [the contract update procedure](src/main/openapi/README.md) when either
downstream API changes.

## Branch workflow and troubleshooting

Use `feature/* → develop`; `main` will be added later as a release branch. For
generated-client failures, validate the versioned OpenAPI inputs and rerun
`mvn -B clean verify`. For runtime 5xx responses, use the correlation ID and
check authentication/profile health; do not log request credentials or tokens.

## Licence

Copyright © 2026 Bernard McGeever. All rights reserved.

This repository contains proprietary software belonging to Bernard McGeever.
It may not be used, copied, modified or distributed without express written
permission. See [LICENSE](./LICENSE).
