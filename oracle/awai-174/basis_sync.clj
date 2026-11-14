(require '[kotobase.governed-execution :as g] '[kotobase.execution-identity :as id] '[ed25519.sign :as ed])
(def seed (vec (repeat 32 7)))
(def keypair (ed/secret-key! seed))
(defn msg [s] (mapv #(bit-and 255 %) (.getBytes ^String s "UTF-8")))
(defn sign [record] (ed/sign keypair (msg (id/payload-cid record))))
(defn verify [{:keys [payload-cid signature]}] (ed/verify (:public keypair) (msg payload-cid) signature))
(def q {:find [:invoice/id] :where [[:invoice/id "INV-42"]]
        :scope {:tenant "acme" :resources #{"INV-42"} :purpose :payment-review :basis "bafy-basis"} :limit 20})
(def request {:request/version 1 :principal "did:key:test" :tenant "acme" :graph "invoices" :operation :read
              :query/digest (id/value-cid q) :base/commit "bafy-basis" :authority/policy "bafy-policy"
              :authority/epoch 7 :nonce "nonce-1" :expires-at "2026-09-02T15:00:00Z"})
(def m {:execution/version 1 :data/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7
        :location/manifest "bafy-packs" :schema/root "bafy-schema" :parent nil :issued-at "2026-09-02T14:00:00Z"})
(defn trial [k v mode]
  (let [calls (atom []) manifest (assoc m k v) manifest (assoc manifest :signature (if (= mode :bad-signature) (vec (repeat 64 0)) (sign manifest)))
        opts {:request request :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7
                   :consume-nonce! (fn [_] (swap! calls conj :nonce) true)}
              :value-cid id/value-cid :verify verify :plan-digest (constantly "bafy-plan")
              :cost (constantly {:dependent-hops 1 :requests 2 :bytes 512 :cache-profile :cold})
              :implementation/build "local-oracle" :sign sign
              :commit! (fn [_] (swap! calls conj :commit) {:receipt/durable? true :receipt/cid "bafy-receipt"})
              :authorize! (fn [q] (swap! calls conj :authorize) {:allowed? true :projection (set (:find q)) :basis "bafy-basis" :policy-cid "bafy-policy"})
              :schema {:invoice/id {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
              :evaluate! (fn [_ _] (swap! calls conj :evaluate) [{:invoice/id "INV-42"}])}
        err (try (g/execute-bound! opts
                 (when-not (= mode :absent)
                   (fn [context]
                     (swap! calls conj :basis)
                     (assert (= request (:request context)))
                     (assert (= manifest (:manifest context)))
                     (case mode :pass true :bad-signature true :deny false :truthy :yes :throws (throw (ex-info "basis-error" {})))))) nil (catch Exception e (ex-data e)))]
    {:field k :reason (or (:kotobase.governed-execution/reason err) (:kotobase.execution-contract/reason err)) :calls @calls}))

(let [good (trial :data/commit "bafy-basis" :pass)
      bad-record (trial :data/commit "bafy-other" :pass)
      invalid-signature (trial :data/commit "bafy-basis" :bad-signature)
      deny (trial :data/commit "bafy-basis" :deny)
      truthy (trial :data/commit "bafy-basis" :truthy)
      throws (trial :data/commit "bafy-basis" :throws)
      absent (trial :data/commit "bafy-basis" :absent)]
  (doseq [r [good bad-record invalid-signature deny truthy throws absent]] (prn r))
  (assert (= [:basis :nonce :authorize :evaluate :commit] (:calls good)))
  (assert (nil? (:reason good)))
  (assert (= :cross-record-mismatch (:reason bad-record)))
  (assert (empty? (:calls bad-record)))
  (assert (= :signature-not-verified (:reason invalid-signature)))
  (assert (empty? (:calls invalid-signature)))
  (doseq [r [deny truthy]]
    (assert (= :basis-admission-refused (:reason r)))
    (assert (= [:basis] (:calls r))))
  (assert (= [:basis] (:calls throws)))
  (assert (= :missing-basis-verifier (:reason absent)))
  (assert (empty? (:calls absent))))
(shutdown-agents)
