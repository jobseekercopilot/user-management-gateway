# User Management Gateway API

This reference describes public contract `2.1.0`. It replaces the incompatible
1.x browser bearer-token contract; see the
[OpenAPI contract policy](OPENAPI_CONTRACT.md) for migration and pinning rules.

The gateway listens on `http://localhost:8083` by default. JSON responses use
this envelope:

```json
{
  "statusCode": 200,
  "success": true,
  "message": "Human-readable outcome",
  "user": {
    "id": "user-id",
    "name": "Example User",
    "email": "user@example.invalid",
    "profile": {}
  }
}
```

`user` is omitted on failures. Access and refresh tokens are never present in
JSON; the gateway stores them in separate HttpOnly browser cookies. Callers
must use the HTTP status, not only the duplicated
`statusCode` field. Downstream client errors retain their HTTP status; an
unavailable or 5xx downstream returns `503`. Failures also include a stable,
versioned error object, for example:

```json
{
  "statusCode": 400,
  "success": false,
  "message": "Request validation failed.",
  "error": {
    "schemaVersion": "1",
    "code": "REQUEST_VALIDATION_FAILED",
    "message": "Request validation failed.",
    "violations": [
      { "field": "password", "code": "UNICODELENGTH" }
    ]
  }
}
```

Errors never include rejected values, passwords, downstream response bodies or
exception causes. Malformed JSON, unsupported media types and oversized bodies
use `MALFORMED_JSON`, `UNSUPPORTED_MEDIA_TYPE` and `PAYLOAD_TOO_LARGE`.

## Browser session and CSRF

Begin with `GET /api/auth/csrf`. It sets a readable `SameSite=Lax` CSRF cookie
and returns the random value plus the response's canonical header name. Echo
that value in the named header on every `POST`, `PUT` or `PATCH` request and
send requests with browser credentials enabled. Login and registration set
separate HttpOnly access and rotating refresh cookies. Browser code must not
send an `Authorization` or user-ID header; those headers have no public
authority. Missing/mismatched CSRF returns stable `403`; a missing/invalid
session returns stable `401`.

## Register

`POST /api/auth/register` creates the authentication account, logs in, and
creates a blank profile synchronously. Only the approved account fields are
needed:

```json
{
  "name": "Example User",
  "email": "user@example.invalid",
  "password": "<password>"
}
```

The optional legacy `profile` property remains accepted during the compatibility
window, but new browser registration does not send it. Omitted profiles start
with empty skills, qualifications and roles and no declared preferences.
Success is `201`. Names are trimmed and contain 1–100
Unicode code points. Emails are trimmed, syntactically valid and at most 254
code points. New passwords contain 15–128 Unicode code points and are forwarded
exactly as supplied; whitespace is never trimmed or otherwise changed.
Success also establishes the cookie session without returning either token.
Registration is not yet atomic: a later profile failure can leave the account
created, as tracked in UMG-02.

## Login

`POST /api/auth/login`:

```json
{
  "email": "user@example.invalid",
  "password": "<password>"
}
```

Success is `200`, establishes the cookie session and includes the user/profile
without tokens. A missing profile is
represented with empty skills, qualifications and roles; login does not create
it. Login preserves passwords exactly and accepts legacy account passwords up
to 128 Unicode code points; authentication-service remains responsible for
credential verification.

## Password reset

`POST /api/auth/password-reset/request` is a public CSRF-protected route:

```json
{
  "email": "user@example.invalid"
}
```

A valid request returns `202` and the same message for known and unknown
accounts. The gateway canonicalises and validates the bounded email before the
authentication service applies the account cooldown. The direct-peer rate gate
also covers this route. Neither response bodies nor logs contain account
existence, delivery state, or reset material.

`POST /api/auth/password-reset/complete` is also public and CSRF protected:

```json
{
  "token": "<single-use URL-safe reset token>",
  "newPassword": "<new password>"
}
```

The gateway bounds both fields, forwards them only through the generated
authentication client, and returns one stable rejection for invalid, expired,
modified or consumed reset links. Success is `200` and clears every browser
session cookie in this response; authentication-service atomically changes the
password and revokes every server-side session. The gateway never logs or
returns the token.

## Read the current profile

`GET /api/auth/profile` requires the access cookie. The gateway asks
authentication-service to validate the server-held token and derive the user
ID, then reads that ID's profile. If no profile exists it creates an empty one.
Success is `200`.

The route has no email or user-ID selector. Its OpenAPI operation declares the
cookie `browserSession` scheme; neither an authorization header nor a cookie
value is exposed as a normal JavaScript-managed parameter.

## Update the current profile

`PUT /api/auth/profile` requires the access cookie, CSRF header and a profile body:

```json
{
  "skills": ["Customer service", "Spreadsheets"],
  "qualifications": [
    {
      "qualificationName": "Example certificate",
      "issuingBody": "Example body",
      "status": "COMPLETED",
      "grade": "PASS",
      "dateAchieved": "2026-01-15",
      "expectedCompletion": null
    }
  ],
  "roles": [
    {
      "jobTitle": "Adviser",
      "employer": "Example employer",
      "status": "CURRENT",
      "startDate": "2025-01-01",
      "endDate": null,
      "keyResponsibilities": "Supporting customers"
    }
  ],
  "aspirations": {
    "targetRoles": ["Support analyst"],
    "targetWeeklyHours": "FLEXIBLE"
  },
  "workPreferences": {
    "location": {
      "postcode": "AA1 1AA",
      "region": "Example region",
      "adminDistrict": "Example district",
      "latitude": 51.5,
      "longitude": -0.1
    },
    "commuteRange": 20
  }
}
```

`targetWeeklyHours` accepts `FULL_TIME`, `PART_TIME_16_30`,
`PART_TIME_UNDER_16`, or `FLEXIBLE`. Success is `200`. Profile collections and
text fields have bounded sizes; commute range is 0–500, latitude is -90–90 and
longitude is -180–180. Domain-specific status and date consistency remains
owned by the profile service.

## Progressive profile preferences

`PATCH /api/auth/profile` accepts only skills, aspirations and work preferences.
It preserves roles and qualifications and accepts the current profile revision
in `If-Match`. Employment types, working patterns and workplace arrangements
use the producer-defined enums. Availability is explicitly claimant-selected:
`availableFrom` and `noticePeriodDays` are optional and mutually exclusive.

## Evidence Library

The browser-facing Evidence Library routes are:

- `GET /api/auth/evidence`
- `GET /api/auth/evidence/{entryId}`
- `POST /api/auth/evidence`
- `PUT /api/auth/evidence/{entryId}`
- `POST /api/auth/evidence/{entryId}/confirm`
- `POST /api/auth/evidence/{entryId}/hide`
- `POST /api/auth/evidence/{entryId}/show`
- `POST /api/auth/evidence/{entryId}/archive`
- `POST /api/auth/evidence/{entryId}/restore`
- `POST /api/auth/evidence/{entryId}/supersede`

Writes forward `If-Match`; successful reads and mutations return the producer
`ETag`. Entry, revision, fact and lifecycle schemas are generated from the
pinned User Profile 1.2 contract. Browser callers cannot select an owner.

All JSON `POST`, `PUT` and `PATCH` bodies are limited to 65,536 bytes by
default. Operators can set the positive `GATEWAY_REQUEST_MAXIMUM_BODY_BYTES`
runtime value. A missing/unsupported JSON content type is rejected before any
downstream call.

## Refresh and logout

`POST /api/auth/refresh` requires the refresh cookie and CSRF header. Success
rotates both HttpOnly cookies. Concurrent requests carrying the same old token
are coalesced inside one gateway instance; an expired/replayed session clears
the cookies and returns `401`.

`POST /api/auth/logout` requires the access cookie and CSRF header. The gateway
asks authentication-service to revoke the session and always expires the local
access/refresh cookies. A dependency failure is reported accurately rather
than claiming remote revocation.

## Machine-readable contracts

- Gateway OpenAPI: `GET /v3/api-docs` while the application is running, or
  `target/openapi.json` after `mvn -B clean verify`.
- Reviewed downstream inputs: `src/main/openapi/authentication-service.yaml`
  and `src/main/openapi/user-profile-service.yaml`.
- Downstream contract update process: `src/main/openapi/README.md`.

Examples deliberately use reserved/example values. Never paste a real
password, cookie or complete JWT into documentation, issues, shell
history, URLs or logs.

Unknown JSON request properties are rejected with the stable version 1 error
envelope rather than silently discarded. Additive response fields require an
explicit reviewed public-contract change. See
[the OpenAPI ownership contract](OPENAPI_CONTRACT.md).
