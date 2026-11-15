# Governed multi-row Forest query oracle, 2026-11-14

A hosted JVM used `open-transaction` to persist three `:file/name` facts in a real Prolly tree through Otehon's Forest block/ref adapter, with root `bafyreidivosmsl2hxv4fuw5my44pnqwj455oac3v755sgkzab3nr6kyh6m`. A separate JVM recovered that ref and queried the actual facts through Datalog using the admitted `:where` clauses. Limit 3 served all three names and wrote/read back a signed causal execution receipt in an in-memory store; limit 2 refused the actual three-row result before another receipt, preserving source ref and receipt head. A fresh read process also succeeded with a nonexistent Forest writer path.

The manifest and receipt use an ephemeral JVM Ed25519 keypair, but identity, policy, time, nonce and encryption are fixture callbacks; the policy authorizer always allows and the receipt backend is in memory. The logical basis is not independently signed, and there is no authenticated production identity, actual policy engine, product service, Native, OS-cache loss, multi-Pack coverage or production qualification in this experiment. The underlying physical group here is one Forest file; no GiB or performance claim follows.

A local negative control replaced the row-limit predicate in a task-local source copy with `false`, retaining all other code. The same recovered-data test failed with `unexpected receipt calls` (exit 1), confirming the test detects a missing limit guard. All original user data and the positive fixture were retained.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, writer and reader were separate processes. Tool SHA-256 `e5fb23076a46c055142b3dbf3efa1ad5a68532d14419ad9994785986719b205f`; physical Forest SHA-256 `8f0cd21d33eff752c7c21369b716f6d953a1cf7b120efef90c6acd64cb546413`; oracle SHA-256 `bf41dfb23bb5ce9a8d51f24bc9a147dd47a8a495e3a94f7cc5fce898561daf87`. No downloads. `MULTI_FOREST_OUTPUT` must name a new task-owned directory for write; it fails if the directory exists. This uses unmerged Kotobase, Kotoba and Awai OS source overlays described by `GOVERNED-FOREST-QUERY-AST-20261114.md`.

```sh
task_kotobase=/Users/junkawasaki/github/wt/codex/awai-174-key-registry
task_oracle="$task_kotobase/.agent-output/key-registry-oracle"
task_jars="$HOME/.m2/repository/org/clojure"
export MULTI_FOREST_OUTPUT="$task_kotobase/.agent-output/multi-forest-query-01"
export FOREST_TOOL=/Users/junkawasaki/github/wt/codex/awai-532-ipld-forest/.agent-output/gib-actual-writer-01/stage/forest-tool
cd /Users/junkawasaki/github/wt/codex/awai-532-ipld-forest
task_cp="$task_jars/clojure/1.12.5/clojure-1.12.5.jar:$task_jars/spec.alpha/0.5.238/spec.alpha-0.5.238.jar:$task_jars/core.specs.alpha/0.4.74/core.specs.alpha-0.4.74.jar:$task_kotobase/.agent-output/query-cycle-oracle:$task_oracle/ipld-930:$task_oracle/governed-host:$task_oracle/authority-attestation:$task_oracle/link-candidate:$task_oracle/guest-readback/cp0:$task_oracle/guest-readback/cp1:$task_oracle/guest-readback/cp2:$task_oracle/guest-readback/cp3:$task_oracle/guest-readback/cp4:$task_oracle/guest-readback/cp5:$task_oracle/guest-readback/cp6:$task_oracle/guest-readback/cp7:$PWD/nixos/otehon/storage/src:$PWD/nixos/otehon/storage/vendor/apex/src:$PWD/nixos/otehon/storage/vendor/apex-car/src:$PWD/nixos/otehon/storage/vendor/kotobase-storage/src"
java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/governed_forest_multiline.clj" write
FOREST_TOOL=/nonexistent/no-forest-writer java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/governed_forest_multiline.clj" read
```
