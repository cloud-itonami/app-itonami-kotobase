# Signed basis admission order (2026-11-14 UTC)

The previous signed-basis verifier called the checkpoint adapter before rejecting an invalid signature. This could trigger physical checkpoint reads for a refused signature. The verifier now checks the logical-commit digest, location and authorized signature before asking the checkpoint adapter to restore and validate the selected physical root. Location verification remains host-provided and may itself perform I/O; this change does not claim that all pre-authorization I/O is eliminated.

Local reference-only runs on macOS arm64: Clojure CLI 1.12.5.1654, ClojureScript 1.12.42, Node v26.0.0. The existing shared reference classpath was used; no dependencies were downloaded. Candidate `signed_basis.cljk` SHA-256 `580d6b2e232cd2a1ad075eca7dbff62c62da41be4403a6e4022a41095421a012`, staged as `.cljc`. Tests and callbacks are fixtures, not production key registry, policy, receipt or Native evidence.

From the isolated `.agent-output/signed-order-20261114` directory, with candidate/test files staged under `classpath` and the existing reference dependencies in `../classpath`:

```sh
clojure -Sdeps '{:paths ["classpath" "../classpath"]}' -M -e '(require (quote kotobase.signed-basis-test)) (let [r (clojure.test/run-tests (quote kotobase.signed-basis-test))] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))'
clojure -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.12.42"}} :paths ["classpath" "../classpath"]}' -M -m cljs.main -co '{:target :nodejs :output-dir "cljs-out" :output-to "signed-order.js" :optimizations :none}' -c signed-basis-async
node signed-order.js
```

The candidate JVM oracle passed 2 tests / 14 assertions. The CLJS oracle passed the valid and rejected-signature paths and observed respectively `[:digest :location :signature :checkpoint]` and `[:digest :location :signature]`. A negative control staged the parent revision `39d0bc7` verifier with the same JVM test: it failed 1 of 14 assertions because calls were `[:checkpoint :signature]`. These are local one-off reference measurements, not CI or Native gates.
