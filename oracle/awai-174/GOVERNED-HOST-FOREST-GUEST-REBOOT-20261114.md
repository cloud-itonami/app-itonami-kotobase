# Governed host entry with Forest and guest reboot, 2026-11-14

Candidate `6bbb6399f131f69f1a987754152e6f53954f7c95`. This continues the dedicated guest store, persisted scoped issuer attestation and prior receipt in `GOVERNED-SCOPED-FOREST-GUEST-REBOOT-20261114.md`. After verifying the prior store manifest, the pinned Forest tool and observed-hash QEMU/x86 runtime were re-fetched and checked against the hashes in `GOVERNED-LINKED-FOREST-GUEST-REBOOT-20261114.md`. The guest-only wrapper used QEMU guest base `0x800000000000`. The x86 runtime package downloads were not independently package-signature qualified; this is emulated hosted Otehon, not Native.

The guest fixture `governed_host_guest_forest.clj` SHA-256 `2f4d6e446aef61e151b9e97446e5893f1e54043e6b6eb4aeefca1c6cfacb102c` uses the same guest environment as the scoped report, with the new `governed-host` candidate classpath directory before `authority-attestation` and `link-candidate`. In `LINKED_MODE=advance`, `read-registry-signed-authority!` constructs the scoped verifier and exact causal commit authenticator from the saved attestation and fixture registry, then performs a fresh linked query over recovered Prolly and commits the receipt to guest-local Forest against the previous receipt commit:

```clojure
{:receipt "bafyreiaqljtrlu5ex3rul2qm6sxwvqcl765v44arcix7rzrnpxplp2wfla", :new-nonce :accepted, :mode :advance, :issuer-scope :verified, :records 3, :rows 1, :host-entry :verified, :previous :readable, :commit "bafyreihtjmvnxfoi655okm6cnqvrdqpdwz22orhwdo5fo74sewepe2ymnq", :pass true}
```

A fresh JVM using `LINKED_MODE=replay-advance` and nonexistent writer/staging paths refused the new nonce replay, authenticated the issuer/authority, verified the new receipt signature and link, checked old/new receipt records and their basis relation, and kept the root unchanged. Before reboot, all four staged tool hashes matched the pinned executable. Unique CAR/Forest/logs were preserved as a tar SHA-256 `23738b794e33c87de9c744f6205806372e97790d9ae0acd9b123b49515b2aaca`; duplicate tool and downloaded runtime bytes were tmpfs and were lost on reboot, with hashes/origins retained. Guest `host-evidence` and task scratch preserve the tar, hashes and manifests.

After `sync` and exit of Java/writer processes, clean guest stop/start changed boot ID from `9e4b2427-41d4-40ac-86e4-c70169e75d63` to `85a84328-7a22-4117-a14a-75523e922f25`. Guest store, host candidate, attestation candidate and previous source manifests matched before/after. A fresh JVM again used nonexistent tool/staging paths:

```clojure
{:mode :replay-after-advance, :host-entry :verified, :issuer-scope :verified, :replay :refused, :root :unchanged, :old-and-new :verified, :pass true}
```

A separate read-only `host_guest_negative.clj` SHA-256 `a91d478303aa534aa33efeabf8f00964055675da6f2720bf949d38289c595066` then rejected no issuer key, refused issuer, attempted verifier override and invalid signature before basis access; root and store hashes stayed unchanged. The first negative probe did not complete because its output map had an odd number of forms; the corrected probe passed. Both logs are retained in task scratch.

This tests the JVM library host entry, not an Awai Otehon product service: its current package pins only the Kotobase storage contract, while the governed host and bounded codec are unmerged draft dependencies. Registry, issuer, keys and policy are fixtures. Attestation and linked receipt are separate operations. No production identity/authority, rollback witness, Native, host OS cache loss, physical power failure, independent nodes, CI or cross-store atomicity is established.
