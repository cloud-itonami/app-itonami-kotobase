# Hosted CLJS frontier bounds, 2026-11-14

The compiled `frontier_bounds_async.cljs` probe passed on macOS ARM, OpenJDK
21.0.1, ClojureScript 1.11.132 and Node 26.0.0. A Promise backend supplied an
untrusted stored block to `accept-and-store!`: a 20-level CBOR array was
rejected at `:max-depth`, and a 16,385-byte block at `:max-bytes`. Both errors
were `:cbor/resource-limit`; event recording showed only ref and block reads,
with no signature verifier invocation, block put or ref CAS. These are
separate malformed fixtures, not valid signed frontier values.

The probe uses the staged classpath, sources and limitations documented in
`WORKER-STAGED-SUITE-20261114.md`: Kotoba 930 IPLD/CBOR/codec and b941 storage
sources, .cljk copied as .cljc, host export metadata adaptations, restored
historical local dependencies and cp6/cp3 overlays. It is a hosted local
oracle, not a clean dependency build, Native, production, or a reboot test.
The compiler emitted a warning about this probe's single-segment namespace.

With the retained task scratch, from the repository root:

```sh
cp oracle/awai-174/frontier_bounds_async.cljs .agent-output/cljs-worker-oracle-01/src/frontier_bounds_async.cljs
clojure -Sdeps "$(cat .agent-output/local-oracle-alias.edn)" -M:cljs-test:local-oracle -m cljs.main -co '{:target :nodejs :output-to ".agent-output/cljs-worker-oracle-01/frontier-bounds.js" :output-dir ".agent-output/cljs-worker-oracle-01/bounds-out" :optimizations :none :main frontier-bounds-async}' -c frontier-bounds-async
node .agent-output/cljs-worker-oracle-01/frontier-bounds.js
```
