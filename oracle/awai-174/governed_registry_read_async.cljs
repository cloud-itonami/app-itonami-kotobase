(ns governed-registry-read-async
  (:require [kotobase.governed-read :as read] [kotobase.frontier-store :as frontier]
            [kotobase.storage.memory :as memory] [kotobase.engine.identity :as engine-id]
            [kotobase.engine.profile :as profile] [kotobase.execution-identity :as id]
            [ed25519.sign :as ed]))
(def keypair (ed/secret-key! (vec (repeat 32 7))))
(defn msg [s] (vec (array-seq (.encode (js/TextEncoder.) s))))
(defn sign [v] (ed/sign keypair (msg (id/payload-cid v))))
(def q {:find [:invoice/id] :where [[:invoice/id "INV-42"]] :scope {:tenant "acme" :resources #{"INV-42"} :purpose :payment-review :basis "bafy-basis"} :limit 20})
(def request {:request/version 1 :principal "did:key:test" :tenant "acme" :graph "invoices" :operation :read :query/digest (id/value-cid q) :base/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :nonce "nonce" :expires-at "2026-09-02T15:00:00Z"})
(def unsigned {:execution/version 1 :data/commit "bafy-basis" :authority/policy "bafy-policy" :authority/epoch 7 :location/manifest "bafy-packs" :schema/root "bafy-schema" :parent nil :issued-at "2026-09-02T14:00:00Z"})
(def commit {:format engine-id/logical-commit-format :database-id "db" :epoch 1 :parents [] :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema" :model-contract-root "model" :admission-policy-root "bafy-policy"})
(defn digest [s] (str "digest:" s))
(def root (digest (engine-id/logical-commit-string commit)))
(def payload (engine-id/signature-payload {:logical-commit-root root :issuer "issuer" :key-id "key"}))
(def entry {:root root :commit commit :issuer "issuer" :key-id "key" :signature (ed/sign keypair (msg payload)) :physical-root "bafy-basis"})
(def checkpoint {:database-id "db" :epoch 1 :logical-checkpoint-root "cp" :physical-root "bafy-basis" :engine profile/prolly})
(def key-a {:key/id "key" :key/algorithm :ed25519 :key/material (:public keypair)})
(def backend (memory/memory-store))
(defn trial [duplicate?]
 (let [calls (atom [])
       sink (fn [_] (swap! calls conj :commit) (js/Promise.resolve {:receipt/durable? true :receipt/cid "bafy-receipt"}))
       req {:request request :manifest (assoc unsigned :signature (sign unsigned))
        :authority-decision {:decision/status :allow :decision/runtime-capability-spec {:capability/action :object/read :capability/resource #{"INV-42"} :capability/tenant "acme" :capability/principal "did:key:test"}}
        :commit {:database sink :expected-basis-cid nil :receipt-cid-fn id/value-cid}
        :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (fn [_] (throw (js/Error. "unexpected nonce callback")))}
        :value-cid id/value-cid :verify (fn [{:keys [payload-cid signature]}] (js/Promise.resolve (ed/verify (:public keypair) (msg payload-cid) signature)))
        :plan-digest (fn [_] (js/Promise.resolve "bafy-plan")) :cost (fn [] (js/Promise.resolve {:dependent-hops 1 :requests 2 :bytes 512 :cache-profile :cold}))
        :implementation/build "local-oracle" :sign #(js/Promise.resolve (sign %))
        :authorize! (fn [q] (swap! calls conj :authorize) (js/Promise.resolve {:allowed? true :projection (set (:find q)) :basis "bafy-basis" :policy-cid "bafy-policy"}))
        :schema {:invoice/id {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
        :evaluate! (fn [_ _] (swap! calls conj :evaluate) (js/Promise.resolve [{:invoice/id "INV-42"}]))}
       basis {:backend backend :ref-name "frontier" :database-id "db" :fetch-entry! (fn [_] (swap! calls conj :fetch) (js/Promise.resolve entry))}
       verification {:digest! (fn [s] (js/Promise.resolve (digest s)))
                     :checkpoint! (fn [_] (swap! calls conj :checkpoint) (js/Promise.resolve checkpoint))
                     :verify-location! (fn [_] (js/Promise.resolve true))
                     :registry (fn [_] (swap! calls conj :registry) (if duplicate? [key-a key-a] [key-a]))
                     :algorithm :ed25519
                     :issuer-authorized? (fn [{:keys [issuer]}] (swap! calls conj :issuer) (= issuer "issuer"))
                     :verify-with (fn [{:keys [key payload signature-value]}] (swap! calls conj :crypto) (ed/verify (:key/material key) (msg payload) signature-value))}]
   (-> (read/read-registry-persistent-async! req basis verification {:backend backend :single-writer? false})
       (.then (fn [r] {:calls @calls :rows (:rows r)}))
       (.catch (fn [e] {:calls @calls :error (or (:kotobase.execution-keys/reason (ex-data e)) (:kotobase.authority-window/reason (ex-data e)))})))))
(defn -main []
 (-> (frontier/accept-and-store! backend "frontier" "db" {:allow-initialize? true :candidate-root root :path [entry] :verify-entry (constantly true)})
     (.then (fn [_] (trial true)))
     (.then (fn [bad] (assert (= [:fetch :registry] (:calls bad))) (assert (= :duplicate-key-id (:error bad))) (trial false)))
     (.then (fn [good] (assert (= [:fetch :registry :issuer :crypto :checkpoint :authorize :evaluate :commit] (:calls good))) (assert (= [{:invoice/id "INV-42"}] (:rows good))) (trial false)))
     (.then (fn [replay] (assert (= [:fetch :registry :issuer :crypto :checkpoint] (:calls replay))) (assert (= :nonce-replayed (:error replay))) (println "CLJS registry persistent read: reject, accept and replay pass")))
     (.catch (fn [e] (.error js/console e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
