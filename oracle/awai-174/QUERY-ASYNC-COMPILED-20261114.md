# Compiled async query oracle, 2026-11-14

The actual `authorized_query.cljk` was copied byte-for-byte as `.cljc` into an
isolated scratch classpath (SHA-256
`7d38fb251dd560c3924341ccb4d5f03d28df1d667b814c79f4838bb8cbf6bde6`).
The tracked `query_async_compiled.cljs` driver exercises real ClojureScript
Promise admission and execution: an allowed two-row result invokes one
receipt, a three-row result exceeds the limit without another receipt, and a
denial rejects. The final counts are two evaluations and one receipt. It passed
on macOS ARM, OpenJDK 21.0.1, ClojureScript 1.12.42 and Node 26.0.0.

This isolated run uses previously staged `kotoba.lang.coll` and
`kotoba.lang.text` from `guest-readback/cp6` (SHA-256 respectively
`6880ecaf27a40f6385a5861d6b04ea31456c69d745964a97f513acfded86041e` and
`35b52f68b6d5808f6b178f662fb1c62c7e50b09981fb1d549dd661c498dd69af`).
The compiler and dependencies were already cached; no successful download
was needed for this isolated command. From the scratch directory:

```sh
clojure -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.12.42"}} :paths ["src" "../key-registry-oracle/guest-readback/cp6"]}' -M -m cljs.main -co '{:target :nodejs :output-to "query.js" :output-dir "out" :optimizations :none :main awai-oracle.query-async-compiled}' -c awai-oracle.query-async-compiled
node query.js
```

The repository-wide Worker suite did not run: normal dependency resolution
could not clone the old `kotobase-engine` URL. The specified engine SHA
`b8b163e889341cdf49217570380872e0575fcbdd` exists in local Git history, but
the local checkout was actually at `a1ff23ade4f4ceca520311a99961e28fe6619f59`.
The earlier assertion that the checkout matched the pin was incorrect. A
scratch archive of the exact pinned commit subsequently exposed further
unavailable historical dependency URLs and local-root conflicts; they remain
unresolved. This probe is not a full Worker
suite, durable receipt, Native gate, deployment or resource-bound proof.
