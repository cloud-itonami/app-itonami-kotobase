# Governed receipt pre-CAS readback, 2026-11-14

The `GOVERNED-PORTS-READ-20261114.md` fixture now constructs a read-only
backend over the persisted `publish-ports!` verifier port. Before ref CAS it
reads the candidate commit through `causal/receipt-at` and compares its
receipt and expected basis, in addition to verifying the signed bundle. The
verifier backend refuses writes and CAS. On macOS ARM, OpenJDK 21.0.1 /
Clojure 1.12.5, a new store successfully served three rows; a fresh JVM with
the writer absent recovered its published receipt and verified the signed
basis. The script SHA-256 is
`9af33e326c8bc74d5e00cb44f319cfaaf4462dcc4b7aa8218f2ca8ee2e8f86cd`.

In a separate store, the verifier's target read was deliberately withheld.
Publication failed with `verifier target missing`, and another process with
the writer absent verified that the receipt ref did not exist. An initial
version passed nil to the underlying decoder and failed with a null exception;
the final verifier converts missing blocks into explicit errors. All scratch
stores and outputs are retained.

The readback verifies the receipt and the blocks accessed by that operation;
it is not a general traversal of every possible commit reference. This remains
a hosted fixture with test identity, keys, clock and non-persistent nonce;
basis admission is outside `governed-read/read!`. It is not a product host
adapter, independent authority, two-Pack, crash/reboot, Native or production
qualification. Use the retained wrappers described in
`GOVERNED-PORTS-READ-20261114.md`, `GOVERNED_PORTS_LABEL` for a fresh store,
and `GOVERNED_MISSING_TARGET=1` for this negative control.
