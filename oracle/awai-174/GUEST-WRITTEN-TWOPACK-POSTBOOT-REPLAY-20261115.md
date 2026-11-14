# Guest-written two-Pack nonce replay after reboot, 2026-11-15 JST

Following `GOVERNED-TWOPACK-GUEST-WRITE-20261115.md`, a fresh hosted JVM in
the rebooted Linux guest reopened the **guest-written** two-Pack receipt
store and called the actual `read-registry-persistent!` with its original
nonce. The scoped fixture registry admitted the signed basis; the nonce was
refused as `:nonce-replayed` before policy authorization, evaluation or
receipt publication. The receipt ref was unchanged. The Forest writer path
was deliberately nonexistent: the already-persisted nonce block was reused
without starting a writer. All 15 original store files still matched their
preboot hashes and the store file listing had no added files.

The run was after the first postboot receipt read and missing-Pack control,
so it is a warm follow-up rather than the first read or a latency benchmark.
It used guest Linux 7.3.0-9 aarch64 and OpenJDK 21.0.13.0.3, with no download.
The replay script is `governed_ports_twopack_replay.clj`, SHA-256
`4227f9e20f4ae74ef5ae3fd05deefba95e5324c4a91878de153b7994f76e05e6`.
Guest/host evidence archive SHA-256
`9e120e55d6858815ea7d715c96c330e6ee99d4606f2d09672326763075a5e91d`;
the host copy is in `.agent-output/governed-twopack-guest-evidence-01`.

The source basis was host-created and identity, registry, keys, time and
initial frontier are fixtures. This does not show a fresh allowed postboot
query, Native execution, GiB scale, independent nodes, production trust,
host/device cache loss or a physical power interruption. Nonce consumption
and receipt publication are separate transactions.
