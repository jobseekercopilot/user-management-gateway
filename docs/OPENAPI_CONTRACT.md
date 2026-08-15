# OpenAPI authentication and ownership contract

## Public contract version

The current browser-facing contract is `4.0.0`. Version 4 preserves the
gateway-owned HttpOnly access/refresh cookies, CSRF-protected writes and
subject-bound profile operations from earlier versions. It requires explicit
terms, privacy-notice and minimum-age acknowledgements against the exact
server-published legal version during registration. Version 4 also retains the
version 3 Evidence Library limits and owner-scoped professional-contact route.
Earlier clients must regenerate and present the reviewed acknowledgements;
they must not silently invent or default legal consent.

Consumers must pin both the semantic contract version and the exact source
revision that produced the reviewed OpenAPI document. A breaking public schema,
authentication or ownership change requires a new major version; compatible
additions and fixes require deliberate minor/patch updates with contract-test
evidence. Runtime implementation versions are independent of this public API
version.

CSRF bootstrap, registration requirements, registration and login do not
require an existing session. `GET /api/auth/registration-requirements` returns
the reviewed legal version, exact HTTPS Terms and Privacy URLs and minimum age;
the response is non-cacheable. The gateway retrieves this contract from the
service-authenticated Authentication API and rejects malformed or unavailable
requirements rather than publishing placeholders.
Profile read/update and logout declare the OpenAPI cookie scheme
`browserSession`; refresh declares `browserRefresh`. The gateway owns those
HttpOnly cookies and derives the current user through authentication-service;
profile operations have no email, user-ID, authorization-header or owner
parameter. State-changing operations also require the CSRF header described in
the API reference.

`PATCH /api/auth/profile/professional-contact` accepts only user-declared phone
and labelled HTTPS professional links, forwards `If-Match`, and returns the
resulting profile revision as `ETag`. Its public DTO is gateway-owned and
validated before mapping to the exact generated User Profile 2.3 producer
model. Neither the request nor the generated downstream operation has an owner
selector. `UserProfile.professionalContact` is read-only at the public gateway
boundary, and the legacy full-profile mapper omits it so that route cannot
bypass optimistic concurrency.

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
The export assertions also pin the already-enforced registration/login name,
email and password bounds and the producer-aligned Evidence Library write
bounds. They also pin the professional-contact operation, HTTPS-link bounds,
cookie ownership and absence of owner selectors. Registration and login string lengths use Unicode code points;
password descriptions state that the supplied value is forwarded exactly so
consumers cannot silently trim or normalise a credential.
