# Bounded IPLD CID length pin, 2026-11-14

Kotoba ioIpld is pinned to draft PR #1339 head `9306581b94e23d448eedcceeb886a5cdf0e3016e`, which adds a 128-character CID input cap before parsing and provider access. This is an unmerged draft dependency; see its `lib/kotoba.ioIpld/test/bounded-cid-length-evidence.md` for the negative control and hosted JVM/CLJS suite results.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, the changed `ipld/core.cljk` was staged as `.cljc` with the host namespace-export quote adaptation in a new leading `ipld-930` classpath directory, ahead of the paths documented in `IPLD-PREFLIGHT-PIN-20261114.md`. The loaded source path was independently checked. A 100001-character CID was refused with zero instrumented parser calls and zero provider calls; the existing `governed_host_scoped_memory.clj` fixture passed linked allow/deny, issuer and replay checks against the recovered Forest/Prolly basis. Nonce and receipt stores were in memory, trust was fixture-backed, and no dependency downloads or Forest writes occurred.

This is not clean dependency resolution, Native, production or durable reboot evidence for this exact source; dependency review and CI remain open.
