;; Fresh-process readback of the local fixture's persisted frontier and receipt.
(require '[clojure.edn :as edn] '[kotobase.frontier-store :as frontier]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[kotobase.execution-identity :as id] '[ed25519.sign :as ed]
         '[otehon.kotobase-forest-blocks :as forest])
(def base (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "output required" {}))))
(def tool (or (System/getenv "FOREST_TOOL") (throw (ex-info "tool required" {}))))
(def backend (forest/open-backend (str base "/store") (str base "/stage") tool))
(def database (core/open {:storage backend :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def ids (edn/read-string (slurp (str base "/result.edn"))))
(def saved (frontier/read-frontier backend "frontier" "db"))
(def receipt (first (:receipt/records (causal/receipt-at database (:commit ids) (:receipt ids)))))
(assert (= "root" (get-in saved [:frontier :logical-commit-root])))
(assert (= (:receipt ids) (id/value-cid receipt)))
(assert (true? (ed/verify (ed/public-key (vec (repeat 32 7)))
                         (mapv #(bit-and 255 %) (.getBytes ^String (id/payload-cid receipt) "UTF-8"))
                         (:signature receipt))))
(prn {:frontier (:cid saved) :receipt (:receipt ids) :commit (:commit ids) :signature :verified :fresh-process true :pass true})
(shutdown-agents)
