# Security policy

This repository is private. Report suspected vulnerabilities privately to the
repository owner. Do not open a public issue or include credentials, tokens,
personal data, exploit details, or production logs in an issue.

Do not commit secrets. Use runtime environment variables or the approved
secret-management mechanism. If a credential may have been exposed, stop its
use, report the type and affected location without reproducing its value, and
arrange rotation with the owner.

The current code is a beta-readiness baseline, not a security certification.
Known risks and beta blockers are tracked in `docs/BETA_READINESS_AUDIT.md`.

The gateway is the browser session boundary. It returns no access or refresh
token to JavaScript, accepts no browser-selected identity header, requires CSRF
on every state-changing route, and permits credentialed CORS only for exact
configured origins. The production profile requires Secure `__Host-` cookies
and HTTPS origins and disables API documentation. Do not weaken these controls,
log cookie/token values, or enable the localhost HTTP defaults in production.
See `docs/adr/0001-browser-session-boundary.md` for the accepted boundary and
its remaining platform assumptions.

Dependency vulnerability reports are generated for every push and pull request.
Critical and High findings block the build unless the repository owner approves
a narrow, issue-linked exception with an expiry of no more than 30 days. See
`docs/DEPENDENCY_SECURITY.md`; a scanner failure or missing report fails closed.
