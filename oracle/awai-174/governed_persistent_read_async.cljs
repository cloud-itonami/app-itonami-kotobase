(ns governed-persistent-read-async
  (:require [kotobase.governed-read :as read] [kotobase.frontier-store :as frontier]
            [kotobase.storage.memory :as memory] [kotobase.engine.identity :as engine-id]
            [kotobase.execution-identity :as id] [ed25519.sign :as ed]))
(def keypair (ed/secret-key! (vec (repeat 32 7))))
(defn msg [s] (vec (array-seq (.encode (js/TextEncoder.) s))))
(defn sign [v] (ed/sign keypair (msg (id/payload-cid v))))
(def q {:find [:invoice/id] :where [[:invoice/id "INV-42"]] :scope {:tenant "acme" :resources #{"INV-42"} :purpose :payment-review :basis "bafy-basis"} :limit 20})
(def request {:request/version 1 :principal "did:key:test" :tenant "acme" :graph "invoices" :operation :read :query/digest (id/value-cid q) :base/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :nonce "nonce" :expires-at "2026-09-02T15:00:00Z"})
(def unsigned {:execution/version 1 :data/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :location/manifest "bafy-packs" :schema/root "bafy-schema" :parent nil :issued-at "2026-09-02T14:00:00Z"})
(def entry {:root "root" :commit {:format engine-id/logical-commit-format :database-id "db" :epoch 1 :parents [] :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema" :model-contract-root "model" :admission-policy-root "bafy-policy"}})
(def backend (memory/memory-store))
(defn trial [allowed?]
 (let [calls (atom [])
       sink (fn [_] (swap! calls conj :commit) (js/Promise.resolve {:receipt/durable? true :receipt/cid "bafy-receipt"}))
       req {:request request :manifest (assoc unsigned :signature (sign unsigned))
        :authority-decision {:decision/status :allow :decision/runtime-capability-spec {:capability/action :object/read :capability/resource #{"INV-42"} :capability/tenant "acme" :capability/principal "did:key:test"}}
        :commit {:database sink :expected-basis-cid nil :receipt-cid-fn id/value-cid}
        :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (fn [_] (swap! calls conj :nonce) (js/Promise.resolve true))}
        :value-cid id/value-cid :verify (fn [{:keys [payload-cid signature]}] (js/Promise.resolve (ed/verify (:public keypair) (msg payload-cid) signature)))
        :plan-digest (fn [_] (js/Promise.resolve "bafy-plan")) :cost (fn [] (js/Promise.resolve {:dependent-hops 1 :requests 2 :bytes 512 :cache-profile :cold}))
        :implementation/build "local-oracle" :sign #(js/Promise.resolve (sign %))
        :authorize! (fn [q] (swap! calls conj :authorize) (js/Promise.resolve {:allowed? true :projection (set (:find q)) :basis "bafy-basis" :policy-cid "bafy-policy"}))
        :schema {:invoice/id {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
        :evaluate! (fn [_ _] (swap! calls conj :evaluate) (js/Promise.resolve [{:invoice/id "INV-42"}]))}
       cfg {:backend backend :ref-name "frontier" :database-id "db" :fetch-entry! (fn [_] (swap! calls conj :fetch) (js/Promise.resolve entry))
            :verify-entry! (fn [_] (swap! calls conj :verify) (js/Promise.resolve allowed?))}]
   (-> (read/read-persistent-async! req cfg {:backend backend :single-writer? false})
       (.then (fn [r] {:calls @calls :rows (:rows r)}))
       (.catch (fn [e] {:calls @calls :error (or (:kotobase.persisted-basis/reason (ex-data e)) (:kotobase.authority-window/reason (ex-data e)))})))))
(defn -main []
 (-> (frontier/accept-and-store! backend "frontier" "db" {:allow-initialize? true :candidate-root "root" :path [entry] :verify-entry (constantly true)})
     (.then (fn [_] (trial false)))
     (.then (fn [bad] (assert (= [:fetch :verify] (:calls bad))) (assert (= :unverified-entry (:error bad))) (trial true)))
     (.then (fn [good] (assert (= [:fetch :verify :authorize :evaluate :commit] (:calls good))) (assert (= [{:invoice/id "INV-42"}] (:rows good))) (trial true)))
     (.then (fn [replay] (assert (= [:fetch :verify] (:calls replay))) (assert (= :nonce-replayed (:error replay))) (println "CLJS persistent governed read: reject, accept and replay pass")))
     (.catch (fn [e] (.error js/console e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
