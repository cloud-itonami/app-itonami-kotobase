# Signed policy query and Otehon scoped publication, 2026-11-14

This hosted reference replaces the custom staged backend in `signed_policy_multi_receipt.clj` with the actual `otehon.kotobase-forest-blocks/publish-scoped!` API from Awai OS PR #124 revision `aca4768ab8ffe2a217ed4bb6c2966054acd0257e`. It recovers the same signed three-row Prolly basis and its policy, binds the basis envelope CID, physical root and policy CID into the signed execution manifest, verifies that binding, applies a Dango fixture decision and policy filter to admitted Datalog clauses, and publishes request/manifest/causal receipt blocks using the Otehon API. The scope's intermediate block acknowledgements are provisional; the trusted fixture does not return the receipt acknowledgement until after scoped publication, CAS and independent readback.

The authorized query returned three rows; a limit-2 query was rejected without a ref. An injected false semantic verifier caused `:closure-rejected`, no returned rows and no `receipt-failed` ref. A wrong-resource Dango decision skipped evaluation and created a denial receipt. A fresh JVM with a nonexistent writer executable recovered and verified both signed bundles and refused missing request bytes, wrong basis location, wrong policy CID and invalid basis signature; overflow and failed refs remained absent.

```clojure
{:overflow :refused, :mode :write, :policy :recovered, :rows 3, :semantic-failure :refused, :receipts :scoped-forest-ref, :pass true, :dango-denial :receipted, :signed-basis :verified}
{:overflow-ref :absent, :mode :read, :missing-request :refused, :allow :verified, :policy :recovered, :pass true, :failed-ref :absent, :deny :verified, :signed-basis :verified, :wrong-basis-policy-signature :refused}
```

This composes a real Otehon API in a hosted fixture, not a product service. The signer/registry/clock/nonce are fixtures; the signed-basis source and receipt stores are separate, and the IDs/public-key sidecar is neither authenticated nor crash-atomic. A builder can still make external side effects if the trusted caller violates the API contract; the overlay does not provide a durable `IBlockStore` acknowledgement. Concurrent GC, independent fencing, Native, GiB data and OS-cache-loss are not tested here. Three private tool copies, including the rejected semantic-verifier case, occupy ~194 MiB of retained staging. The earlier guest-reboot evidence used the earlier custom composition, not this new API.

macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5. Script SHA-256 `196619317d3ec7ca509535f62f44bb8a74fc467f50b2a85d360fa0a21f6f7462`; Otehon adapter SHA-256 `5bfa4b1073466678b87ccb5bd766aa42e921be61c4f5cfe3fbc568f25bda8216`; fixture IDs/public-key sidecar SHA-256 `c9c31a82dc6527309e84665d02df539ca6011d39a47dbce0c6434fa287247965`. Dependency and tool pins follow `SIGNED-POLICY-MULTI-RECEIPT-20261114.md`; no downloads. The script exclusively creates the `scoped-receipts` subdirectory; use a fresh copy of the signed-basis store to repeat a write and preserve existing evidence.

```sh
# Set task_kotobase, task_cp and FOREST_TOOL as in GOVERNED-FOREST-MULTIROW-20261114.md,
# with the Awai OS source at the specified revision on the classpath.
export KOTOBASE_ORACLE_ROOT="$task_kotobase"
export MULTI_FOREST_OUTPUT="$task_kotobase/.agent-output/multi-policy-signed-01"
java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_scoped_receipt.clj" write
FOREST_TOOL=/nonexistent/no-forest-writer java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_scoped_receipt.clj" read
```
