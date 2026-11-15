# Linked receipt sink reference, 2026-11-14

`linked-execution-receipt-sink` validates an ExecutionReceipt, requires a literal true from the host's authority-receipt authentication callback, signs and verifies a versioned authority/execution link, then uses one canonical Kotobase commit and readback for the execution receipt, causal authority receipt and link. This is an opt-in sink; it is not connected to `governed-read`. The host is responsible for authenticating the authority receipt, its signer and evidence, and the execution receipt must already have been verified. It does not establish a trusted mutable head, cross-store or distributed atomicity.

Local macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5 reference: `authority_link_atomic_sink_probe.clj` with the leading `link-candidate` classpath described in `AUTHORITY-EXECUTION-LINK-20261114.md` and the revised `causal_commit.cljk` copied as `.cljc`. Fixture callbacks and in-memory backend were used. Output:

```clojure
{:linked true, :tampering :refused, :pass true}
{:atomic-commit :one, :records 3, :readback :verified, :failure-before-commit :verified, :pass true}
```

The test checks refusal without backend changes for denied host authentication, an invalid link signature and a tenant mismatch. It then reads the three records from the returned exact commit and verifies the link. SHA-256: causal commit `5e09e63a20709aca7f1ba886d2ec68964721ac25329aed91dd0e4310ebdb66b5`; probe `2bd164ca150fcdd212a158422189e49ffdffc780d0e3ea51ab295748ba70c1b4`. No dependency downloads. This is not Forest persistence, restart, Native, actual authority authentication or CI evidence.
