# Governed read with persisted basis (2026-11-14 UTC)

`read-bound!` and `read-bound-async!` compose the existing authority-capability checks and canonical execution receipt sink with persisted-basis admission. The original unbound `read!` remains available; callers must opt into the bound operation and supply a trustworthy frontier backend and verifier. This change does not configure a production caller, key registry, policy, nonce ledger or receipt store.

Local reference-only runs: macOS arm64, Clojure CLI 1.12.5.1654, ClojureScript 1.12.42, Node v26.0.0. `governed_read.cljk` SHA-256 `fe70670f45f2ab0154bdba9a8b857795893e4c48d84caae563f1568cea9825e4`; the candidate was staged as `.cljc`. Tests use real governed execution, Ed25519 test keys, the bounded codec and an in-memory frontier. A deliberately substituted causal-commit namespace returns a fixture receipt sink; this is not evidence of durable receipt publication. Both JVM and Promise runs observed valid `[:fetch :verify :nonce :authorize :evaluate :commit]` and invalid `[:fetch :verify]`. A separate repository test for the real receipt sink and in-memory backend was added but has not been run in this local reference setup.

With candidate sources, fixture sink and oracle sources staged in `.agent-output/bound-read-20261114/classpath`, the existing reference dependencies in `../classpath` and `../frontier/src`, and bounded `kotoba/value/codec.cljc`, `ipld/value.cljc` and `cbor/core.cljc` copied from `../frontier/out`:

```sh
clojure -Sdeps '{:paths ["classpath" "../frontier/src" "../classpath"]}' -M ../../oracle/awai-174/governed_bound_read_oracle.clj
clojure -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.12.42"}} :paths ["classpath" "../frontier/src" "../classpath"]}' -M -m cljs.main -co '{:target :nodejs :output-dir "cljs-final-out" :output-to "bound-read-final.js" :optimizations :none}' -c governed-bound-read-async
node bound-read-final.js
```

Initial CLJS runs failed because the prior generated output on the classpath shadowed Closure files, and then because unbounded codec dependencies were mixed with the bounded codec. These outputs were retained; the final run used a fresh output and the three matching bounded sources. No dependencies were downloaded. These local measurements are not Native, CI, real policy/receipt or OS-cache-loss qualification.
