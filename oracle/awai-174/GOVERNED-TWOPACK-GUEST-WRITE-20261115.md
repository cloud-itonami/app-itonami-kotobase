# Guest-written governed two-Pack recovery, 2026-11-15 JST

On the dedicated Linux aarch64 guest, a hosted JVM ran
`governed_ports_guest_write.clj` against the previously host-created signed
Prolly basis. The actual `read-registry-persistent!`, fixture-scoped Ed25519
registry, guest-persisted frontier and nonce, recovered policy/Datalog,
Kotobase hosted receipt callbacks and Otehon publication adapter produced a
new guest-local receipt group with exactly **two Forest Packs**. The query
returned three rows; empty registry and repeated nonce were refused. The
location record contains two synthetic 600 KiB blocks by CID string, and the
pre-CAS verifier read their bytes and the receipt. The writer was the pinned
Linux x86_64 Forest 0.39.0 tool executed via QEMU user on aarch64: this is
hosted emulation, not Native.

After a writer-free baseline read, the guest was synced, stopped and started.
Boot ID changed from `9c1e7f3f-956a-417c-bc3b-b7ca6acb62fd` to
`e1c95797-8a7a-494f-9447-47c0ab010854`. The first task-data read after boot
used a fresh JVM with the writer absent and verified the receipt, signed
basis, location and both block bytes; all 15 original guest-written store
files matched their preboot SHA-256 manifest. In a separate copied store, one
large Pack was withheld and recovery refused with `:missing-forest`. The
withheld archive SHA-256 was
`7b50de388e824f6f35a4cf1cd76b8db75ca5daa624f4697a99203db8d21c8b61`.

Staging used task-owned guest tmpfs because the guest disk had 374 MiB free.
Before reboot, all 12 non-executable staging files were archived; three
duplicate staged writer executables each matched pinned SHA-256
`7af03845db7324da80c5bc2848fe6897303b39cbac8bb701675e57ad82033ba0`.
The non-executable staging archive SHA-256 was
`d34abbe0dacc50879b49cd48279aacf7c3ce32aab53933b9467551cca50c7771`.
The task-owned binfmt registration was removed before reboot. An initial
re-registration attempt failed with an I/O error; the validated magic/mask
were registered under a new task name and the temporary diagnostic
registration was also removed. Tmpfs artifacts were lost on reboot after the
above evidence was retained.

Guest/host evidence archive SHA-256:
`5f56c41d48cfd11e17a36934910355f3a98f9818336f90b4cd9c2208291a3656`.
Script SHA-256:
`71f1f43c8a3a7252963d6eec7bdca75121bf573e50270bab02d9316dc8f6260f`.
Artifacts remain under the guest's `~/awai-native-forest/governed-twopack-reboot-01`
and the host worktree's `.agent-output/governed-twopack-guest-evidence-01`.
The pinned download and QEMU/runtime hashes and their provenance limits are
recorded in `GOVERNED-TWOPACK-POSTBOOT-REPLAY-20261115.md`.

The source basis was host-created; issuer, keys, trust, time and initial
frontier are fixtures. Postboot recovery here did not execute a fresh query
or nonce consumer. The block references are fixture CID strings rather than
standard IPLD links. Guest page-cache loss does not establish host/device
cache loss or physical power failure. It does not qualify GiB scale, Native,
independent-node durability, arbitrary DAG closure or production security.
Nonce and receipt remain separate operations.
