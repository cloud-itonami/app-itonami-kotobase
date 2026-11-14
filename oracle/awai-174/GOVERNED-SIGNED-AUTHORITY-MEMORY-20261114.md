# Signed causal authority commit readback, 2026-11-14

The synchronous JVM `decision-receipt-authenticator` adapter takes an exact immutable decision commit CID and a host verifier. It requires the host to authenticate that CID and request context, then reads the named causal decision from the commit and checks the stream, prior basis and entire receipt. The host must independently establish issuer/key authorization, policy, freshness and rollback protection; the adapter does not supply those guarantees.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, `governed_signed_authority_memory.clj` ran using the recovered Forest/Prolly basis and the classpath/environment described in `GOVERNED-LINKED-REGISTRY-MEMORY-20261114.md`, with modified `causal_commit.cljk` copied as `.cljc` into the leading candidate directory. A test issuer signed a domain-separated attestation containing the decision commit CID, tenant and epoch using Ed25519. The real causal-trust decision was persisted in an in-memory Kotobase decision ledger. The verifier checked the fixture public key and scope; readback returned the exact trust receipt. Wrong signing key, wrong commit, missing receipt and an altered receipt were refused. The linked allowed and query-policy denied paths then passed, including nonce replay and signed-link checks.

```clojure
{:authority :signed-commit-readback, :replay :refused, :records 3, :rows 1, :linked-read :allowed, :authority-refusal :before-basis, :pass true, :backend :memory, :tamper :withheld}
{:query-policy :denied, :evaluator :not-called, :linked-records 3, :readback :verified, :replay :refused, :backend :memory, :pass true}
```

The local JVM governed execution/read regression passed 21 tests / 164 assertions / 0 failures / 0 errors. SHA-256: causal commit `8ce763068d5d0082ad3c9b3ffcbf3407d1f4efd78a55a5eaf3eed78f558c00ae`; probe `365df6e193b46fce5fb57f87caf78e0dd98178628d1748bc00361809b93b0eab`. No dependency downloads or Forest writes. Identity, key and scope are fixtures; authority ledger, nonce and receipt are in memory. Not production authority, anti-rollback, Native, async, restart or CI evidence.
