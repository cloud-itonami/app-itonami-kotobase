# Causal trust decision with recovered governed fixture, 2026-11-14

Following `GOVERNED-REGISTRY-POSTBOOT-HOST-20261114.md`, run `governed_registry_causal_trust_replay.clj` in a fresh hosted JVM with the same environment, classpath, recovered stores and pinned tool. Fixture SHA-256: `0a83a52d9f6395a3815f886ec702d6cc38c736fbe2e105c6147a58b4bb2375a7`. No dependency download or Forest write was needed.

The fixture invokes the actual `grant.causal-trust/decide` authority kernel with a fixture identity, grant and policy, and passes its decision into `read-registry-persistent!`. The allow decision reaches persisted basis admission and is rejected on the already consumed nonce; the existing receipt's signature and CID verify and the root remains unchanged. Changing the fixture policy to deny object reads yields a deny that is rejected before basis fetch or evaluation.

```clojure
{:replay :refused, :events [:basis], :root :unchanged, :receipt "bafyreibymgzopqwpv3e7kyzp72jf2mmg6ejf7bxeleuunenlw37b36a674", :signature :valid, :pass true}
{:kernel :causal-trust, :allow :nonce-replay-refused, :deny :before-basis, :pass true}
```

The first trial was correctly rejected because its sequence-1 epoch lacked a predecessor; the successful fixture uses a sequence-0 epoch. This checks composition and rejection, not a fresh allowed query under the real decision, production identity or policy, real trust claims, a Native artifact, or guest-side writes. Those remain open.
