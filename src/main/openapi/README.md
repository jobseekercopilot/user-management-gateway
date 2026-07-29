# Downstream API contracts

These reviewed consumer contracts are the reproducible inputs for the Java
clients generated during Maven's `generate-sources` phase. Generated Java
sources and client JARs remain build output and must not be committed.

The authentication contract carries `x-source-repository` and
`x-source-revision` fields. The User Profile input is an exact byte-for-byte
copy of the producer-owned contract and its separate `.pin.json` records the
repository, source revision, digest and published client coordinate. A build
test verifies all of that provenance. When a downstream API changes:

1. export `/v3/api-docs` from a clean checkout of that approved repository;
2. review the user-management operations and schemas against this consumer
   contract;
3. update the contract and its source revision/digest pin together;
4. run `mvn -B clean verify` and the affected cross-service contract path.

The authentication snapshot intentionally contains only public operations
consumed by this gateway. The User Profile snapshot remains the exact complete
producer document so its digest is independently verifiable; generated APIs
that are not wired into a gateway controller are not browser-facing routes.
