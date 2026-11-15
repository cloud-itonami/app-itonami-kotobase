# AWAI-174: signed manifest binding before query — 2026-11-13 UTC

The request and signed ExecutionManifest must agree on data basis, policy and
authority epoch. Previously `validate-execution!` checked these fields only
while preparing the receipt, after the nonce was consumed and the policy and
evaluator were called. A validly signed manifest naming a different basis,
policy or epoch was eventually rejected, but the query had already run.

The shared contract now checks those three fields after manifest signature
verification and before nonce consumption or policy/evaluation in both the sync
and Promise paths. The final full receipt-bundle check reuses the same function.

Reference measurements on macOS arm64: Clojure CLI 1.12.5.1654 / Temurin 21,
ClojureScript 1.12.42 / Node 26.0.0. The local oracle uses the real existing
Ed25519 implementation with deterministic test keys, and the canonical
`kotoba.value.codec` to sign the actual manifest address. For each of the
three independently modified and re-signed fields, the old sync and async
implementations called `[:nonce :authorize :evaluate]` before rejecting with
`:cross-record-mismatch`; the candidate rejected with `:calls []`. A matching
manifest passed and called `[:nonce :authorize :evaluate :commit]`. The sink,
policy and evaluator here are test fixtures, not deployed integrations.

An adapted local JVM run of `execution-contract-test` and
`governed-execution-test` passed 19 tests / 114 assertions. For that reference
run, `.cljk` files were staged as `.cljc`, test namespace imports of
`kotoba.test` and `kotoba.lang.error/ExceptionInfo` were translated to
`clojure.test` and the host exception class, and pre-existing staged
`:kotoba/export` attributes were quoted. `manifest_inputs.json` records the
candidate staged source hashes. The sync/async direct drivers are included.
The Worker repository test namespace has not been run as a complete suite;
the independent compiled Node driver exercises the actual Promise path.

Run from a private scratch directory without the repository's `deps.edn`:

```
clojure -Sdeps '{:paths ["classpath"]}' -M manifest_sync.clj
clojure -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.12.42"}} :paths ["classpath"]}' -M -m cljs.main -co '{:target :nodejs :output-dir "cljs-out" :output-to "manifest-async.js" :optimizations :none}' -c awai-oracle.manifest-async
node manifest-async.js
```

The classpath builds on the AWAI-174 Prolly v6 reference staging, adding the
seven Kotobase governed-query source namespaces. An initial command run from
the repository root attempted to resolve its old `kotobase-engine` git URL and
failed with repository not found; no successful download resulted. All passing
runs were from scratch with locally cached dependencies. This evidence does
not claim a signed commit, real durable receipt, Native gate or deployment.
