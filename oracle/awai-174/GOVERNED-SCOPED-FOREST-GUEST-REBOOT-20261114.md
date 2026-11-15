# Scoped issuer attestation and Forest recovery, 2026-11-14

Candidate `6bb9212cb783240e9ad1d4ccfd9eb1253226a611`, continuing the dedicated guest store and two clean reboots in `GOVERNED-LINKED-AFTERBOOT-20261114.md`. The guest's persistent store manifest matched before work. The pinned Linux x86_64 Forest 0.39.0 tool, QEMU user 11.0.3 and observed-hash x86 runtime were re-fetched into tmpfs and checked against the hashes in `GOVERNED-LINKED-FOREST-GUEST-REBOOT-20261114.md`; the guest-only binfmt wrapper used QEMU guest base `0x800000000000`. The x86 runtime package downloads were not independently package-signature qualified. This is emulated hosted Otehon, not Native.

`governed_scoped_guest_forest.clj` SHA-256 `3af87d51ae62e920b1292d33085cbf027cb51db60fb9659d2e8823119af0bc0b` uses the same guest environment as the preceding report, with the new `authority-attestation` candidate directory first on the classpath. It commits a signed v1 authority-commit attestation to task-owned Otehon metadata, then uses the scoped issuer registry and Ed25519 fixture verifier to authenticate the recovered causal commit/receipt. A fresh nonce executes Datalog on the recovered Prolly basis and writes the three linked receipt records against the previous execution receipt commit:

```clojure
{:receipt "bafyreie5qvjexz4zjktaaqbrvzkgunegw53zy5wrdnytp4h253toup22ea", :new-nonce :accepted, :mode :advance, :issuer-scope :verified, :records 3, :rows 1, :previous :readable, :commit "bafyreih7wzubjn3uuzfvvxmjvxks3n4azkckccl2osp4se4mmmcm55kexu", :pass true}
```

The attestation metadata publication and linked receipt commit are separate operations, not one transaction. A fresh JVM with nonexistent writer and stage paths reloaded the persisted attestation, verified the registered issuer signature and authority commit, checked old/new linked receipts, and refused the nonce replay with root unchanged. Before reboot the four duplicate staged executables all matched the pinned tool hash. Unique staged CAR/Forest/log files were preserved as a tar SHA-256 `ac7f54f58733f269050267a64b32ea0de8913a1b81a14d7f85633b8938d7e0e8`; duplicate tool and downloaded runtime bytes were tmpfs and were lost on reboot, while hashes/origins were retained. The tar, staged-tool hashes, source/store manifests and outputs are retained under guest `scoped-evidence` and task scratch.

After `sync` and exit of Java/writer processes, a clean guest stop/start changed boot ID from `d7364221-4bf6-4e62-a422-f4138bb6797d` to `9e4b2427-41d4-40ac-86e4-c70169e75d63`. Persistent store and candidate source manifests matched. A fresh JVM with nonexistent writer/stage returned:

```clojure
{:mode :replay-after-advance, :issuer-scope :verified, :replay :refused, :root :unchanged, :old-and-new :verified, :pass true}
```

A separate read-only probe `scoped_guest_negative.clj` SHA-256 `0bf002dfbe87ffdaadb4985e6b0f49df0b0aa16bb0b35b096fcaeda197de6aa1` used that persisted attestation after reboot and rejected a different issuer even when re-signed, invalid signature, duplicate registry key, wrong tenant and wrong commit. Store hashes stayed unchanged.

The issuer identity, registry, keys, policy and clock are fixtures. The result does not establish actual production authority, registry/issuer provenance, freshness, anti-rollback, host OS cache loss, physical power interruption, independent nodes, Native or cross-store atomicity. The local receipt locator is a test fixture, not a trusted rollback witness.
