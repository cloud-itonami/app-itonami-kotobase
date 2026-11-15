# Key registry ambiguity reference check, 2026-11-14

In revision `877faeaa720073645f5e065f10b2e034a82cd4cc`, a host registry returning two entries with the same key ID caused `into {}` to choose the last entry. A local reference probe returned `true` and invoked the injected verifier with material `B` for `[A B]`, and with material `A` for `[B A]`. This does not imply that an attacker can control a production registry.

The candidate rejects repeated key IDs in the requested role/tenant/epoch scope before calling the injected cryptographic verifier. On Babashka 1.12.218, four duplicate controls (both orders, identical entries and a duplicate among unrelated keys) returned `:duplicate-key-id` with no verifier call; a unique-key control reached the verifier. The same assertion script failed against the baseline. This is a local reference run, not Native, CI, a real key registry or production cryptographic qualification.

The repository `kotobase.execution-keys-test` namespace was also run on Clojure CLI 1.12.5.1654 using local reference classpaths and a `.cljc` test copy adapted from `kotoba.test` to `clojure.test` and the host exception class. Candidate: 6 tests / 29 assertions / 0 failures / 0 errors. The same tests with the baseline source produced 6 failures in the new duplicate-key test and 0 errors. Candidate source was copied unchanged. Adapted test SHA-256 `06c0ac9ec7b277cb2c8cfdcbf0956533aabc0a4f13efd8b440c4898350b0bba7`; existing adapted governed-execution fixture SHA-256 `df31cd92c39b0a27a549366ab7bc057a94a4c881192f9b21be4f1565bcf190f5`. The reference classpaths and staging procedure match `GOVERNED-READ-SUITE-20261114.md`, with candidate source and this test staged first. No dependency downloads were needed. This is a targeted namespace reference run, not the full project suite, Native or CI.

From the task scratch directory, the candidate command was:

```sh
clojure -Sdeps '{:paths ["jvm" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/bound-read-real-20261114/classpath" "/Users/junkawasaki/github/wt/codex/awai-532-ipld-forest/.agent-output/receipt-sink/classpath" "/Users/junkawasaki/github/wt/codex/awai-532-ipld-forest/.agent-output/signed-receipt-adapted" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/frontier/src" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/classpath" "/Users/junkawasaki/github/wt/codex/awai-532-ipld-forest/.agent-output/policy-forest/classpath"]}' -M -e '(require (quote kotobase.execution-keys-test)) (let [r (clojure.test/run-tests (quote kotobase.execution-keys-test))] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))'
```

For the baseline control the same command used `jvm-baseline` in place of `jvm`. An initial attempt from the scratch directory used incorrect relative copy paths and failed to locate the test; no test result was obtained from that attempt.

The one-time `key_registry_ed25519.clj` reference combines the candidate registry with the existing portable `ed25519.sign` implementation and deterministic test-only seeds. On Clojure CLI 1.12.5.1654 it passed six controls: valid signature, wrong public key, altered payload, wrong authority epoch, and duplicate IDs in both orders. Duplicate and wrong-epoch cases did not call cryptography. The same script failed against baseline at the first duplicate assertion. It is not a production signature profile, key source, credential, Native test or product integration. Script SHA-256 `4712b0917413d8844f603bcb33f73621c70682e5c6ae814eec2ba8c3d365d97d`; staged Ed25519 entrypoint SHA-256 `7a21891b988e7d8de632b5aa13e790d4b642bc5cc5e8070f81d240a08c484679` (its transitive local reference dependencies are the existing policy-forest classpath). No download occurred. Commands from the task scratch directory:

```sh
clojure -Sdeps '{:paths ["jvm" "/Users/junkawasaki/github/wt/codex/awai-174-manifest-binding/.agent-output/classpath" "/Users/junkawasaki/github/wt/codex/awai-532-ipld-forest/.agent-output/policy-forest/classpath"]}' -M ../../oracle/awai-174/key_registry_ed25519.clj
```

The baseline control used the same command with `jvm-baseline` in place of `jvm`.

Commands: copy `src/kotobase/execution_keys.cljk` unchanged as `kotobase/execution_keys.cljc`, supply a local `kotoba.lang.coll` shim exposing `clojure.set/difference`, then run `bb -cp <staged-root> <check.clj>`. Staged data and logs are retained in task-owned `.agent-output/key-registry-oracle/`; no dependencies were downloaded for this run. Baseline source SHA-256 `cab5d99b7f2e091700eb00fa9e007e32ee26a33a3c6922ce4710f09a5d885ae4`; candidate source `6f7c3c503197f57e24db68b9425d525621fc32e23c4458356a79d78b2a517f5e`; shim `57ae1276ebf4c148394cb3ad5f65d5f6f9f31ddf294a300580441aa7336e78a4`; check `5d726161787785341a7d327c81c8dcf7a8a7b63c5c0434f39e816674040a6924`.
