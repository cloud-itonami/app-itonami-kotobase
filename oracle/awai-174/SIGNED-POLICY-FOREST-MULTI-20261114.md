# Signed multi-row Prolly basis and recovered policy, 2026-11-14

The hosted writer created a new Forest-backed Prolly transaction containing three names and a `kotobase.policy/read` protected-prefix fact. It derived the normalized policy and CID from that snapshot, bound the policy CID and checkpoint into an existing Kotobase logical commit, signed its canonical payload with the existing Ed25519 primitive and staged the commit envelope together with its data blocks. The semantic callback re-read the stored envelope, recovered the Prolly state, recomputed the policy, and verified signature and checkpoint before publishing the `signed-basis` ref.

A separate JVM with a nonexistent writer path recovered the envelope through that ref, checked its content CID, reconstructed the same policy from Prolly, verified the signed logical root and checkpoint using `kotobase.signed-basis/verifier`, rejected a modified signature and a different policy CID, and queried the same snapshot. A valid fixture Dango token provided the protected-read capability: the policy filter returned three names with the capability and zero without it.

```clojure
{:mode :write, :basis "bafyreidmohviwiszk3epzgclala7nq6csdcezwhr6knmkxu63hzngckznm", :physical "bafyreiagluyx46pxdidp6vopgfihrcfj7bc77emahhqmvgplywvn5lnaru", :policy "bafyreiadioixzcdfft2cjwm5ic4iozd3y5keuxs2wqto3tqjh2s3hbo3fq", :rows 3, :pass true}
{:mode :read, :signed-basis :verified, :policy :recovered-and-bound, :allowed 3, :no-capability 0, :tamper :refused, :pass true}
```

Host input is an isolated fixture: signer and Dango roots are deterministic test keys, clock is fixed, transaction/schema/model roots are not independently authenticated, and the location verifier is explicitly `true`; there is no secure issuer registry, request binding, rollback-resistant witness or authenticated physical catalog. This test exercises the actual checkpoint and policy CID binding, not production authority, a governed receipt, Native, independent nodes, OS cache loss, physical power loss or a general query compiler. Source and basis blocks are published in one Forest group, but this 3-row fixture produced one physical Forest file and does not establish multi-Pack scale. The separate receipt experiment in `GOVERNED-FOREST-MULTI-DURABLE-20261114.md` is not combined with this signed-policy fixture yet.

On macOS ARM OpenJDK 21.0.1 / Clojure 1.12.5, the source SHA-256 is `5f55b9201d5dafa071dedd81cc200fc2f9e17853deee60b8f69ef09425f4ee48`, and the physical Forest SHA-256 is `290a82b9978397b880b564e839163ed706c7cd525e1df7b0862e73ddf6341432`. The pinned tool hash and classpath overlays follow `GOVERNED-FOREST-MULTIROW-20261114.md`. No dependencies downloaded. The retained test output occupies 65 MiB, including the private tool copy.

Reproduce using `task_kotobase`, `task_cp` and `FOREST_TOOL` from that document. Choose an unused task-owned output path; writer creation fails if it exists. Each command starts a separate process.

```sh
export KOTOBASE_ORACLE_ROOT="$task_kotobase"
export MULTI_FOREST_OUTPUT="$task_kotobase/.agent-output/multi-policy-signed-01"
java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_forest_multi.clj" write
FOREST_TOOL=/nonexistent/no-forest-writer java -cp "$task_cp" clojure.main "$task_kotobase/oracle/awai-174/signed_policy_forest_multi.clj" read
```
