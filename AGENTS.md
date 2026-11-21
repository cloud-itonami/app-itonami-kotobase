# AGENTS.md — kotobase

The workspace rules in com-junkawasaki/root `AGENTS.md` apply: CDCI is
stopped, and when it restarts it runs Amu selfhost and Kotoba native gates only.

## kotobase is a library

kotobase is a library, not a service or a host ([ADR 0050](https://github.com/network-awai/network-awai-apex/blob/main/docs/adr/0050-kotobase-is-a-library-yataverse-is-the-service.md), owner decision 2026-11-21).
Do not add a deploy target, a `kotobase.net` host or service-side state here. Service work (storage, delivery,
authentication, receipts, public hosts) belongs to Yataverse, which depends on this library, never the reverse.

## CI/CD: no JVM, no nbb

Owner decision 2026-10-22: the JVM and nbb (Node.js) are not used, so they are
not part of CI/CD (Linear JUN-290). `.github/workflows/ci.yml` (a JDK 17/21 matrix: shared
security adoption, `clojure -M:test`, `clojure -M:cid-conformance`, the
Promise IStore ClojureScript test under Node, and `clojure -M:lint`) was
removed, together with the JVM test that read it.

- Do not add `setup-java`, `setup-clojure`, `clojure`, nbb, babashka or
  GraalVM steps to a workflow, and do not keep them as a fallback. A check that
  needs something the native route cannot do yet is a blocker to record in
  Linear, not permission to bring a host runtime back.
- A native gate is an Amu selfhost build: the pinned root compiler
  (kotoba-lang/amu `seed/bootstrap/aarch64-linux-static-root.elf`) compiles a
  Kotoba module in the seed subset into a loaderless static image whose result
  is checked, together with a negative control that must fail.
- The six `kotoba/*.kotoba` modules do not build with the pinned root
  compiler yet (amu `f5b11a08`, measured 2026-10-22: `E2004` for
  `cid_crypto_primitives`, `cid_external_dag_traversal`,
  `cid_external_transaction_replay`, `cid_signed_commit`; `E7102` for
  `cid_dag_traversal`, `cid_graph_replay`). Bringing them into the seed
  subset is a blocker for a native gate, not a reason to restore the JVM
  conformance run.

### What CI no longer covers

The `.cljc`/`.cljk` tests, the CID conformance qualification of the Kotoba
modules, the ClojureScript Promise path and the lint ran only on the JVM (and
Node). `docs/coverage.edn` stage m4 (CI-gated runner) no longer has a runner.
They are migration inventory (JUN-290).
