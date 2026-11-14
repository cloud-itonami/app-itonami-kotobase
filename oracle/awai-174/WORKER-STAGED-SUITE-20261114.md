# Staged Worker suite, 2026-11-14

On macOS ARM, OpenJDK 21.0.1, ClojureScript 1.11.132 and Node 26.0.0,
the repository's `kotobase.async-test-runner` compiled and ran **55 tests,
398 assertions, zero failures and zero errors**. This includes the governed
execution async and portable suites, canonical causal commit async,
conformance, effect contract, evidence, execution keys and governed effect.
No compiler warning appeared in the final invocation.

This was a staged local oracle, not the unmodified repo command, a CI gate,
Native, a deployed Worker or a durable Forest integration test. Historical
repository URLs returned 404; exact historical commits were extracted from
local Git objects where available, and explicit local dependency overrides
were necessary. A peer commit was fetched from GitHub. Staging, including the
complete override alias, stays under `.agent-output/cljs-worker-oracle-01`
and `.agent-output/local-oracle-alias.edn` in this worktree.

Source and tests were copied from candidate commit
`6ffa070` with `.cljk` copied as `.cljc`. In the staged tests only,
`kotoba.test` imports were changed to `cljs.test` macros and
`kerror/ExceptionInfo` to `cljs.core/ExceptionInfo`. Previously staged cp6/cp3
dependencies remain on the classpath; they are not an independently verified
clean dependency build. Storage core and memory were staged from Kotoba
`b94158fd48ec541b70d3c455912a06f407537ddc`; value codec, IPLD value and CBOR
were staged from Kotoba `9306581b94e23d448eedcceeb886a5cdf0e3016e`. Their
`:kotoba/export` metadata was quoted where necessary for the host compiler.
The earlier run had a stale `ipld.value` warning; this final run replaced it.

Staged SHA-256: IPLD value `2ebb778cef1024fa936e8435ee7927a23ba670ada69145a8b01c57ecf6b37975`,
CBOR `b597c060b56766a56d94f367ed1bc7c59ec8d086a93ac85037867bb701ec33ab`,
value codec `a8e4b199461ea3ffc2ecfa97ec4793b986ba59bf7ae6d35f50024ff7f733b504`,
storage core `4ce94d0eb58ade7bc6afca48c946bf3eb79f7918a844b52ec911c1c0c37adef5`,
memory `65ee21de06160c2dd529f08f3f9646e40815333b0505f0263292a33debdeddfa`.

From the repository root with the retained task scratch:

```sh
clojure -Sdeps "$(cat .agent-output/local-oracle-alias.edn)" -M:cljs-test:local-oracle -m cljs.main -co '{:target :nodejs :output-to ".agent-output/cljs-worker-oracle-01/tests.js" :output-dir ".agent-output/cljs-worker-oracle-01/out" :optimizations :none :main kotobase.async-test-runner}' -c kotobase.async-test-runner
node .agent-output/cljs-worker-oracle-01/tests.js
```

The result does not demonstrate structural-limit rejection: the suite does
not assert that behavior. Reproducible clean dependency resolution and the
full persistent Datalog/Forest product path remain open.
