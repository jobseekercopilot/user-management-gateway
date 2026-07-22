# OpenAPI authentication and ownership contract

## Public contract version

The current browser-facing contract is `2.0.0`. Version 2 is the approved
breaking migration from the 1.x browser bearer-token response/header contract
to gateway-owned HttpOnly access/refresh cookies, CSRF-protected writes and
subject-bound profile operations. A 1.x client must not be used against this
boundary or relabelled as version 2 without regeneration.

Consumers must pin both the semantic contract version and the exact source
revision that produced the reviewed OpenAPI document. A breaking public schema,
authentication or ownership change requires a new major version; compatible
additions and fixes require deliberate minor/patch updates with contract-test
evidence. Runtime implementation versions are independent of this public API
version.

CSRF bootstrap, registration and login do not require an existing session.
Profile read/update and logout declare the OpenAPI cookie scheme
`browserSession`; refresh declares `browserRefresh`. The gateway owns those
HttpOnly cookies and derives the current user through authentication-service;
profile operations have no email, user-ID, authorization-header or owner
parameter. State-changing operations also require the CSRF header described in
the API reference.

Every documented non-success response uses `GatewayResponse` containing the
versioned `ApiError` schema. Error codes/messages and field/code violations are
stable, while rejected values, passwords, bearer tokens, downstream bodies and
exception details are forbidden.

Public request DTOs reject unknown JSON properties. This prevents misspelled,
obsolete or ownership-like fields from being accepted and discarded. Schema
changes must update source, validation, OpenAPI assertions, `docs/API.md` and
affected consumers in one reviewed compatibility change. Generated downstream
models remain build output under `target/`; the gateway public DTOs are the
owned boundary and must not be replaced silently by a downstream model.

`OpenApiExportTest` asserts public/protected operation separation, cookie
scheme shape, token-field absence, no dead email/raw-header parameters,
required status codes and stable error-schema references. Controller tests
prove unknown input fails before any downstream service call.
