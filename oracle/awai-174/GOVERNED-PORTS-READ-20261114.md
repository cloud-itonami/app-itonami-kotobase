# Governed read to Forest ports, 2026-11-14

On macOS ARM, OpenJDK 21.0.1 / Clojure 1.12.5, the hosted fixture invokes
`governed-read/read!` with its new canonical publication sink and actual
Otehon `publish-ports!`. A signed fixture basis and persisted policy are
recovered from the existing Forest/Prolly dataset; an actual Datalog query
returns three rows. The publication callback uses peer `commit!` with
provisional ports, writes the request and signed manifest, verifies their
bundle before ref CAS, and returns the commit only after publication. The
governed sink then independently checks the ref and reads back the receipt.
A fresh JVM with the Forest writer executable absent recovered the ref and
receipt and verified its signature, request, manifest and signed basis.

In a separate new store the semantic verifier was forced to reject. The
process failed with `semantic verifier refused publication`; a fresh process
with the writer absent found no `governed-receipt` ref. The initial log check
incorrectly searched stdout for `:closure-rejected`, which Clojure had written
only to its error report; checking the actual message and absent ref passed.
Both 65 MiB task stores and their staging artifacts remain in scratch.

This is a local hosted composition, not Native, OS-reboot or production
qualification. Principal, capability, keys, clock and nonce are fixtures; the
nonce callback returns true and is not persisted. Basis admission is explicitly
called before `read!`; the registry-persistent governed entry has not been
connected here. The pre-CAS semantic callback checks the external signed bundle
but does not independently traverse the complete receipt commit closure; the
sink verifies the receipt only after publication. Thus this is not proof that
all semantic corruption is refused before publishing the ref. The sidecar
containing receipt IDs and the test public key is not an authenticated
anti-rollback witness. No two-Pack, GiB or crash-atomic claim follows from
this fixture.

Fixture SHA-256 `c5df9df8c241188481f2b16e1bbf61851a4902aa9807d354b0046c4ecd67e146`.
Its environment and base classpath follow `SIGNED-POLICY-PORTS-RECEIPT-20261114.md`,
with `.agent-output/publication-sink-oracle` first for candidate 625a5d3.
The retained wrappers are `.agent-output/query-cycle-oracle/run-governed-ports.sh`
and `run-governed-ports-readonly.sh`; `KOTOBASE_ORACLE_ROOT` names this
worktree. `GOVERNED_PORTS_LABEL` selects a fresh output directory and
`GOVERNED_REJECT=1` exercises the negative control. No download occurred.
