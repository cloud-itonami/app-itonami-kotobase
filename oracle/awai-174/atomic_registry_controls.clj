;; Local hosted oracle; key material is synthetic and does not authorize production.
(require '[kotobase.governed-atomic :as atomic] '[kotobase.governed-execution :as governed]
         '[kotobase.nonce-store :as nonce] '[ed25519.sign :as ed])
(def seed (vec (repeat 32 3)))
(def req {:request/version 1 :principal "p" :tenant "tenant" :graph "g" :operation :read
          :query/digest "q" :base/commit "c" :authority/policy "policy" :authority/epoch 7
          :nonce "n" :expires-at "2026-09-02T15:00:00Z"})
(def opts {:request req :authority {}})
(def evaluations (atom 0))
(def host {:backend :fixture :scope (atomic/request-scope req) :admit-basis! (constantly true)
           :publish! (fn [_ _] {:receipt/durable? true :receipt/cid "c"})})
(def key {:key/id "k" :key/algorithm :ed25519 :key/material (ed/public-key seed)})
(def payload "bafk-test-payload")
(defn bytes [s] (mapv #(bit-and 255 %) (.getBytes s "UTF-8")))
(def signature {:key/id "k" :key/algorithm :ed25519 :signature/value (ed/sign (ed/secret-key! seed) (bytes payload))})
(def ctx {:record :execution-manifest :tenant "tenant" :epoch 7 :payload-cid payload :signature signature})
(def verification {:registry (fn [scope] (when (= scope {:role :issuer :tenant "tenant" :epoch 7}) [key]))
                   :verify-with (fn [{:keys [key payload-cid signature-value]}] (ed/verify (:key/material key) (bytes payload-cid) signature-value))})
(defn run! [context]
 (with-redefs [nonce/available? (fn [& _] true)
               governed/execute-bound! (fn [o _]
                 (when-not (true? ((:verify o) context)) (throw (ex-info "signature refused" {})))
                 ((get-in o [:authority :consume-nonce!]) "n")
                 (swap! evaluations inc) ((:commit! o) {}))]
  (atomic/execute-registry-bound! opts host verification)))
(assert (true? (:receipt/durable? (run! ctx))))
(doseq [bad [(assoc ctx :tenant "other") (assoc ctx :epoch 8)
             (assoc ctx :record :execution-receipt)
             (assoc-in ctx [:signature :key/id] "unknown")
             (assoc ctx :payload-cid "tampered")]]
 (assert (try (run! bad) false (catch Exception _ true))))
(assert (= 1 @evaluations))
(assert (try (atomic/execute-registry-bound! (assoc opts :verify (constantly true)) host verification) false
             (catch Exception e (= :invalid-verifier-options (:kotobase.governed-atomic/reason (ex-data e))))))
(prn {:scoped-ed25519 :verified :wrong-tenant-epoch-role-key-signature :refused :no-extra-evaluation true})
(shutdown-agents)
