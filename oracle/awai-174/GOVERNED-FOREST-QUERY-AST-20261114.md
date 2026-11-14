# Request-bound clauses over Forest / Prolly, 2026-11-14

The older `governed_host_scoped_memory.clj` fixture admitted a placeholder `:where` while its evaluator ignored it and executed a different hard-coded query. Thus it proves that fixture composition, not faithful execution of the admitted query. `governed_forest_query_ast.clj` is a follow-on hosted oracle: it puts the actual clauses in the request-bound query digest and passes exactly the received `:where` into Datalog over a recovered Forest-backed Prolly snapshot. The fixture-specific `:find` mapping remains fixed; this is not a general product query compiler. The signed execution receipt binds the request digest; the request itself is not asserted to be independently signed.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, with Kotobase candidate `8737d387d93ab14c8f251b655e9bdbd086a35aed`, bounded ioIpld draft `9306581b94e23d448eedcceeb886a5cdf0e3016e`, and Awai OS Forest candidate `02be90c12e24ea2ccd769dc99d9f501dcbf97117`, the index-8 query returned one row, an otherwise identical index-999999 query returned zero, both recorded execution receipts binding their respective requests, and a mismatched query digest was refused before evaluation and receipt-head change. Output:

```clojure
{:source :forest-prolly, :request-query-clauses :forwarded, :index-8 1, :index-absent 0, :query-digest-mismatch :refused, :receipt-head :unchanged-on-mismatch, :nonce-and-receipt-backend :memory, :pass true}
```

The input snapshot has one `:chunk/cid` fact. An initial proposed test expected multiple results after removing the index predicate; it correctly failed because there was only one, so an actual-data over-limit case remains unverified. The separate limit unit test is not substituted for it. A real Dango/policy fixture and Ed25519 are used, but registry, authority, keys and clock are fixtures, and nonce/receipt storage is in memory. The source is a hosted reference, not product wiring, durable receipt, Native, production security, clean dependency resolution, CI, cold-cache or power-loss evidence. No downloads or Forest writes in this run. Source SHA-256 `8e4ab981a5c0cc3430813e30c5a10708a641abb1df1e532490e099d2942a3d5c`.

Command, using the preserved fixture overlays and the staged current `authorized_query.cljc` from `QUERY-RESULT-LIMIT-20261114.md`:

```sh
task_kotobase=/Users/junkawasaki/github/wt/codex/awai-174-key-registry
task_oracle="$task_kotobase/.agent-output/key-registry-oracle"
task_jars="$HOME/.m2/repository/org/clojure"
export KOTOBASE_ORACLE_ROOT="$task_kotobase" SIGNED_BASIS_OUTPUT="$task_oracle/guest-readback/data/basis" GROUP_FOREST_STORE="$task_oracle/guest-readback/data/basis/store" FRONTIER_FOREST_OUTPUT="$task_oracle/guest-readback/data/frontier" BOUND_RECEIPT_OUTPUT="$task_oracle/guest-readback/data/receipt" FOREST_TOOL="$task_oracle/nonexistent-tool"
cd /Users/junkawasaki/github/wt/codex/awai-532-ipld-forest
java -cp "$task_jars/clojure/1.12.5/clojure-1.12.5.jar:$task_jars/spec.alpha/0.5.238/spec.alpha-0.5.238.jar:$task_jars/core.specs.alpha/0.4.74/core.specs.alpha-0.4.74.jar:$task_kotobase/.agent-output/query-cycle-oracle:$task_oracle/ipld-930:$task_oracle/governed-host:$task_oracle/authority-attestation:$task_oracle/link-candidate:$task_oracle/guest-readback/cp0:$task_oracle/guest-readback/cp1:$task_oracle/guest-readback/cp2:$task_oracle/guest-readback/cp3:$task_oracle/guest-readback/cp4:$task_oracle/guest-readback/cp5:$task_oracle/guest-readback/cp6:$task_oracle/guest-readback/cp7:$PWD/nixos/otehon/storage/src:$PWD/nixos/otehon/storage/vendor/apex/src:$PWD/nixos/otehon/storage/vendor/apex-car/src:$PWD/nixos/otehon/storage/vendor/kotobase-storage/src" clojure.main "$task_kotobase/oracle/awai-174/governed_forest_query_ast.clj"
```
