;; Independent JVM readback of the hosted signed-basis fixture receipt.
(require '[clojure.edn :as edn] '[kotobase.frontier-store :as frontier]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[kotobase.execution-identity :as id] '[ed25519.sign :as ed]
         '[kotobase.storage.core :as storage] '[kotoba.value.codec :as codec]
         '[otehon.kotobase-forest-blocks :as fb])
(def output (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "receipt output required" {}))))
(def frontier-output (or (System/getenv "FRONTIER_FOREST_OUTPUT") (throw (ex-info "frontier output required" {}))))
(def tool (or (System/getenv "FOREST_TOOL") (throw (ex-info "tool required" {}))))
(def backend (fb/open-backend (str output "/store") (str output "/stage") tool))
(def db (core/open {:storage backend :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def ids (edn/read-string (slurp (str output "/result.edn"))))
(def frontier-backend (fb/open-backend (str frontier-output "/store") (str frontier-output "/stage") tool))
(def frontier-cid (:cid (storage/-read-ref frontier-backend "frontier")))
(def frontier-bytes (get (storage/-get-blocks frontier-backend [frontier-cid]) frontier-cid))
(def db-id (:database-id (codec/verify-value-cid-structurally-bounded frontier-cid frontier-bytes {:max-bytes 16384 :max-depth 16 :max-items 128})))
(def saved (frontier/read-frontier frontier-backend "frontier" db-id))
(def receipt (first (:receipt/records (causal/receipt-at db (:commit ids) (:receipt ids)))))
(assert (some? saved))
(assert (= (:receipt ids) (id/value-cid receipt)))
(assert (= :allow (:authority/decision receipt)))
(assert (true? (ed/verify (ed/public-key (vec (repeat 32 4)))
               (mapv #(bit-and 255 %) (.getBytes ^String (id/payload-cid receipt) "UTF-8"))
               (get-in receipt [:signature :signature/value]))))
(prn {:frontier (:cid saved) :receipt (:receipt ids) :commit (:commit ids) :signature :verified :fresh-process true :pass true})
(shutdown-agents)
