(load-file "host-guest.clj")
(def before (:root (store/read-store (str output "/store"))))
(def events (atom []))
(def req (-> opts (dissoc :commit!)
 (assoc :request (assoc request :nonce "host-negative-postboot")
        :commit {:database (receipt-db) :expected-basis-cid (:commit previous-ids) :receipt-cid-fn id/value-cid}
        :authority-decision real-decision
        :authorize! (fn [q] (swap! events conj :authorize) ((:authorize! opts) q))
        :evaluate! (fn [q d] (swap! events conj :evaluate) ((:evaluate! opts) q d)))))
(def hostcfg {:authority-database authority-db :authority-receipt authority-receipt
 :attestation attestation :registry authority-registry :algorithm :ed25519
 :issuer-authorized? (fn [{:keys [issuer key]}] (and (= issuer "did:key:fixture-authority") (= (:key/id key) "authority")))
 :verify-with (fn [{:keys [key payload-cid signature-value]}] (ed/verify (:key/material key) (payload-bytes payload-cid) signature-value))})
(doseq [bad [(assoc hostcfg :registry (constantly []))
             (assoc hostcfg :issuer-authorized? (constantly false))
             (assoc hostcfg :verify-authority! (constantly true))
             (assoc-in hostcfg [:attestation :signature :signature/value] (vec (repeat 64 0)))]]
 (assert (try (host/read-registry-signed-authority! req (config events identity) (verification-config)
               {:backend (receipt-backend) :single-writer? true} bad) false (catch Exception _ true)))
 (assert (empty? @events)))
(assert (= before (:root (store/read-store (str output "/store")))))
(prn {:host-entry :negative-controls :rejection :before-basis :root :unchanged :pass true})
(shutdown-agents)
