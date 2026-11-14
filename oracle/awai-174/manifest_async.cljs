(ns awai-oracle.manifest-async
  (:require [kotobase.governed-execution :as g] [kotobase.execution-identity :as id] [ed25519.sign :as ed]))
(def seed (vec (repeat 32 7)))
(def keypair (ed/secret-key! seed))
(defn msg [s] (vec (array-seq (.encode (js/TextEncoder.) s))))
(defn sign [record] (ed/sign keypair (msg (id/payload-cid record))))
(defn verify [{:keys [payload-cid signature]}] (js/Promise.resolve (ed/verify (:public keypair) (msg payload-cid) signature)))
(def q {:find [:invoice/id] :where [[:invoice/id "INV-42"]] :scope {:tenant "acme" :resources #{"INV-42"} :purpose :payment-review :basis "bafy-basis"} :limit 20})
(def req {:request/version 1 :principal "did:key:test" :tenant "acme" :graph "invoices" :operation :read :query/digest (id/value-cid q) :base/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :nonce "nonce-1" :expires-at "2026-09-02T15:00:00Z"})
(def m {:execution/version 1 :data/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :location/manifest "bafy-packs" :schema/root "bafy-schema" :parent nil :issued-at "2026-09-02T14:00:00Z"})
(defn trial [k v]
  (let [calls (atom []) unsigned (assoc m k v) manifest (assoc unsigned :signature (sign unsigned))
        opts {:request req :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (fn [_] (swap! calls conj :nonce) (js/Promise.resolve true))}
              :value-cid id/value-cid :verify verify :plan-digest (fn [_] (js/Promise.resolve "bafy-plan"))
              :cost (fn [] (js/Promise.resolve {:dependent-hops 1 :requests 2 :bytes 512 :cache-profile :cold}))
              :implementation/build "local-oracle" :sign #(js/Promise.resolve (sign %))
              :commit! (fn [_] (swap! calls conj :commit) (js/Promise.resolve {:receipt/durable? true :receipt/cid "bafy-receipt"}))
              :authorize! (fn [q] (swap! calls conj :authorize) (js/Promise.resolve {:allowed? true :projection (set (:find q)) :basis "bafy-basis" :policy-cid "bafy-policy"}))
              :schema {:invoice/id {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
              :evaluate! (fn [_ _] (swap! calls conj :evaluate) (js/Promise.resolve [{:invoice/id "INV-42"}]))}]
    (-> (g/execute-async! opts)
        (.then (fn [_] {:field k :reason nil :calls @calls}))
        (.catch (fn [e] {:field k :reason (or (:kotobase.governed-execution/reason (ex-data e)) (:kotobase.execution-contract/reason (ex-data e))) :calls @calls})))))
(defn -main []
  (-> (js/Promise.all #js [(trial :data/commit "bafy-other") (trial :authority/policy "bafy-other-policy")
                           (trial :authority/epoch 8) (trial :data/commit "bafy-basis")])
      (.then (fn [rs] (let [results (vec (array-seq rs)) bad (subvec results 0 3) good (last results)]
                        (doseq [r results] (prn r))
                        (when-not (and (every? #(and (= :cross-record-mismatch (:reason %)) (empty? (:calls %))) bad)
                                       (nil? (:reason good)) (= [:nonce :authorize :evaluate :commit] (:calls good)))
                          (throw (js/Error. "async manifest binding oracle failed"))))))
      (.catch (fn [e] (.error js/console e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
