# Observability contract

This gateway provides vendor-neutral telemetry for user-management operations.
The repository does not select, purchase or configure a production monitoring
platform. Platform owners must connect the Spring `MeterRegistry` to an
approved private exporter and implement the dashboard and alert specification
below before a beta environment is declared operationally ready.

## Metrics and privacy

Spring records standard server metrics such as `http.server.requests`. The
gateway additionally records these business-level meters:

| Meter | Type | Meaning |
|---|---|---|
| `jobseeker.user.management.operation.outcomes` | Counter | Completed register, login, profile-read and profile-update outcomes |
| `jobseeker.user.management.operation.duration` | Timer/histogram | The same operation latency, with 100 ms, 500 ms and 2 s service-level boundaries |
| `jobseeker.user.management.authentication.outcomes` | Counter | Registration and login outcomes only |

The complete custom tag allowlist is `operation`, `outcome` and HTTP status
family. Values are fixed, low-cardinality categories. Email addresses, user
IDs, names, postcodes, tokens, correlation IDs, URLs, query strings, response
messages and exception text must never be metric labels. The unit tests enforce
the allowlist and verify that response content cannot become a tag.

`/actuator/metrics` is deliberately not exposed over HTTP. Export metrics only
over an authenticated private management path after the public security
baseline and monitoring platform are approved. Exposing the endpoint publicly
to make a dashboard work is not an acceptable workaround.

## Health and readiness

`/actuator/health` returns only the aggregate process status. Component details
are hidden. `/actuator/health/readiness` returns bounded component status and
the observed circuit state for `authentication-service` and
`user-profile-service`; it contains no URL, credential, account or exception
detail.

Readiness is passive. It does not generate downstream probe traffic. Before a
dependency has been called, `AVAILABLE` means that no open circuit has been
observed. `OPEN` makes readiness `DOWN`; `HALF_OPEN` shows recovery evaluation.
This is a dependency-safety signal, not proof that a never-called service is
reachable.

## Correlation IDs

The gateway accepts `X-Correlation-Id` only when it matches
`[A-Za-z0-9][A-Za-z0-9._:-]{0,127}` after trimming. Missing or unsafe values are
replaced with a UUID before logging, returning the response header or making a
downstream call. The same safe value is propagated through both generated
clients and removed from logging context at request completion. Tests exercise
the inbound-to-response-to-downstream path and rejection of unsafe values.

Correlation IDs are diagnostic metadata, not authentication or authorization
credentials. Do not use them as metric tags and do not assume they are unique
or secret.

## Dashboard specification

Create one dashboard per environment with:

1. request volume and success/error ratio by operation;
2. authentication outcomes, including rate-limited and dependency-unavailable
   rates, without identity dimensions;
3. p50, p95 and p99 operation latency plus the 100 ms, 500 ms and 2 s buckets;
4. gateway process health and readiness state;
5. authentication and profile dependency circuit state;
6. JVM heap, process CPU, thread and HTTP connection saturation; and
7. release/version annotation and a link to the gateway runbook.

Do not add account, bearer token, request body, query-string or raw exception
panels.

## Alert specification

Initial beta thresholds are deliberately conservative and must be tuned from
measured traffic:

| Signal | Initial trigger | Response |
|---|---|---|
| Readiness | `DOWN` continuously for 2 minutes | Page the user-management service owner |
| Operation availability | 5xx/dependency-unavailable outcomes exceed 5% for 5 minutes with at least 20 operations | Page; identify the affected dependency from readiness/circuit state |
| Authentication throttling | Rate-limited outcomes exceed 10% for 10 minutes with at least 50 attempts | Notify security/operations; investigate abuse and client retry behaviour |
| Latency | p95 exceeds 2 seconds for 10 minutes with at least 20 operations | Notify service owner; inspect downstream and saturation panels |
| Telemetry silence | No gateway samples for 5 minutes while the environment should serve traffic | Notify operations; verify process and exporter health |

Alerts must route through the approved on-call system, include environment and
runbook links, and avoid request or user data. Platform owners must test alert
delivery and recovery notifications in the target environment. This repository
specifies the rules but does not claim that production alerting is deployed.

## Operator triage

Start with aggregate health and readiness, then compare operation outcomes and
latency. Use the response correlation ID to locate metadata-only logs across
the gateway and its downstream services. If a dependency circuit is open,
follow the downstream service runbook and do not increase retry budgets during
an incident. See [the local operations guide](OPERATIONS.md) for commands and
known readiness limitations.
