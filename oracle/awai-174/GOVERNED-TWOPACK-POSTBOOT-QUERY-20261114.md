# Datalog query after governed two-Pack guest reboot, 2026-11-14

Following `GOVERNED-TWOPACK-GUEST-REBOOT-20261114.md`, a fresh JVM in the
same Linux guest, with the writer path absent, reopened the saved signed
Prolly basis and persisted policy. The actual Datalog query returned three
rows with the fixture Dango capability and zero without it; the fixture also
rejected changed signature and policy. Output:

```clojure
{:mode :read, :signed-basis :verified, :policy :recovered-and-bound, :allowed 3, :no-capability 0, :tamper :refused, :pass true}
```

This query ran **after** the postboot receipt/attachment read and is not the
first task-data read or a cold-query performance measurement. The regular
input manifest was rechecked after the query: all 52 files matched. Output
SHA-256 was `2eb8b46d526bdf34b1b070c5cf4d9df09a2fa767b337215c105fd53d7537847f`.
Guest and host retained evidence archive SHA-256 was
`004d0cc1a9166987d55e457720216082c954cf64fccf266e9a9ce9bb76efe48c`;
no download occurred.

This is a direct query of the recovered source basis after receipt recovery,
not a new `governed-read` or nonce-consumer execution. No writer was available
in the guest. Identity, registry, keys, time and Dango capability are test
fixtures. Host cache loss, guest writes, physical power loss, Native, GiB and
production qualification remain unproven.
