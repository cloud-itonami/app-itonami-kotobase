(ns awai-oracle.basis-async
  (:require [kotobase.governed-execution :as g] [kotobase.execution-identity :as id] [ed25519.sign :as ed]))
(def seed (vec (repeat 32 7)))
(def keypair (ed/secret-key! seed))
(defn msg [s] (vec (array-seq (.encode (js/TextEncoder.) s))))
(defn sign [record] (ed/sign keypair (msg (id/payload-cid record))))
(defn verify [{:keys [payload-cid signature]}] (js/Promise.resolve (ed/verify (:public keypair) (msg payload-cid) signature)))
(def q {:find [:invoice/id] :where [[:invoice/id "INV-42"]] :scope {:tenant "acme" :resources #{"INV-42"} :purpose :payment-review :basis "bafy-basis"} :limit 20})
(def req {:request/version 1 :principal "did:key:test" :tenant "acme" :graph "invoices" :operation :read :query/digest (id/value-cid q) :base/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :nonce "nonce-1" :expires-at "2026-09-02T15:00:00Z"})
(def m {:execution/version 1 :data/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :location/manifest "bafy-packs" :schema/root "bafy-schema" :parent nil :issued-at "2026-09-02T14:00:00Z"})
(defn trial [k v mode]
  (let [calls (atom []) unsigned (assoc m k v) manifest (assoc unsigned :signature (if (= mode :bad-signature) (vec (repeat 64 0)) (sign unsigned)))
        opts {:request req :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (fn [_] (swap! calls conj :nonce) (js/Promise.resolve true))}
              :value-cid id/value-cid :verify verify :plan-digest (fn [_] (js/Promise.resolve "bafy-plan"))
              :cost (fn [] (js/Promise.resolve {:dependent-hops 1 :requests 2 :bytes 512 :cache-profile :cold}))
              :implementation/build "local-oracle" :sign #(js/Promise.resolve (sign %))
              :commit! (fn [_] (swap! calls conj :commit) (js/Promise.resolve {:receipt/durable? true :receipt/cid "bafy-receipt"}))
              :authorize! (fn [q] (swap! calls conj :authorize) (js/Promise.resolve {:allowed? (not= mode :policy-deny) :projection (set (:find q)) :basis "bafy-basis" :policy-cid "bafy-policy"}))
              :schema {:invoice/id {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
              :evaluate! (fn [_ _] (swap! calls conj :evaluate) (js/Promise.resolve [{:invoice/id "INV-42"}]))}]
    (-> (g/execute-bound-async! opts
           (when-not (= mode :absent)
             (fn [context]
               (swap! calls conj :basis)
               (when-not (and (= req (:request context)) (= manifest (:manifest context)))
                 (throw (js/Error. "wrong basis context")))
               (case mode :pass (js/Promise.resolve true) :policy-deny (js/Promise.resolve true) :bad-signature (js/Promise.resolve true) :deny (js/Promise.resolve false)
                          :truthy (js/Promise.resolve :yes) :throws (js/Promise.reject (js/Error. "basis-error"))
                          :policy-shaped (js/Promise.reject (ex-info "untrusted admission error" {:kotobase.query/reason :denied}))))))
        (.then (fn [_] {:field k :reason nil :calls @calls}))
        (.catch (fn [e] {:field k :reason (or (:kotobase.governed-execution/reason (ex-data e)) (:kotobase.execution-contract/reason (ex-data e))) :calls @calls})))))
(defn -main []
  (-> (js/Promise.all #js [(trial :data/commit "bafy-basis" :pass)
                           (trial :data/commit "bafy-other" :pass)
                           (trial :data/commit "bafy-basis" :bad-signature)
                           (trial :data/commit "bafy-basis" :deny)
                           (trial :data/commit "bafy-basis" :truthy)
                           (trial :data/commit "bafy-basis" :throws)
                           (trial :data/commit "bafy-basis" :policy-shaped)
                           (trial :data/commit "bafy-basis" :policy-deny)
                           (trial :data/commit "bafy-basis" :absent)])
      (.then (fn [rs]
               (let [[good bad invalid-signature deny truthy throws policy-shaped policy-deny absent] (vec (array-seq rs))]
                 (doseq [r [good bad invalid-signature deny truthy throws policy-shaped policy-deny absent]] (prn r))
                 (when-not (and (nil? (:reason good)) (= [:basis :nonce :authorize :evaluate :commit] (:calls good))
                                (= :cross-record-mismatch (:reason bad)) (empty? (:calls bad))
                                (= :signature-not-verified (:reason invalid-signature)) (empty? (:calls invalid-signature))
                                (every? #(and (= :basis-admission-refused (:reason %)) (= [:basis] (:calls %))) [deny truthy])
                                (= [:basis] (:calls throws)) (= [:basis] (:calls policy-shaped))
                                (= :authority-denied (:reason policy-deny)) (= [:basis :nonce :authorize :commit] (:calls policy-deny))
                                (= :missing-basis-verifier (:reason absent)) (empty? (:calls absent)))
                   (throw (js/Error. "async basis admission failed"))))))
      (.catch (fn [e] (.error js/console e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
