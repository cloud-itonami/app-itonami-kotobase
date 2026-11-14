# Hosted fixture recovery after guest reboot, 2026-11-14

Candidate: `9680b0f110046f24524d4934195c041a1b40dc1a`. Local, read-only Otehon/JVM reference experiment; not a Native gate or production qualification. The host-created signed basis, Prolly/Forest data, frontier, nonce and receipt fixture were copied to a dedicated Lima Ubuntu ARM guest before a clean stop and start. Test keys, policy, principal and trust roots are fixtures. Guest boot IDs changed from `02f7b42e-ea32-425a-afe1-c8734a8ee9f2` to `68a68574-fa14-4fed-98cd-bb5ad1172371`. OpenJDK 21.0.13.0.3 and Clojure 1.12.5 ran in the guest. Guest package installation downloaded approximately 47 MB for the JRE and 23 MB for apt indexes, consuming approximately 207 MB and 210 MB respectively.

The copied data manifest SHA-256 was `f7021ee46978bce355827fe2578594bd19ba67f838f88fc9b287457eee80d877`; candidate source/jar manifest SHA-256 was `440c7fff4d57b2c912504a102357b299b90ddabe139c7cc785f92398965a35ec`. The host copies, complete manifests and outputs remain under the task worktree's `.agent-output/key-registry-oracle/guest-readback`; manifests were checked in the guest before and after reboot. Other reference overlay sources were copied as fixtures but are not covered by the candidate manifest. The guest writer tool was deliberately absent: `FOREST_TOOL` points to a nonexistent path, and this run performs no Forest writes.

After reboot, the following was run in the guest, with `root` naming the copied fixture directory:

```sh
cd "$root/app"
export SIGNED_BASIS_OUTPUT="$root/data/basis" GROUP_FOREST_STORE="$root/data/basis/store"
export FRONTIER_FOREST_OUTPUT="$root/data/frontier" BOUND_RECEIPT_OUTPUT="$root/data/receipt"
export FOREST_TOOL="$root/nonexistent-forest-tool"
cp="$root/jars/*:$root/cp0:$root/cp1:$root/cp2:$root/cp3:$root/cp4:$root/cp5:$root/cp6:$root/cp7:$root/app/nixos/otehon/storage/src:$root/app/nixos/otehon/storage/vendor/apex/src:$root/app/nixos/otehon/storage/vendor/apex-car/src"
java -cp "$cp" clojure.main recovery-query.clj
cd "$root" && sha256sum -c data.sha
```

`recovery-query.clj` is `governed_registry_guest_recovery.clj` (SHA-256 `7cb45f808106cc6035296b09789f274545f76bd6b90f9c251915c816f996f561`). Output:

```clojure
{:replay :refused, :events [:basis], :root :unchanged, :receipt "bafyreifakvfs3qgb4htpqlhrbui6h4r3uqz2dylfgzn6tqsuugn3yxifva", :fresh-process true, :pass true}
{:restored-prolly :ok, :datalog :ok, :policy-allowed 1, :policy-denied 0, :pass true}
```

The first result is a governed-entry replay rejection with verification of the saved receipt; the second is a separate direct fixture-policy-filtered Datalog query on the restored Prolly snapshot. It is not a new allowed governed-entry execution. Data manifest verification passed after the query. This demonstrates a clean guest OS reboot and its guest page-cache loss, not loss of the host cache, a physical power cut, guest-side persistence, real policy/receipts, Native Forest I/O, or distributed durability.
