# User Management Gateway API

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
creates the profile synchronously.

```json
{
  "name": "Example User",
  "email": "user@example.invalid",
  "password": "<password>",
  "profile": {
    "skills": ["Customer service"],
    "qualifications": [],
    "roles": [],
    "aspirations": {
      "targetRoles": ["Support analyst"],
      "targetWeeklyHours": "FULL_TIME"
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
}
```

`profile` is optional; omitted profiles start with empty skills,
qualifications and roles. Success is `201`. Names are trimmed and contain 1–100
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
