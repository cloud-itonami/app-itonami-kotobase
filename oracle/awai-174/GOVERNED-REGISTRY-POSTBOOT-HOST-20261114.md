# Fresh governed fixture read after guest reboot, 2026-11-14

Following `GOVERNED-REGISTRY-GUEST-REBOOT-20261114.md`, copied the rebooted guest's `data` directory to a separate host store and checked it against the pre-reboot `data.sha` (manifest SHA-256 `f7021ee46978bce355827fe2578594bd19ba67f838f88fc9b287457eee80d877`). All entries passed. No guest write was performed. Local macOS ARM, OpenJDK 21.0.1, Clojure 1.12.5; Forest 0.39.0 tool SHA-256 `e5fb23076a46c055142b3dbf3efa1ad5a68532d14419ad9994785986719b205f`.

The host used the previously copied sources and jars described in the guest reboot report. From this worktree, `root="$PWD/.agent-output/key-registry-oracle/guest-readback"` and `data="$PWD/.agent-output/key-registry-oracle/postboot-host/data"`; set `SIGNED_BASIS_OUTPUT=$data/basis`, `GROUP_FOREST_STORE=$data/basis/store`, `FRONTIER_FOREST_OUTPUT=$data/frontier`, `BOUND_RECEIPT_OUTPUT=$data/receipt`, and `FOREST_TOOL` to the pinned macOS tool. Run from `$root/app` using `java -cp "$root/jars/*:$root/cp0:$root/cp1:$root/cp2:$root/cp3:$root/cp4:$root/cp5:$root/cp6:$root/cp7:$root/app/nixos/otehon/storage/src:$root/app/nixos/otehon/storage/vendor/apex/src:$root/app/nixos/otehon/storage/vendor/apex-car/src" clojure.main` followed by the absolute path to each fixture below, in separate JVMs.

The first fixture `governed_registry_postboot_host.clj` (SHA-256 `b0b04601a7c556c04fdfbdb57181ffb62806226d114fcaca303eef481002cf07`) admitted a fresh nonce, ran fixture-policy Datalog on the restored Prolly snapshot and wrote a causal receipt based on the previous receipt commit:

```clojure
{:events [:basis :authorize :evaluate :commit], :rows 1, :receipt "bafyreibymgzopqwpv3e7kyzp72jf2mmg6ejf7bxeleuunenlw37b36a674", :commit "bafyreieqpanxx4uqfomwb36lyla7chsg4iszjgmfhd25iccc7x3gqoapey", :pass true}
```

The second fixture `governed_registry_postboot_host_replay.clj` (SHA-256 `9e0054184b305949e55c17486db5900bc05c09b4fd9fe05bdb178c0ca156d2eb`) ran in a fresh JVM and verified receipt CID and test-key signature, rejected the same nonce before policy or evaluation, and left the root unchanged:

```clojure
{:replay :refused, :events [:basis], :root :unchanged, :receipt "bafyreibymgzopqwpv3e7kyzp72jf2mmg6ejf7bxeleuunenlw37b36a674", :signature :valid, :pass true}
```

This is hosted reference evidence with fixture keys, identity, registry, policy and clock. The successful post-reboot write happened on the host after copying the guest data, not in the guest; host cache loss, physical power failure, Native, production authority, cross-store atomicity and independent nodes remain unproven. Scratch stores and stages are retained. Available host disk fell to approximately 283 MiB, so further large tests require safe capacity recovery.
