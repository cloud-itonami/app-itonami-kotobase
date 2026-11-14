;; Reference-only integration: real policy/query gate and persisted-frontier
;; admission, but fixture receipt sink and in-memory frontier backend.
(require '[clojure.string :as str] '[kotobase.governed-read :as read]
         '[kotobase.frontier-store :as frontier] '[kotobase.storage.memory :as memory]
         '[kotobase.engine.identity :as engine-id])
(load-string (first (str/split (slurp "../../oracle/awai-174/basis_sync.clj") #"\(defn trial")))
(def backend (memory/memory-store))
(def entry {:root "root" :commit {:format engine-id/logical-commit-format :database-id "db" :epoch 1
 :parents [] :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema"
 :model-contract-root "model" :admission-policy-root "bafy-policy"}})
(frontier/accept-and-store! backend "frontier" "db" {:allow-initialize? true :candidate-root "root" :path [entry] :verify-entry (constantly true)})
(defn trial [allowed?]
 (let [calls (atom [])
       manifest (assoc m :signature (sign m))
       sink (fn [_] (swap! calls conj :commit) {:receipt/durable? true :receipt/cid "bafy-receipt"})
       req {:request request :manifest manifest
            :authority-decision {:decision/status :allow :decision/runtime-capability-spec
              {:capability/action :object/read :capability/resource #{"INV-42"} :capability/tenant "acme" :capability/principal "did:key:test"}}
            :commit {:database sink :expected-basis-cid nil :receipt-cid-fn id/value-cid}
            :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (fn [_] (swap! calls conj :nonce) true)}
            :value-cid id/value-cid :verify verify :plan-digest (constantly "bafy-plan")
            :cost (constantly {:dependent-hops 1 :requests 2 :bytes 512 :cache-profile :cold})
            :implementation/build "local-oracle" :sign sign
            :authorize! (fn [q] (swap! calls conj :authorize) {:allowed? true :projection (set (:find q)) :basis "bafy-basis" :policy-cid "bafy-policy"})
            :schema {:invoice/id {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
            :evaluate! (fn [_ _] (swap! calls conj :evaluate) [{:invoice/id "INV-42"}])}
       cfg {:backend backend :ref-name "frontier" :database-id "db" :fetch-entry! (fn [_] (swap! calls conj :fetch) entry)
            :verify-entry! (fn [_] (swap! calls conj :verify) allowed?)}
       result (try (read/read-bound! req cfg) (catch Exception e {:error (ex-data e)}))]
   {:calls @calls :result result}))
(let [good (trial true) bad (trial false)]
 (assert (= [:fetch :verify :nonce :authorize :evaluate :commit] (:calls good)))
 (assert (= [{:invoice/id "INV-42"}] (get-in good [:result :rows])))
 (assert (= [:fetch :verify] (:calls bad)))
 (assert (= :unverified-entry (get-in bad [:result :error :kotobase.persisted-basis/reason])))
 (prn {:valid (:calls good) :invalid (:calls bad) :receipt-sink :fixture :frontier :memory :pass true}))
(shutdown-agents)
