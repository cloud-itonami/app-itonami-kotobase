# Registry and nonce replay after guest reboot, 2026-11-14/15 JST

After the clean guest reboot and the prior receipt and warm Datalog reads in
`GOVERNED-TWOPACK-GUEST-REBOOT-20261114.md` and
`GOVERNED-TWOPACK-POSTBOOT-QUERY-20261114.md`, a fresh JVM invoked the actual
`read-registry-persistent!` and nonce consumer against the saved two-Pack
dataset. The scoped fixture registry admitted the signed basis; the previously
consumed nonce was refused before policy authorization, evaluation or receipt
publication. The receipt ref was unchanged. The 52 preexisting input files
still matched their prior manifest; a file listing found no additional files
inside the source and receipt stores, and the receipt staging directory was
empty. This is a later warm operation, not the first postboot read.

The guest was Linux aarch64 7.3.0-9, OpenJDK 21.0.13.0.3, same postboot ID
`9c1e7f3f-956a-417c-bc3b-b7ca6acb62fd`. The required Forest 0.39.0 Linux
x86_64 writer was available through QEMU user 11.0.3 with guest base
`0x800000000000`. Packages were extracted to guest tmpfs without installation;
the task-owned binfmt registration was removed after the run. The downloaded
Forest zip was 89.31 MiB, QEMU package 17.9 MB, libc package 2.03 MiB and
libgcc package 80,298 bytes. Python 3's standard zipfile module extracted the
release executable because unzip was absent; it is not part of the storage or
Native execution path.

The verified SHA-256 values matched the existing pinned or previously
observed evidence: Forest zip
`81224a9efed00353ec3c6cc5197f26abfdf48ed9458c1ce72b612e825d5bb64a`,
Forest binary `7af03845db7324da80c5bc2848fe6897303b39cbac8bb701675e57ad82033ba0`,
QEMU package `2b6aef95b13143cb1a44a95c7e275cccd85fee3d809b95a37a4238f7a31b24cd`,
QEMU binary `66497b6efd488ba2fe79655a17b6180f959b99f04b257468a379d327a7f7b3bd`,
libc package `1db945b9558d118d2507c48b355b99336e696730fd33b6b3b9d95ffb366e84ff`,
and libgcc package `6d0b3779d578ffc598c26678ea26228b20880044742f44dcd78b2556fdc97d07`.
The x86 runtime packages were checked against earlier observed hashes, not an
independent package-signature qualification. Replay script SHA-256
`4227f9e20f4ae74ef5ae3fd05deefba95e5324c4a91878de153b7994f76e05e6`;
guest/host evidence archive SHA-256
`8efb304973d97182945e458263346ea133c311c76c053a1482c83b43a2af5101`.
Guest source, task data and evidence remain; the host copy is under
`.agent-output/governed-twopack-guest-evidence-01`.

The fixture keys, registry, identity, time, TOFU frontier and host-written
source remain untrusted production substitutes. Emulated hosted execution is
not Native, and guest cache loss does not imply host/device cache loss or
physical power interruption. This does not establish a fresh allowed query,
guest-side creation of the two-Pack group, GiB scale, independent-node
durability or production qualification. Nonce and receipt remain separate
transactions.
