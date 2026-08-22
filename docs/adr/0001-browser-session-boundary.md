# ADR 0001: Browser session boundary

- Status: Accepted for controlled private beta
- Date: 2026-07-22
- Owners: User Management Gateway and Authentication Service
- Review: Formal legal/privacy and production-platform review remains required before public launch

## Context

Angular currently receives authentication tokens and stores tokens and profile
data in JavaScript-readable browser storage. The Java gateway accepts bearer and
identity headers supplied by the browser. That design makes a successful script
injection a credential-exfiltration event and leaves CSRF, refresh concurrency,
logout, origins and downstream identity propagation undefined.

Authentication-service now issues short-lived RS256 access tokens, rotates
single-use refresh tokens, detects replay, revokes sessions on logout and
publishes JWKS. Profile-service independently validates access tokens and derives
ownership only from `sub`.

## Decision

User-management-gateway (UMG) is the browser-facing session boundary. Browser
JavaScript never receives an access or refresh token. UMG is the only component
that converts authentication-service token responses into browser cookies.

### Token custody and cookies

UMG stores the access and refresh values in separate `HttpOnly` cookies. The
production names use the `__Host-` prefix; the explicit local HTTP profile uses
different unprefixed names because `__Host-` requires `Secure` transport.

| Purpose | Production name | Local-only name | Path | HttpOnly | SameSite | Lifetime |
|---|---|---|---|---|---|---|
| Access | `__Host-jsc-access` | `jsc-access-local` | `/` | yes | `Lax` | authentication response `expiresIn`, capped by configuration |
| Refresh | `__Host-jsc-refresh` | `jsc-refresh-local` | `/` | yes | `Lax` | configured authentication refresh lifetime, initially 7 days |
| CSRF double-submit value | `__Host-jsc-csrf` | `jsc-csrf-local` | `/` | no | `Lax` | browser session |

Production cookies are always `Secure`, have no `Domain`, and production startup
fails if secure cookies or an explicit HTTPS browser origin are absent. Local
Docker HTTP is selected explicitly and cannot be enabled by production profile.
Cookies contain no user/profile data.

### CSRF and origins

`GET /api/auth/csrf` is the public bootstrap route and causes UMG to create a
cryptographically random CSRF cookie. Every state-changing browser request,
including register, login, refresh, logout and profile update, must echo it in
`X-CSRF-Token`. Missing or mismatched values return a stable redacted `403`.

Credentialed CORS is allowed only for configured exact origins. Wildcards,
origin paths, user-info and malformed origins are rejected at startup. Forwarded
client-address headers are not trusted; proxy trust requires a later explicit
platform decision.

### Route and session behavior

- Health and readiness are public and contain redacted status only.
- CSRF bootstrap is public.
- Register and login do not require an existing session but do require CSRF.
- Profile read/update, refresh and logout use the cookie session. Unknown routes
  are denied by default.
- Non-production API documentation follows an explicit configuration flag and
  does not weaken production routing.
- UMG does not accept a browser `Authorization` or `X-User-Id` header as identity.

On login (and the current pre-idempotency registration flow), UMG receives an
access/refresh pair, sets both HttpOnly cookies and returns only non-secret
account/profile data. On refresh, UMG consumes the refresh cookie and rotates
both cookies. Refresh failure clears both cookies and returns the browser to a
stable logged-out state.

Concurrent refreshes carrying the same old refresh token are coalesced by UMG.
The first request performs rotation; bounded short-lived in-memory result sharing
gives concurrent requests the same replacement pair instead of replaying the old
token. The cache key is a SHA-256 digest, never the token. The cache is bounded
and expires after the concurrency window. A future horizontally scaled UMG must
move this coordination to an approved shared store before production scale-out.

Logout calls authentication-service with the access token to revoke its session,
then clears both cookies even when the downstream session is already invalid.
Failures are redacted and recoverable; no response claims a still-active remote
session was revoked when the dependency could not be reached.

### Downstream identity

UMG authenticates the access cookie through authentication-service. It forwards
that same end-user access token as `Authorization: Bearer ...` to profile-service.
It never forwards a caller-selected user ID. Profile-service independently checks
RS256 signature, issuer, audience, expiry, token type and subject, so UMG cannot
override ownership with an ordinary header.

Authentication-service calls also include UMG's separate service-identity token.
Private signing material remains only in authentication-service.

### Rate and error policy

Register, login and refresh receive configurable direct-peer and global limits.
State is bounded in memory and expires with the rate window. A rejection includes
`Retry-After` and the same non-enumerating error contract regardless of account
existence. Authentication/session failures use stable `401`, CSRF failures stable
`403`, and throttling stable `429`. Responses and logs never contain passwords,
complete tokens, cookie values, downstream bodies, subjects or parser details.

## Consequences

This intentionally replaces the browser bearer-header contract. Angular must use
credentialed same-origin/session requests, bootstrap CSRF, stop persisting tokens
and profile PII, and handle `401` by attempting at most one coordinated refresh.
Those consumer changes remain CLIENT-02 and CLIENT-03.

UMG keeps sensitive bearer material in process memory only for the duration of a
request or bounded refresh-coalescing window. TLS termination, proxy trust,
multi-instance refresh coordination, key rotation and secret delivery remain
production-platform responsibilities and require evidence before public launch.

Registration consistency is intentionally not decided here; UMG-02 owns durable
idempotency and partial-registration recovery.
