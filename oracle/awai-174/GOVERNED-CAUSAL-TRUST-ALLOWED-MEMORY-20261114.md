# Real causal trust decision with recovered Prolly, 2026-11-14

Run `governed_registry_causal_trust_allowed_memory.clj` (SHA-256 `99a5b806f514b858f25800fd335a2e7a5f2f84f9b27b6356437a49c510864c17`) in a hosted JVM with the same environment, classpath, recovered Forest/Prolly basis and pinned tool described in `GOVERNED-REGISTRY-POSTBOOT-HOST-20261114.md`. This variant replaces the receipt and nonce backend with one in-memory backend; it makes no Forest writes and requires no downloads.

Actual `grant.causal-trust/decide` evaluated the fixture identity, grant and policy. A deny was refused before basis access without changing memory state; a fresh allow entered the registry-bound signed basis path and returned the expected row from recovered Prolly/Datalog. The causal execution receipt CID and test-key Ed25519 signature verified. A repeated nonce was refused before query and did not change the memory backend.

```clojure
{:receipt "bafyreie6cfs4im2r5srxr2vxcqubqr4dbrmaorrr3hvy3vf6wcincrx474", :signature :valid, :replay :refused, :receipt-store :memory, :rows 1, :denial :before-basis, :trust :actual-kernel, :pass true, :nonce-store :memory, :decision :allow}
```

This demonstrates composition with a real decision algorithm and fixture inputs. Since the nonce and receipt live only in memory, it is not a persistent receipt, process-restart or crash-recovery result. Production principal authentication, grants, policy inputs, trust claims, key custody, and Native execution remain open.
