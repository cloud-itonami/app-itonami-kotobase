# Governed host source test, 2026-11-14

Added `test/kotobase/governed_host_test.cljk` SHA-256 `83dae3f570e4e361ef13a146a6ff114d2c173a5dfc1c35443b6441c8151420c7`. It uses an in-memory causal decision commit, signed fixture attestation, saved frontier, scoped nonce and canonical linked sink. It checks allowed read and linked records, query-policy denial and linked denial records, replay refusal, and missing issuer key, verifier override and mismatched decision rejection before basis fetch or mutation.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, the test was copied as `.cljc` with `kotoba.test` replaced by `clojure.test` in task scratch. Alongside authority attestation, governed execution/read and execution keys reference tests, it passed 29 tests / 213 assertions / 0 failures. The classpath is the staged candidate and dependency overlay from `IPLD-CID-LENGTH-PIN-20261114.md`, with the staged test directory first. The first attempt failed because an older request fixture used a constant receipt CID; the new test explicitly uses the canonical CID function and then passed.

The test uses fixture signatures and in-memory stores; it does not replace the separate Ed25519/Forest guest evidence or prove a production service, clean dependency resolution, Native, restart or CI qualification. No dependencies were downloaded.
