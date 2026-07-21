# OpenAPI authentication and ownership contract

`POST /api/auth/register` and `POST /api/auth/login` are public operations.
`GET /api/auth/profile` and `PUT /api/auth/profile` declare the OpenAPI HTTP
bearer scheme `bearerAuth` with JWT format. The gateway derives the current
user through authentication-service; profile operations have no email, user-ID
or owner parameter. The raw authorization header is hidden from generated
method parameters because the security scheme owns credential transport.

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

`OpenApiExportTest` asserts public/protected operation separation, bearer
scheme shape, absence of dead email/raw-header parameters, required status
codes and stable error-schema references. Controller tests prove unknown input
fails before any downstream service call.
