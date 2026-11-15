# Governed two-Pack read after guest reboot, 2026-11-14

The host-created `GOVERNED-PORTS-TWOPACK-20261114.md` fixture, including its
two-Pack receipt group and source signed-basis store, was copied to a new
task-owned directory in the existing dedicated Lima VZ Linux aarch64 guest.
After a successful baseline read, `sync` and clean guest stop/start changed
boot ID from `8cf85d55-b78a-4c0a-9b44-d4e121277060` to
`9c1e7f3f-956a-417c-bc3b-b7ca6acb62fd`. The first task-data read after boot
was a fresh JVM with a nonexistent writer: it verified the receipt, signed
basis, signed location and both synthetic 600 KiB block bytes. A subsequent
regular-file manifest check verified all 52 entries. In a separate copied
store, withholding one large archive failed with `:missing-forest`; the
withheld archive SHA-256 was
`00d5665a2d8ae517bd4c58a5dc2cc9d12b151beb5afdfb74c22c3d694a97a982`.

The transfer archive SHA-256 was
`e720d3c3da9c30758f3737bfe46976cd4b3c32edd22444f36be0963cec0d1cfa`;
the guest and host evidence archive SHA-256 was
`ca99e1b5f93aef4600e60043cd2b1509e11977036906aece55c2cd7120fed7d2`.
The guest input, outputs, negative copy and archive remain under
`~/awai-native-forest/governed-twopack-reboot-01`; a copy of the evidence is
under this worktree's `.agent-output/governed-twopack-guest-evidence-01`.
Linux tar reported macOS extended-attribute warnings; regular file checks
passed. Guest Linux 7.3.0-9-generic, OpenJDK 21.0.13.0.3; no downloads.

This establishes loss of the guest OS page cache for this host-written,
hosted fixture. The host and device caches were not controlled. The postboot
read did not run the writer, registry admission, nonce consumer or Datalog;
it verifies their saved output. Attachments are synthetic and referred to by
CID strings, not standard IPLD links. Test identities, keys, clock and TOFU
frontier remain fixtures. This is not GiB, Native, independent-node,
production or physical power-loss evidence.
