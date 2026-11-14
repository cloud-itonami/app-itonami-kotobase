# Fresh allowed governed query after guest reboot, 2026-11-15 JST

After the guest-written two-Pack fixture and clean reboot described in
`GOVERNED-TWOPACK-GUEST-WRITE-20261115.md`, a fresh hosted JVM used a new
nonce and the existing persisted frontier to call
`read-registry-persistent!`. Its scoped fixture Ed25519 registry, recovered
Prolly policy and actual Datalog query returned three rows. Product hosted
receipt callbacks and the Otehon publication adapter committed a new group
with two physical Packs to a **separate ref**; the original receipt CID was
unchanged. An empty registry and reuse of the new nonce were refused. Fresh
writer-free processes verified both the new receipt and the original receipt.
The source basis was created on the host and the location references two
synthetic 600 KiB blocks by CID strings.

The first attempt published a receipt but the fixture then failed because it
compared the original ref's entire `{cid, version}` map. Otehon's version is
the global store root generation, which changes when any ref changes. The
second attempt used another nonce and ref and compared the original CID;
both first-attempt data and staging were retained. This observation does not
claim independent ref versioning or an atomic transaction across refs.

The guest was then synced and cleanly stopped/started a second time; boot ID
changed from `e1c95797-8a7a-494f-9447-47c0ab010854` to
`f0f2e4eb-35d7-462e-865e-6969e6382043`. The first task-data read after this
boot was a writer-free fresh JVM which verified the new receipt, signed basis,
location and both block bytes; all 27 preboot store files matched their SHA-256
manifest. A subsequent writer-free process verified the original receipt.

Guest Linux aarch64 used OpenJDK 21.0.13.0.3 and the pinned x86_64 Forest
0.39.0 writer under QEMU 11.0.3. Re-downloaded packages were checked against
the same hashes and limits in `GOVERNED-TWOPACK-POSTBOOT-REPLAY-20261115.md`;
the task-owned binfmt entry was removed and the non-executable staging from
both attempts archived before reboot. Four staged tool copies matched the
pinned binary hash. The staging evidence SHA-256 was
`56c276b41ccc37de26c0da22bcf4f76255404ff26b638aa66dff88eb3ee0828a`;
the guest/host advance evidence archive was
`1d127884066d7eebb2a5489d5abf5452936a56d3d88b39319a2f68d5bf4e0896`,
and the second reboot evidence archive was
`65408458730368cbdef3c9d53694975bbd043ac67701393d05631397530311d1`.
The fixture SHA-256 was
`21c8445dd06e1e8cdc522aa44c1666b6d6364cf075a71b6c24bb02ada3ef0494`.
Guest evidence and the host copies under
`.agent-output/governed-twopack-guest-evidence-01` are retained.

This is a later warm query after the first reboot, not a first-read benchmark.
Registry, keys, principal, authority, clock and TOFU frontier are fixtures.
The new receipt is not a causal continuation of the old one; nonce and
receipt remain separate transactions. This is emulated hosted execution, not
Native, GiB, independent-node, host/device cache loss, physical power-loss or
production qualification.
