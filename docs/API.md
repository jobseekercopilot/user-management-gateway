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
    "profile": {},
    "token": "<JWT returned only by register and login>"
  }
}
```

`user` is omitted on failures. `token` is omitted from profile read/update
responses. Callers must use the HTTP status, not only the duplicated
`statusCode` field. Downstream client errors retain their HTTP status; an
unavailable or 5xx downstream returns `503`. Other unhandled failures currently
return `500` and are tracked for safe-error remediation in UMG-04.

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
qualifications and roles. Success is `201`. The current gateway performs only
basic name/email/password checks; shared validation hardening is tracked in
UMG-04. Registration is not yet atomic: a later profile failure can leave the
account created, as tracked in UMG-02.

## Login

`POST /api/auth/login`:

```json
{
  "email": "user@example.invalid",
  "password": "<password>"
}
```

Success is `200` and includes the user, profile and token. A missing profile is
represented with empty skills, qualifications and roles; login does not create
it.

## Read the current profile

`GET /api/auth/profile` requires `Authorization: Bearer <token>`. The gateway
asks authentication-service to derive the user ID from the token, then reads
that ID's profile. If no profile exists it creates an empty one. Success is
`200`; the response user does not contain a token.

The controller still accepts an optional `email` query parameter for backwards
compatibility, but ignores it. It is not an identity or authorisation input and
clients must omit it. Removal and an accurate OpenAPI security scheme are
tracked in UMG-06.

## Update the current profile

`PUT /api/auth/profile` requires the same bearer header and a profile body:

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
`PART_TIME_UNDER_16`, or `FLEXIBLE`. Success is `200`. Current nested status,
date and distance values are strings/integers without complete boundary
validation; UMG-04 owns that remediation.

## Machine-readable contracts

- Gateway OpenAPI: `GET /v3/api-docs` while the application is running, or
  `target/openapi.json` after `mvn -B clean verify`.
- Reviewed downstream inputs: `src/main/openapi/authentication-service.yaml`
  and `src/main/openapi/user-profile-service.yaml`.
- Downstream contract update process: `src/main/openapi/README.md`.

Examples deliberately use reserved/example values and token placeholders.
Never paste a real password or complete JWT into documentation, issues, shell
history, URLs or logs.
