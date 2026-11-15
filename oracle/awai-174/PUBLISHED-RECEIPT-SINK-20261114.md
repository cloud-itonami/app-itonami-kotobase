# Explicit publication receipt boundary, 2026-11-14

`governed-read` now accepts an explicit publication adapter under its commit
options. The adapter receives canonical prepared receipt transaction data;
after it returns, the sink reads the published ref and the exact immutable
receipt before acknowledging durability. A stale ref or missing receipt
refuses the read. The legacy canonical sink remains the default.

On macOS ARM OpenJDK 21.0.1 and Clojure 1.12.5, the staged hosted JVM oracle
ran `kotobase.governed-read-test`: 7 tests, 59 assertions, zero failures and
zero errors. The new case uses a memory provider and a publication callback
that commits canonical transaction data; it checks the returned receipt,
rejects a stale ref and rejects a matching ref to a commit without the receipt.
The sources were copied from this change as `.cljc`; the test import was
adapted from `kotoba.test` to `clojure.test` and its exception class to the
JVM class. The classpath is the staged local oracle documented in
`QUERY-CYCLE-AND-FANOUT-20261114.md`, with
`.agent-output/publication-sink-oracle` first. No download was needed.

The host is trusted to implement semantic verification, persist all blocks
and perform the claimed atomic ref publication. This sink independently reads
the ref and exact receipt but cannot prove a dishonest host's transaction
semantics or prevent rollback. Nonce and receipt remain separate operations.
The test does not yet connect Otehon `publish-ports!`, exercise crash recovery,
qualify Native or establish production authority. Those integrations remain
required before treating this route as an end-to-end result.
