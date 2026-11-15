;; Fresh process: the real Forest-backed nonce blocks replay and receipt survives.
(require '[clojure.edn :as edn] '[kotobase.nonce-store :as nonce] '[kotobase.core :as core]
         '[kotobase.causal-commit :as causal] '[kotobase.execution-identity :as id]
         '[ed25519.sign :as ed] '[otehon.storage :as store] '[otehon.kotobase-forest-blocks :as forest])
(def base (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "output required" {}))))
(def tool (or (System/getenv "FOREST_TOOL") (throw (ex-info "tool required" {}))))
(def backend (forest/open-backend (str base "/store") (str base "/stage") tool))
(def db (core/open {:storage backend :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def ids (edn/read-string (slurp (str base "/result.edn"))))
(def saved-root (:root (store/read-store (str base "/store"))))
(assert (false? ((nonce/consumer {:backend backend :scope "fixture/tenant/principal" :single-writer? true}) "fixture-bound-prolly-persistent-nonce-20261114")))
(assert (= saved-root (:root (store/read-store (str base "/store")))))
(def receipt (first (:receipt/records (causal/receipt-at db (:commit ids) (:receipt ids)))))
(assert (= (:receipt ids) (id/value-cid receipt)))
(assert (true? (ed/verify (ed/public-key (vec (repeat 32 4)))
               (mapv #(bit-and 255 %) (.getBytes ^String (id/payload-cid receipt) "UTF-8"))
               (get-in receipt [:signature :signature/value]))))
(prn {:nonce-replay :refused :store-root :unchanged :receipt (:receipt ids) :signature :verified :fresh-process true :pass true})
(shutdown-agents)
