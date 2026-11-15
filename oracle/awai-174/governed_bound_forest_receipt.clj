;; Hosted reference oracle: fixtures for identity and policy, actual Forest
;; block backend, persisted frontier and canonical causal execution sink.
(require '[clojure.string :as str] '[kotobase.governed-read :as read]
         '[kotobase.frontier-store :as frontier] '[kotobase.engine.identity :as engine-id]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[otehon.storage :as store] '[otehon.kotobase-forest-blocks :as forest])
(load-string (first (str/split (slurp "../../oracle/awai-174/basis_sync.clj") #"\(defn trial")))
(def base (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "output required" {}))))
(def tool (or (System/getenv "FOREST_TOOL") (throw (ex-info "tool required" {}))))
(assert (.mkdir (java.io.File. base)))
(assert (.mkdir (java.io.File. (str base "/stage"))))
(store/init! (str base "/store"))
(defn backend [] (forest/open-backend (str base "/store") (str base "/stage") tool))
(defn database [] (core/open {:storage (backend) :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def entry {:root "root" :commit {:format engine-id/logical-commit-format :database-id "db" :epoch 1 :parents [] :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema" :model-contract-root "model" :admission-policy-root "bafy-policy"}})
(frontier/accept-and-store! (backend) "frontier" "db" {:allow-initialize? true :candidate-root "root" :path [entry] :verify-entry (constantly true)})
(defn trial [allowed?]
 (let [calls (atom []) db (database) manifest (assoc m :signature (sign m))
       req {:request request :manifest manifest
        :authority-decision {:decision/status :allow :decision/runtime-capability-spec {:capability/action :object/read :capability/resource #{"INV-42"} :capability/tenant "acme" :capability/principal "did:key:test"}}
        :commit {:database db :expected-basis-cid nil :receipt-cid-fn id/value-cid}
        :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (fn [_] (swap! calls conj :nonce) true)}
        :value-cid id/value-cid :verify verify :plan-digest (constantly "bafy-plan")
        :cost (constantly {:dependent-hops 1 :requests 2 :bytes 512 :cache-profile :cold})
        :implementation/build "local-oracle" :sign sign
        :authorize! (fn [q] (swap! calls conj :authorize) {:allowed? true :projection (set (:find q)) :basis "bafy-basis" :policy-cid "bafy-policy"})
        :schema {:invoice/id {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
        :evaluate! (fn [_ _] (swap! calls conj :evaluate) [{:invoice/id "INV-42"}])}
       cfg {:backend (backend) :ref-name "frontier" :database-id "db" :fetch-entry! (fn [_] (swap! calls conj :fetch) entry)
            :verify-entry! (fn [_] (swap! calls conj :verify) allowed?)}
       original causal/execution-receipt-sink
       result (try (with-redefs [causal/execution-receipt-sink (fn [db opts] (let [sink (original db opts)] (fn [receipt] (swap! calls conj :commit) (sink receipt))))]
                     (read/read-bound! req cfg))
                   (catch Exception e {:error (ex-data e)}))]
   {:calls @calls :result result}))
(let [before (:root (store/read-store (str base "/store"))) bad (trial false)
      after (:root (store/read-store (str base "/store")))]
 (assert (= [:fetch :verify] (:calls bad))) (assert (= before after)))
(let [good (trial true) result (:result good) commit (get-in result [:provenance :receipt-commit-cid]) cid (get-in result [:provenance :receipt-cid])]
 (assert (= [:fetch :verify :nonce :authorize :evaluate :commit] (:calls good)))
 (assert (= [{:invoice/id "INV-42"}] (:rows result)))
 (assert (= [(:execution/receipt result)] (:receipt/records (causal/receipt-at (database) commit cid))))
 (spit (str base "/result.edn") (pr-str {:commit commit :receipt cid}))
 (prn {:valid (:calls good) :invalid [:fetch :verify] :receipt cid :commit commit :forest-readback true :pass true}))
(shutdown-agents)
