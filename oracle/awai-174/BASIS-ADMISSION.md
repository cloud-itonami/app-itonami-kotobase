# AWAI-174 basis admission reference, 2026-11-14 JST

The opt-in `execute-bound!` requires a trusted synchronous basis verifier; `execute-bound-async!` awaits a trusted Promise-capable verifier. Both pass the authenticated request and signed manifest only after signature and request/manifest binding have passed. Literal `true` permits nonce consumption, authorization and evaluation; false, nil, a truthy non-boolean, an exception, rejection, or a missing verifier refuses. Admission failures occur before the guarded policy decision and do not create a policy-denial receipt. The host owns proof, location, policy, freshness and trust verification; this entry point does not implement or qualify them. Existing v1 `execute!` and `execute-async!` are unchanged entry points and do not require basis admission.

Local reference on macOS arm64: Clojure CLI 1.12.5.1654 / JVM and compiled ClojureScript 1.12.42 / Node 26.0.0 exercised a successful signed fixture, cross-record mismatch, invalid Ed25519 signature, false/non-boolean verifier, thrown/rejected verifier and missing verifier. Success order was basis, nonce, authorization, evaluation, receipt commit; every refusal stopped before nonce. The pre-existing sync manifest oracle still passed. The hosted JVM version of the governed execution test namespace passed 15 tests / 112 assertions. For that local test only, `kotoba.test` was mapped to `clojure.test` and `kotoba.lang.error/ExceptionInfo` to the JVM exception class; this is not a native test result.

The oracle classpath is the prior `manifest_inputs.json` adapted inputs, except that `kotobase/governed_execution.cljc` is the candidate source copied as `.cljc` and `awai_oracle/basis_async.cljs` is this unit's oracle. For the unit test the transformed test namespace also overrides the old classpath copy. From a private scratch directory with that classpath named `classpath`:

```sh
clojure -Sdeps '{:paths ["classpath"]}' -M ../oracle/awai-174/basis_sync.clj
clojure -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.12.42"}} :paths ["classpath"]}' -M -m cljs.main -co '{:target :nodejs :output-dir "basis-cljs-out" :output-to "basis-async.js" :optimizations :none}' -c awai-oracle.basis-async
node basis-async.js
```

A first invocation from the repo root inadvertently attempted to fetch the project git dependency `kotobase-engine`; the remote returned repository-not-found and no dependency was downloaded. Running from scratch avoided the project dependency. JVM and Node are reference oracles only, never Native gate, build dependency or fallback. This does not establish Native execution, a product-owned verifier, persistent frontier, or storage durability.

A later same-day negative control found that the async catch originally classified a verifier rejection carrying `:kotobase.query/reason` as a policy denial and called the receipt commit before the guarded read began. The pre-fix trace was `[:basis :commit]`. The candidate marks entry into the guarded read before permitting policy-denial receipt handling. Compiled ClojureScript now leaves that failure at `[:basis]`, while a real policy denial after admission retains `[:basis :nonce :authorize :commit]`. The adapted JVM suite remains 15 tests / 112 assertions. This is a local reference result, not a Native gate.
