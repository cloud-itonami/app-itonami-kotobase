# Bounded IPLD CID preflight pin, 2026-11-14

The Kotoba ioIpld dependency is pinned to draft PR #1339 head `970b5954f3145e27d82a2d8c79b7261303719b82`, which rejects malformed/noncanonical CID and unsupported bounded codec before provider access. This is a draft dependency, not a merged or released source. See its `lib/kotoba.ioIpld/test/bounded-cid-preflight-evidence.md` for the prior-head negative control and hosted JVM/CLJS tests.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, the changed `ipld/core.cljk` was copied as `.cljc` with the host namespace-export quote adaptation into a new leading `ipld-970` classpath directory. A probe verified the actual loaded resource came from that directory, an invalid CID was refused and the provider call count was zero. With that same leading path, the existing `governed_host_scoped_memory.clj` fixture passed its actual causal trust/Ed25519, linked allow/deny, override/refusal and replay checks against recovered Forest/Prolly; the nonce and receipt stores remained in memory. Other fixture sources and classpath follow `GOVERNED-HOST-SCOPED-20261114.md`. No dependencies downloaded or Forest writes.

These are local hosted references, not clean dependency resolution, a product build, Native, CI, production trust or reboot evidence for this exact candidate. Review and CI of the dependency and end-to-end durable integration remain open.
