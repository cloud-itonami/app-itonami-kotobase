# Registry-bound governed receipt publication across two Packs, 2026-11-14

The hosted product callback / Otehon adapter fixture extends
`HOSTED-RECEIPT-PUBLICATION-20261114.md`. Its signed manifest names a
content-addressed location record containing the string CIDs of two seeded,
incompressible 600 KiB raw fixture blocks. The location record, blocks,
request, manifest and canonical receipt commit are staged in one publication.
Before CAS the host verifier checks the signed bundle, location record, both
block bytes and the receipt commit read path. The new receipt group was
inspected and contains exactly **two physical Forest archives**. The normal
registry-persistent read returned three Datalog rows; an unregistered key
and the repeated nonce were refused. A new JVM with the writer absent verified
the receipt, signed basis, location and both original block bytes.

In a separate copied store, one large Forest archive was withheld. A fresh
writer-free process refused recovery with `:missing-forest`; the original
store and the withheld archive were retained. This was macOS ARM, OpenJDK
21.0.1 / Clojure 1.12.5, with no downloads. Fixture SHA-256:
`5ffda655a178bf377f03cb614486fdb9c21779a8fdd118ec483f575432075490`.
The retained `run-governed-twopack.sh` and
`run-governed-twopack-readonly.sh` wrappers use the classpath in
`HOSTED-RECEIPT-PUBLICATION-20261114.md`; `GOVERNED_PORTS_LABEL` selects a
fresh task store. The original 198 MiB task directory and the negative copy are
preserved.

These attachments are synthetic fixture data referenced as CID **strings**
by a host-interpreted location record, not standard IPLD link objects or real
user files. The test checks exactly this location/receipt path, not all DAG
closure. Registry, identity, signing keys, policy configuration and time are
fixtures; the initial frontier is TOFU. It is not a GiB, crash/OS-reboot,
Native, production trust or physical power-loss result. Nonce and receipt
remain separate transactions.
