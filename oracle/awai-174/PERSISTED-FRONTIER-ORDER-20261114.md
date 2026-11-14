# Persisted frontier precheck and recheck (2026-11-14 UTC)

The admission now reads and checks the saved frontier before invoking the host verifier, then rereads it after successful verification. A stale root or epoch therefore does not invoke the potentially I/O-heavy signature/checkpoint verifier; a frontier change while that verifier is running is refused. Both checks and the verifier remain required. This narrows a race but is not a lock: the frontier can still change after admission, and a malicious or rolled-back backing store is outside this contract. The fetch and frontier read themselves can perform I/O before signature verification.

Local reference measurements on macOS arm64, Clojure CLI 1.12.5.1654 and ClojureScript 1.12.42 / Node v26.0.0: JVM `persisted-basis-test` passed 3 tests / 15 assertions; compiled Promise test passed valid, invalid signature, stale entry and frontier-advance-during-verification cases. The existing governed-read repository suite with its actual causal sink and in-memory backend passed 4 tests / 41 assertions. The parent source under the new test had two expected failures: stale input called the verifier, and a change during verification produced the old `:frontier-mismatch` rather than the explicit `:frontier-changed`.

The sources were staged as `.cljc` with the existing bounded codec / frontier reference overlays, not added to CI or Native runtime. Initial baseline execution from the repository root attempted to resolve the repository's old `kotobase-engine.git` dependency and failed with repository not found; no successful download resulted. Re-running from isolated scratch with `-Sdeps` used existing dependencies. The source `persisted_basis.cljk` SHA-256 `4f6b64fb1ae82a2ca690a27209bb88edb88715c429612e202b7a90ea7826e46d` includes a docstring-only clarification after the test run; executable forms are unchanged. The oracle uses an in-memory frontier, fixture verifier and local host runtime; it does not prove production keys, durable replay protection, cross-process fencing, Native or OS cache-loss behavior.

From `.agent-output/frontier-order-20261114`, candidate and test sources were staged in `classpath`; the other two paths are the previously captured local reference overlays:

```sh
clojure -Sdeps '{:paths ["classpath" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/frontier/src" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/classpath"]}' -M -e '(require (quote kotobase.persisted-basis-test)) (let [r (clojure.test/run-tests (quote kotobase.persisted-basis-test))] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))'
clojure -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.12.42"}} :paths ["classpath" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/frontier/src" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/classpath"]}' -M -m cljs.main -co '{:target :nodejs :output-dir "cljs-out" :output-to "persisted-order.js" :optimizations :none}' -c persisted-basis-async
node persisted-order.js
```

The governed-read suite used the command and staging described in `GOVERNED-READ-SUITE-20261114.md`, with the candidate persisted-basis source added ahead of the previous source overlay.
