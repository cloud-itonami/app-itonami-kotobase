# Fresh-process registry and nonce replay, 2026-11-14

Following `GOVERNED-PORTS-REGISTRY-20261114.md`, a new JVM opened the saved
Forest store, recovered the receipt, request and manifest, verified the
receipt signature and basis, and called the actual
`read-registry-persistent!` entry with the same nonce. Registry-bound basis
admission ran; the nonce consumer returned `:nonce-replayed` before policy
authorization, evaluation or receipt publication. The published receipt ref
was unchanged. This run used the real writer executable: the nonce adapter
can put an immutable marker before a rejected CAS, so unchanged ref is not a
claim that no storage bytes or orphan groups changed.

The host was macOS ARM, OpenJDK 21.0.1 / Clojure 1.12.5; no download was
needed. The script SHA-256 is
`0bca3f4c5842eb5668e71a2f3e48183b44bd4b42c7f443f8f858ca6df4c431ea`.
The retained scratch wrapper `run-governed-registry-replay.sh` uses the same
classpath and store as the prior report. Output was
`{:fresh-process true, :registry-admission :verified, :nonce-replay :refused, :receipt-ref :unchanged, :pass true}`.

The public key locator, registry, issuer, principal, authority decision,
clock and initial frontier remain fixtures. There was no process-kill or OS
reboot in this run, no cache-loss, Native, independent trust root, general
closure or production qualification. Receipt and nonce are separate operations.
