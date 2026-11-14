;; Local hosted reference. All authority, data, roots and keys are fixtures.
(require '[kotobase.signed-basis :as signed]
         '[kotobase.signed-basis-keys :as basis-keys]
         '[kotobase.engine.identity :as identity]
         '[kotobase.engine.profile :as profile]
         '[ed25519.sign :as ed])
(def seed (vec (repeat 32 3)))
(def key-a {:key/id "key-a" :key/algorithm :ed25519 :key/material (ed/public-key seed)})
(def commit {:format identity/logical-commit-format :database-id "db" :epoch 7 :parents []
             :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema"
             :model-contract-root "model" :admission-policy-root "policy"})
(defn digest [x] (str "digest:" x))
(def root (digest (identity/logical-commit-string commit)))
(def payload (identity/signature-payload {:logical-commit-root root :issuer "issuer-a" :key-id "key-a"}))
(defn bytes [s] (mapv #(bit-and 255 %) (.getBytes s "UTF-8")))
(def sig (ed/sign (ed/secret-key! seed) (bytes payload)))
(def entry {:root root :commit commit :issuer "issuer-a" :key-id "key-a" :signature sig :physical-root "physical"})
(def ctx {:entry entry :request {:tenant "tenant-a" :authority/epoch 7 :base/commit "physical" :authority/policy "policy"}
          :manifest {:data/commit "physical" :authority/policy "policy"}})
(def other-payload (identity/signature-payload {:logical-commit-root root :issuer "other" :key-id "key-a"}))
(def other-sig (ed/sign (ed/secret-key! seed) (bytes other-payload)))
(def signed-other-issuer (-> ctx (assoc-in [:entry :issuer] "other") (assoc-in [:entry :signature] other-sig)))
(def checkpoint {:database-id "db" :epoch 7 :logical-checkpoint-root "cp" :physical-root "physical" :engine profile/prolly})
(defn attempt [entries c]
  (let [events (atom [])
        callback (basis-keys/verifier
                  {:registry (fn [scope] (swap! events conj :registry)
                               (if (= scope {:role :issuer :tenant "tenant-a" :epoch 7}) entries []))
                   :algorithm :ed25519
                   :issuer-authorized? (fn [{:keys [issuer key]}]
                                         (swap! events conj :issuer)
                                         (and (= issuer "issuer-a") (= key key-a)))
                   :verify-with (fn [{:keys [key payload signature-value]}]
                                  (swap! events conj :crypto)
                                  (ed/verify (:key/material key) (bytes payload) signature-value))})
        verify (signed/verifier {:digest! digest :verify-location! (constantly true)
                                 :verify-signature! callback
                                 :checkpoint! (fn [_] (swap! events conj :checkpoint) checkpoint)})]
    {:result (try (verify c) (catch clojure.lang.ExceptionInfo e
                              (or (:kotobase.signed-basis/reason (ex-data e))
                                  (:kotobase.signed-basis-keys/reason (ex-data e))
                                  (:kotobase.execution-keys/reason (ex-data e)))))
     :events @events}))
(assert (= {:result true :events [:registry :issuer :crypto :checkpoint]} (attempt [key-a] ctx)))
(assert (= {:result :duplicate-key-id :events [:registry]} (attempt [key-a key-a] ctx)))
(assert (= {:result :no-key-registered :events [:registry]} (attempt [key-a] (assoc-in ctx [:request :authority/epoch] 8))))
(assert (= {:result :issuer-not-authorized :events [:registry :issuer]} (attempt [key-a] signed-other-issuer)))
(assert (= {:result :signature-refused :events [:registry :issuer :crypto]}
           (attempt [key-a] (update-in ctx [:entry :signature 0] bit-xor 1))))
(assert (= {:result :root-mismatch :events []} (attempt [key-a] (assoc-in ctx [:entry :root] "other"))))
(prn {:status :passed :signed-basis true :registry true :ed25519 true :controls 6})
