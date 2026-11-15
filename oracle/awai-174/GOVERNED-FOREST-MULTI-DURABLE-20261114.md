# Multi-row Dango, policy and Forest receipt oracle, 2026-11-14

This extends the three-fact source from `GOVERNED-FOREST-MULTIROW-20261114.md`, using a separate task-owned Forest-backed Kotobase receipt backend. An actual Dango root token with a fixture seed is verified for the source basis resource; a wrong resource is refused. An explicit Kotobase peer protected-prefix policy is evaluated and used to filter Datalog scans. A host adapter converts the Dango decision into the governed query authorization result. The allowed query recovered three rows, the limit-2 query refused after evaluating the actual three rows without a receipt, and the wrong-resource policy denial skipped evaluation and committed a denial receipt.

A fresh JVM with a nonexistent writer executable recovered both allow and deny causal execution receipts from the Forest-backed backend at their exact CIDs and commit CIDs, checked their decision types and verified both Ed25519 signatures using the saved public test key. A negative-control source copy changed the receipt verification payload; the independent read failed with `receipt signature invalid` (exit 1). The source data ref was unchanged. The receipt IDs and public key were written to a sidecar `ids.edn`; that sidecar is not a crash-atomic, authenticated or rollback-resistant frontier.

Output on macOS ARM, OpenJDK 21.0.1 / Clojure 1.12.5:

```clojure
{:mode :write, :rows 3, :overflow :refused, :dango-denial :receipted, :deny-evaluation :skipped, :receipt-backend :forest, :policy :fixture, :pass true}
{:mode :read, :allow :verified, :deny :verified, :signatures :verified, :receipt-backend :forest, :source-ref :unchanged, :pass true}
```

The JCA receipt key is ephemeral, Dango seed/roots, clock, nonce and policy are fixtures, the policy is supplied in memory rather than recovered from the same signed basis, and encrypt/decrypt are identity functions. The query's logical basis is not independently signed. Receipt blocks were saved in Forest, but no signed authority ledger, atomic sidecar, independent witness, Native, OS-cache loss, power loss, independent-node, general-purpose product integration or GiB result is established. A real token verification/filter plus adapter is not proof of production key provenance or policy administration. The retained receipt work directory occupied approximately 389 MiB, mainly per-write staging/tool copies; no cleanup was performed.

The source SHA-256 is `196685b4987ff959dd198e34cf97807b6ddec5dc2eed28a99f252af5a7503772`; the generated public-key/IDs sidecar SHA-256 is `4352e6841b93e0f01b434a26b6b4902c1cd13cd391eaa83f1d28109ec395f9af`. Forest executable hash and classpath pins follow `GOVERNED-FOREST-MULTIROW-20261114.md`; no downloads occurred. For reproduction use that document's environment and `task_cp`, set `KOTOBASE_ORACLE_ROOT="$task_kotobase"`, then run the source below. `write` creates `MULTI_FOREST_OUTPUT/durable-receipts` exclusively; use a fresh copy of the source fixture and preserve all existing output. Each command starts a fresh JVM.

```sh
export KOTOBASE_ORACLE_ROOT="$task_kotobase"
java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/governed_forest_multi_durable.clj" write
FOREST_TOOL=/nonexistent/no-forest-writer java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/governed_forest_multi_durable.clj" read
```
