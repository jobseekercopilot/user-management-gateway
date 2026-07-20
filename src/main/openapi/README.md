# Downstream API contracts

These reviewed consumer contracts are the reproducible inputs for the Java
clients generated during Maven's `generate-sources` phase. Generated Java
sources and client JARs remain build output and must not be committed.

The `x-source-repository` and `x-source-revision` fields identify the exact
downstream `develop` revision from which each contract was reviewed. When a
downstream API changes:

1. export `/v3/api-docs` from a clean checkout of that approved repository;
2. review the user-management operations and schemas against this consumer
   contract;
3. update the YAML and source revision together;
4. run `mvn -B clean verify` and the affected cross-service contract path.

The snapshots intentionally contain only the public operations consumed by
this gateway. Internal system-data operations are not part of its runtime
dependency.
