# Linked governed read reference, 2026-11-14

`read-registry-linked-persistent!` adds an explicit linked path. It validates the authority receipt shape, requires the exact trust decision exercised by the request and a literal true from the host authenticator before basis admission, then rechecks authentication and uses the atomic linked receipt sink before returning rows. The receipt CID function must agree with the request's value CID function for every value used by the link. Authentication, issuer, trust evidence, key custody and durable backing are host obligations. A link or later write failure can leave the nonce consumed. This path is currently limited to successfully allowed reads; it does not establish denial-receipt linkage, an async CLJS entry, or cross-store atomicity.

On macOS ARM OpenJDK 21.0.1 and Clojure 1.12.5, `governed_linked_registry_memory.clj` used the recovered Forest/Prolly basis and prior task-local fixture dependencies, with changed governed-read, causal-commit, execution-keys and authority-execution-link sources copied as `.cljc` into the leading `link-candidate` classpath directory described by `AUTHORITY-EXECUTION-LINK-20261114.md`. Environment and rest of classpath match `GOVERNED-REGISTRY-POSTBOOT-HOST-20261114.md`. The actual causal-trust algorithm ran with fixture inputs; authority authentication was a fixture callback and nonce/receipt backend was in memory.

A successful read returned one expected row and one commit containing three records, with link verified on readback. Changed decision and rejected authentication stopped before basis; changing the authentication answer on its second call and a bad link signature both withheld rows; a consumed nonce replay was refused without changing memory state. Output:

```clojure
{:linked-read :allowed, :rows 1, :records 3, :tamper :withheld, :authority-refusal :before-basis, :replay :refused, :backend :memory, :pass true}
```

The first expanded probe failed because it asserted an incorrect exception key; it passed after correcting the assertion and again after moving authority receipt validation before basis admission. SHA-256: governed read `2508c25e01ecb8b06efd0651287cda7f61075b67f227215cd21ffcb16804abdf`, fixture `096665d0a87eea2a25034cd334d0de941e0ec860ea1fca5453697f608c6d8cea`. No dependency downloads. This is not real host authority, persistent nonce/receipt, restart, Native or CI evidence.
