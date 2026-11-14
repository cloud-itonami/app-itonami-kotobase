;; Hosted fixture with actual Prolly, Datalog, policy, signed proof and Forest sink.
(require '[clojure.string :as str] '[clojure.edn :as edn]
         '[kotobase.nonce-store :as nonce-store] '[kotobase.governed-read :as read] '[kotobase.signed-basis :as signed] '[kotobase.signed-basis-keys :as basis-keys]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[otehon.kotobase-forest-blocks :as fb])
(load-file "nixos/otehon/storage/oracle/proof_location_common.clj")
(def frontier-output (or (System/getenv "FRONTIER_FOREST_OUTPUT") (throw (ex-info "frontier output required" {}))))
(def output (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "receipt output required" {}))))
(def frontier-backend (fb/open-backend (str frontier-output "/store") (str frontier-output "/stage") tool))
(assert (.mkdir (java.io.File. output)))
(assert (.mkdir (java.io.File. (str output "/stage"))))
(store/init! (str output "/store"))
(defn receipt-backend [] (fb/open-backend (str output "/store") (str output "/stage") tool))
(defn receipt-db [] (core/open {:storage (receipt-backend) :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(defn config [events transform]
 {:backend frontier-backend :ref-name "frontier" :database-id (:database-id checkpoint)
  :fetch-entry! (fn [{m :manifest}]
                  (swap! events conj :basis)
                  (verify-location! m location signed-proof-cid signed-basis signed-policy)
                  (let [raw (or (catalog/get-group-block h signed-proof-cid) (throw (ex-info "missing proof" {})))]
                    (writer/validate-ipld-block {:cid signed-proof-cid :bytes raw})
                    (transform (edn/read-string (String. ^bytes raw "UTF-8")))))
})
(defn verification-config []
 {:digest! sha :checkpoint! (signed/checkpoint-reader engine)
  :verify-location! (fn [{m :manifest}] (verify-location! m location signed-proof-cid signed-basis signed-policy))
  :registry (fn [scope] (if (= scope {:role :issuer :tenant "test" :epoch 7})
                         (let [k {:key/id "fixture-key" :key/algorithm :ed25519 :key/material (ed/public-key (vec (repeat 32 3)))}] [k k]) []))
  :algorithm :ed25519
  :issuer-authorized? (fn [{:keys [issuer key]}] (and (= issuer signer) (= (:key/id key) "fixture-key")))
  :verify-with (fn [{:keys [key payload signature-value]}]
                 (ed/verify (:key/material key) (payload-bytes payload) signature-value))})
(defn run! [transform]
 (let [events (atom []) nonce (fn [_] (throw (ex-info "external nonce callback called" {}))) auth (:authorize! opts) ev (:evaluate! opts)
       request* (assoc request :nonce "fixture-persistent-entry-20261114")
       req (-> opts (dissoc :commit!) (assoc :request request*
              :commit {:database (receipt-db) :expected-basis-cid nil :receipt-cid-fn id/value-cid}
              :authority-decision {:decision/status :allow :decision/runtime-capability-spec
                {:capability/action :object/read :capability/resource (get-in query [:scope :resources])
                 :capability/tenant (:tenant request*) :capability/principal (:principal request*)}})
            (assoc-in [:authority :consume-nonce!] (fn [n] (swap! events conj :nonce) (nonce n)))
            (assoc :authorize! (fn [q] (swap! events conj :authorize) (auth q))
                   :evaluate! (fn [q d] (swap! events conj :evaluate) (ev q d))))
       original causal/execution-receipt-sink
       result (try (with-redefs [causal/execution-receipt-sink (fn [db cfg] (let [sink (original db cfg)] (fn [r] (swap! events conj :commit) (sink r))))]
                     (read/read-registry-persistent! req (config events transform) (verification-config) {:backend (receipt-backend) :single-writer? true}))
                   (catch Exception e {:error (ex-data e)}))]
   {:events @events :result result}))
(let [before (:root (store/read-store (str output "/store"))) r (run! identity) after (:root (store/read-store (str output "/store")))]
 (assert (= [:basis] (:events r)))
 (assert (= :duplicate-key-id (get-in r [:result :error :kotobase.execution-keys/reason])))
 (assert (= before after))
 (prn {:duplicate :rejected :events (:events r) :root :unchanged :pass true}))
(shutdown-agents)
