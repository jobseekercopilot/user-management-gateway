# Dependency vulnerability scanning

## Decision and scope

The gateway uses Trivy `v0.72.0` through the full-SHA-pinned Trivy Action. This
is the proprietary-compatible scanner already established for the runtime
image by UMG-09. UMG-11 extends it to the resolved Maven runtime dependency set
so Java dependencies receive their own blocking job evidence.

The CI `verify` job:

1. runs `mvn -B verify`, then materializes the resolved runtime dependency JARs
   in an ignored scan-only directory;
2. runs the dependency-policy fixture suite;
3. scans those JARs for library vulnerabilities and writes the complete Trivy
   JSON report, including packages without findings;
4. uploads the JSON as `dependency-vulnerability-report-<commit>` for 30 days;
5. fails if the report is missing/malformed or contains an unaccepted Critical
   or High vulnerability.

The existing container job separately covers the operating system and the
libraries present in the final image. Gitleaks separately scans complete Git
history. These controls are complementary, not substitutes.

## Advisory data and failure behavior

The Trivy Action caches the vulnerability database and Java database in the
GitHub Actions cache and refreshes them from Aqua's public OCI mirrors. The
repository needs no NVD API key or other advisory secret. A database download,
scanner, report-upload or report-validation failure fails the job; CI does not
reuse a hand-maintained result or silently pass without a report.

Database cache access follows GitHub Actions branch-cache rules. The default
branch can seed feature-branch scans, while a pull request cannot replace the
default branch cache. Scanner and report-upload Actions are pinned to immutable
commit SHAs and must be updated in a reviewed dependency pull request.

## Local reproduction

Requirements are Java 17, Maven 3.9, Docker and `jq`:

```bash
mvn -B clean verify
./scripts/test-dependency-report-policy.sh
mvn -B dependency:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory=target/dependency-scan
docker run --rm \
  -v "$PWD/target/dependency-scan:/scan/dependencies:ro" \
  -v "$PWD/config/trivy/.trivyignore:/scan/.trivyignore:ro" \
  -v "$PWD/target:/report" \
  aquasec/trivy:0.72.0 rootfs \
  --scanners vuln \
  --pkg-types library \
  --list-all-pkgs \
  --format json \
  --ignorefile /scan/.trivyignore \
  --output /report/trivy-dependencies.json \
  /scan/dependencies
./scripts/verify-dependency-report.sh \
  target/trivy-dependencies.json config/trivy/.trivyignore
```

Do not commit the generated report or cache. A local pass is supporting
evidence; the pull-request CI report is authoritative because it uses a clean
runner and current advisory data.

## Risk acceptance

Fix or upgrade a vulnerable dependency wherever practical. An exception is
allowed only when the repository owner explicitly accepts a documented risk:

1. open or update a private dependency-security issue with the vulnerability
   ID, affected package/version, reachability evidence, compensating controls,
   owner, remediation plan and review date;
2. add the issue URL immediately above one narrow ignore entry in
   `config/trivy/.trivyignore`;
3. add an `exp:YYYY-MM-DD` no more than 30 days in the future;
4. submit the exception through a reviewed pull request and attach the CI JSON
   report to the issue;
5. remove the exception by its expiry, or repeat owner review with new evidence
   and a new short expiry.

Example syntax only (do not add it without an actual reviewed finding):

```text
# Tracking: https://github.com/jobseekercopilot/user-management-gateway/issues/123
CVE-2099-12345 exp:2099-01-30
```

The policy script rejects untracked, undated, expired or longer-than-30-day
entries. It also rejects blanket package/path suppression. There are currently
no accepted findings.

## Ownership and residual risk

The repository owner approves exceptions and dependency updates. GitHub-hosted
CI owns runner/cache availability; Aqua supplies scanner and advisory data.
Trivy findings depend on published advisories and package identification, so a
clean report does not prove that every dependency is defect-free. Dependency
review remains necessary for large upgrades and newly disclosed risks.
