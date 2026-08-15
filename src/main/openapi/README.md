# Downstream API contracts

These reviewed consumer contracts are the reproducible inputs for the Java
clients generated during Maven's `generate-sources` phase. Generated Java
sources and client JARs remain build output and must not be committed.

The authentication consumer contract carries `x-source-repository`,
`x-source-revision` and `x-source-contract-sha256` fields. Its separate pin
records both the full producer export digest and the reviewed, gateway-scoped
consumer-contract digest. Its current reviewed source is Authentication
contract `2.0.0`, revision
`2806272dfabbd23a369ca13cd258b689ff0554f8`, digest
`a36b56124ce3893dc91a3fc481d4261dc5d15ef9046a0c2e8bce018bc3d0e2e2`.
The User Profile input is an exact byte-for-byte
copy of the producer-owned contract and its separate `.pin.json` records the
repository, source revision, digest and published client coordinate. A build
test verifies all of that provenance. The current reviewed User Profile input
is contract `2.3.0`, source revision
`a880add6e5c7106a2f3abec08a147823f88edf20`, including the owner-scoped
professional-contact operation and schemas. When a downstream API changes:

1. export `/v3/api-docs` from a clean checkout of that approved repository;
2. review the user-management operations and schemas against this consumer
   contract;
3. update the contract and its producer/consumer revision and digest pin together;
4. run `mvn -B clean verify` and the affected cross-service contract path.

The authentication snapshot intentionally contains only public operations
consumed by this gateway. The User Profile snapshot remains the exact complete
producer document so its digest is independently verifiable; generated APIs
that are not wired into a gateway controller are not browser-facing routes.
