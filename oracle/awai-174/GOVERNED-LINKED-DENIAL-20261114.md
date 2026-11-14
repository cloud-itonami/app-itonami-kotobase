# Linked query-policy denial, 2026-11-14

After `GOVERNED-LINKED-REGISTRY-MEMORY-20261114.md`, linked execution now supports a query policy denial after a causal trust allow. The trust receipt remains an allow; the distinct ExecutionReceipt is a deny with no result root. The signed link binds both. The canonical sink commits all three and reads them back before reporting denial; the exception exposes both receipt CID and exact commit CID. A trust refusal before this entry is not covered. No cross-store atomicity is claimed.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, the updated `governed_linked_registry_memory.clj` ran with the environment and classpath documented in `GOVERNED-LINKED-REGISTRY-MEMORY-20261114.md`; the modified `.cljk` sources were copied to the leading `.cljc` candidate directory. The fixture uses actual trust decision logic, recovered Forest/Prolly, fixture identity and host authentication, and in-memory nonce/receipt backends. No download or Forest writes occurred. Result:

```clojure
{:linked-read :allowed, :rows 1, :records 3, :tamper :withheld, :authority-refusal :before-basis, :replay :refused, :backend :memory, :pass true}
{:query-policy :denied, :evaluator :not-called, :linked-records 3, :readback :verified, :replay :refused, :backend :memory, :pass true}
```

The two local JVM reference namespaces `kotobase.governed-execution-test` and `kotobase.governed-read-test` passed 21 tests / 164 assertions / 0 failures / 0 errors using the same leading candidate classpath. This is not Native, CI, real host authority, persistent receipt, process restart or physical power-loss evidence.

SHA-256: link `2ac5da2ed3e9be848e34d9d9f0250efa45918a5bdba082d31104db70324409b1`; governed execution `1dd1cb2b0013a3afe37ccd2b9ef5c59f1733c3af4f50f25b1515f7ec265e4062`; governed read `39db569d546f5bd230f9d43a2a63c35f1d4ba08faa5077e00ed053a4145e6972`; probe `f9e367f22e66ac798f3deaaefe5af3dc122ea4b3aae563dd8542dec0e32fed0b`.
