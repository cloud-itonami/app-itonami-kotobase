# New linked read after guest recovery, 2026-11-14

Following `GOVERNED-LINKED-FOREST-GUEST-REBOOT-20261114.md`, the guest-local Forest store and candidate source hashes were rechecked after the first clean guest reboot. The pinned Linux x86_64 Forest archive/tool and observed-hash QEMU/x86 runtime were downloaded again into guest tmpfs, checked against the preceding report's hashes, and the guest-only QEMU binfmt wrapper with `-B 0x800000000000` re-registered. No additional claim of independent runtime package-signature qualification is made.

`governed_linked_afterboot.clj` SHA-256 `35d713def3098400a769c322a2c1613df62bf3bc4517d0e858ff00a6e48db527` uses the same guest environment and classpath as the preceding report, with `LINKED_MODE=advance`, an existing `BOUND_RECEIPT_OUTPUT`, the tmpfs writer/staging paths, and a fresh nonce. It reads the already persisted authority receipt/commit, executes fixture-policy Datalog over the recovered Prolly basis, and commits new signed linked evidence against the previous execution receipt commit. It reads back both receipt commits and verifies the new link.

```clojure
{:mode :advance, :new-nonce :accepted, :rows 1, :records 3, :previous :readable, :receipt "bafyreifjaeksz7jlj62ii5kd7tcfae5l6wa2uptg2jjpyq3mqx6vde7cke", :commit "bafyreibz6dpvzkrwbhspzk4s22gfi3bx6bu2srcg64gfnfhul4dl5da7tq", :pass true}
```

A fresh JVM using `LINKED_MODE=replay-advance` and nonexistent tool/staging paths refused reuse of the new nonce, verified the signed authority, new receipt signature and link, checked the old and new receipt records and their commit-basis relation, and confirmed the root did not change. Four duplicate staged tools matched the previously pinned hash; unique staged CAR/Forest/log files were copied before reboot to a tar with SHA-256 `39dd1e05f5f40906a5704a736ee261043d34c31e7f72b7bab5d112d9b6df040a`. The tar, tool hash list, source/store manifests and output are retained in guest `advance-evidence` and task scratch. Duplicate tool/package bytes lived only in tmpfs and were lost on reboot; their hashes and origins remain recorded.

After `sync` and termination of all Java and writer processes, the dedicated guest was cleanly stopped and restarted. Boot ID changed from `dad22381-3441-44ea-9824-61ce48863b97` to `d7364221-4bf6-4e62-a422-f4138bb6797d`. Store and candidate source hashes matched before and after the second reboot. A fresh JVM again used nonexistent tool/staging paths and returned:

```clojure
{:mode :replay-after-advance, :replay :refused, :root :unchanged, :old-and-new :verified, :pass true}
```

This demonstrates a new allowed fixture read and durable linked receipt written after guest recovery, followed by another clean guest OS reboot and readback. It does not prove production identity or authority, host OS cache loss, physical power interruption, Native execution, independent nodes or cross-store transactions. The local store and test locator files do not supply a trusted rollback witness.
