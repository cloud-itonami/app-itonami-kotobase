# Signed policy query and explicit provisional ports, 2026-11-14

This hosted reference composes Kotobase `prepare-execution-receipt` (PR #101 commit `ea178e4`) with Otehon `publish-ports!` (PR #124 commit `738331b`), avoiding the prior fixture's volatile object that implemented the durable `IBlockStore` protocol. The preparation step returns canonical transaction data and receipt CID without writing or claiming durability. The builder uses the existing Kotobase peer `commit!` with explicit provisional put/get ports, stages the request and signed manifest, and returns only a commit CID and receipt CID. After Forest batch persistence, semantic bundle verification and conditional ref CAS, the caller reads the causal receipt and bundle back through the real Forest backend, then creates the durable acknowledgement.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, the recovered signed multi-row basis and same-snapshot policy admitted the actual Dango-authorized Datalog query and 3 rows. Limit 2 returned no rows/ref, wrong resource created a denial receipt without evaluation, and an injected false pre-CAS verifier returned no rows/ref. A separate JVM with a nonexistent writer path recovered both allow/deny receipt bundles, verified signatures and the basis/policy location binding, and refused missing request bytes, wrong location/policy and invalid basis signature. Output:

```clojure
{:overflow :refused, :mode :write, :policy :recovered, :rows 3, :semantic-failure :refused, :receipts :explicit-ports-forest-ref, :pass true, :dango-denial :receipted, :signed-basis :verified}
{:overflow-ref :absent, :mode :read, :missing-request :refused, :allow :verified, :policy :recovered, :pass true, :failed-ref :absent, :deny :verified, :signed-basis :verified, :wrong-basis-policy-signature :refused}
```

This is a hosted integration experiment, not a product service. Identity/keys/clock/nonce are fixtures, the physical location sidecar is not a trusted rollback witness, source and receipt stores are separate, and the trusted builder must not make external effects. The source is one physical Forest and each receipt group is bounded; Native, a GiB query, concurrent GC, independent fencing, guest OS cache loss for this specific API and physical power loss are not tested. The previous reboot proof used the old provisional protocol overlay and is not evidence for this revision. Three retained private tool stages use ~194 MiB; no downloads.

Script SHA-256 `3eb65b0ea5bbe91cf2d5f9b2c89ed0f786a5157ea042d31131d4629108302e76`; Kotobase causal source `f880356bf86bd780e06a2c09ded80e817d2125eaebebee92508551206cbf0c3b`; Otehon adapter `0e6c34b1c666e9a84733253fc7ab5f935569ecbec35597c81ccc760d9d61da25`; generated sidecar `09836970019ca014097fd3facec06da46b30ea6ba858be4fb225d1f304f70322`. The source is staged as `.cljc` with local oracle adaptations described in `PREPARED-RECEIPT-TX-20261114.md`. Reproduce using the classpath and environment in `SIGNED-POLICY-MULTI-RECEIPT-20261114.md`, adding `$task_kotobase/.agent-output/prepared-receipt` first on the classpath and using Awai OS source at the stated commit. The `ports-receipts` directory is created exclusively; use a fresh copy of the signed-basis fixture and preserve existing evidence.

```sh
java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_ports_receipt.clj" write
FOREST_TOOL=/nonexistent/no-forest-writer java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_ports_receipt.clj" read
```
