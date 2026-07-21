# Local operations guide

This repository currently describes local development and CI only. There is no
production deployment configuration here.

## Clean-clone verification

From the repository root, with Java 17 and Maven 3.9:

```bash
mvn -B clean verify
```

This command generates both downstream clients from the tracked contracts,
compiles the gateway, runs all tests (including the public-route/documentation
contract), and writes `target/openapi.json`. No sibling repository or local JAR
is required.

With a running Docker daemon, verify the release-shaped container:

```bash
./scripts/verify-container.sh
```

The script builds the image using the same clean verification, confirms the
fixed `10001:10001` user and declared health check, then runs it with a
read-only root filesystem until healthy. It removes its temporary container on
exit.

## Run locally

Start authentication-service and user-profile-service according to their own
local documentation. Put the same private, randomly generated service identity
in both processes through their runtime secret source, then start the gateway:

```bash
mvn spring-boot:run
```

Confirm liveness and readiness:

```bash
curl --fail http://localhost:8083/actuator/health
curl --fail http://localhost:8083/actuator/health/readiness
```

The general health endpoint confirms the gateway process. Readiness includes
the last observed authentication and profile circuit states. It intentionally
does not call either downstream, so `UP` before the first request means no open
circuit has been observed, not that both services were actively probed. An
`OPEN` circuit reports readiness `DOWN` and rejects calls until its configured
open duration passes.

The general endpoint intentionally hides component details. Readiness exposes
only bounded dependency circuit states and no downstream URL, credential or
exception. The [observability contract](OBSERVABILITY.md) defines the custom
meters, privacy rules, dashboard panels and initial alert thresholds.

## Runtime configuration

| Environment variable | Default | Constraint/owner |
|---|---:|---|
| `SERVER_PORT` | `8083` | Gateway listener |
| `AUTHENTICATION_SERVICE_URL` | `http://localhost:8084` | Base URL; authentication-service owner |
| `AUTHENTICATION_SERVICE_TOKEN` | none | Required minimum 32-byte service identity; shared only with authentication-service through the runtime secret manager |
| `USER_PROFILE_SERVICE_URL` | `http://localhost:8085` | Base URL; user-profile-service owner |
| `GATEWAY_REQUEST_MAXIMUM_BODY_BYTES` | `65536` | Positive maximum bytes accepted on `POST`, `PUT` and `PATCH` bodies |
| `DOWNSTREAM_CONNECT_TIMEOUT_MS` | `500` | Positive milliseconds |
| `DOWNSTREAM_READ_TIMEOUT_MS` | `2000` | Positive milliseconds |
| `DOWNSTREAM_RETRY_MAX_ATTEMPTS` | `2` | Positive total attempts for idempotent GET calls |
| `DOWNSTREAM_CIRCUIT_FAILURE_THRESHOLD` | `3` | Positive consecutive transport/5xx failures |
| `DOWNSTREAM_CIRCUIT_OPEN_DURATION_MS` | `30000` | Positive milliseconds |
| `DOWNSTREAM_BULKHEAD_MAX_CONCURRENT` | `32` | Positive calls per dependency |
| `APP_LOG_LEVEL` | `INFO` | Gateway package log level |

Invalid non-positive resilience or request-size values and a missing/short
authentication-service identity fail application startup. The gateway replaces
any browser-supplied `X-Service-Token` with its configured value only on the
dedicated authentication-service client; never expose that header in a public
API contract or proxy it generically. Rotate the value in the gateway first and
authentication-service second using an approved overlap procedure if continuous
availability is required; the current single-token contract otherwise requires
a coordinated restart.

Registration, login and profile writes are never automatically
retried; only idempotent profile reads use the configured attempt budget.
Authentication and profile have independent circuit and concurrency state.

The gateway owns no signing key. `JWT_SIGNING_KEY` belongs in the ignored local
environment configuration of authentication-service and must not be passed to
or stored by this service. The authentication service identity is distinct from
JWT signing material and browser bearer tokens. Keep passwords, real service
identity values, signing keys and complete JWTs out of logs, command-line
arguments, URLs, source, fixtures and issue comments. Clearly labelled
synthetic test values are not deployment credentials.

## Failure diagnosis

- `503` with "A required service is temporarily unavailable": check the
  readiness details and the matching downstream process/URL. Calls fail fast
  while that dependency circuit is open.
- `413` with `PAYLOAD_TOO_LARGE`: reduce the JSON request below
  `GATEWAY_REQUEST_MAXIMUM_BODY_BYTES`, or deliberately raise the positive
  limit after reviewing the memory and abuse impact.
- `400` with `REQUEST_VALIDATION_FAILED`: use the machine-readable field and
  constraint codes. Rejected values are intentionally not echoed.
- Connection failure at startup is not expected: downstream connections are
  lazy. Confirm URLs and services before exercising an API request.
- Generated-client compilation failure: validate the two tracked YAML files,
  follow `src/main/openapi/README.md`, then rerun `mvn -B clean verify`.
- Container never becomes healthy: inspect its startup output for invalid
  numeric configuration and check whether `SERVER_PORT` was overridden.
- Unexpected request result: use the response/request correlation ID to find
  metadata-only logs. Do not add request bodies, credentials, bearer headers,
  response bodies or complete tokens to diagnostic output.
- Missing metrics or alerts: confirm the approved private meter exporter and
  alert routing are configured by the environment owner. `/actuator/metrics`
  is intentionally not exposed by this application.

## Branch and release workflow

Create `feature/*` branches from `develop`, verify from a clean checkout, and
merge through a reviewed pull request. Do not push feature work directly to
`develop`. A `main` release branch and production operations are not yet
defined. See `CONTRIBUTING.md` and `SECURITY.md` for contribution and incident
handling rules.

## Residual ownership

- UMG-02: registration atomicity/idempotency.
- UMG-05: public gateway security policy and rate controls.
- UMG-06: OpenAPI authentication/ownership semantics and legacy query removal.
- UMG-07: broader cross-service and browser integration coverage.
- Platform/deployment owners: private metrics export, deployed dashboards,
  alert routing and trace backend selection as described in the observability
  contract.
- Platform/deployment owners: production secrets, resource limits, network
  policy, monitoring platform and release process.
