(load-file "scoped-guest.clj")
(def ctx {:commit-cid authority-commit :request request :query query :authority-receipt authority-receipt})
(def cfg {:attestation attestation :registry authority-registry :algorithm :ed25519
 :issuer-authorized? (fn [{:keys [issuer key]}] (and (= issuer "did:key:fixture-authority") (= (:key/id key) "authority")))
 :verify-with (fn [{:keys [key payload-cid signature-value]}] (ed/verify (:key/material key) (payload-bytes payload-cid) signature-value))})
(def unsigned-other (assoc (dissoc attestation :signature) :issuer "did:key:other"))
(def resigned-other (assoc unsigned-other :signature (sign "authority" authority-seed unsigned-other)))
(def before (:root (store/read-store (str output "/store"))))
(assert (true? ((attest/verifier cfg) ctx)))
(doseq [[config context] [[(assoc cfg :attestation resigned-other) ctx]
 [(assoc-in cfg [:attestation :signature :signature/value] (vec (repeat 64 0))) ctx]
 [(assoc cfg :registry (fn [_] [authority-entry authority-entry])) ctx]
 [cfg (assoc-in ctx [:request :tenant] "other")]
 [cfg (assoc ctx :commit-cid "other")]]]
 (assert (not (true? (try ((attest/verifier config) context) (catch Exception _ false))))))
(assert (= before (:root (store/read-store (str output "/store")))))
(prn {:persisted-attestation :verified :issuer-key-scope-signature :tampering-refused :root :unchanged :pass true})
(shutdown-agents)
