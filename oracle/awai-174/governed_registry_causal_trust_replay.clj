;; Hosted fixture with actual Prolly, Datalog, policy, signed proof and Forest sink.
(require '[clojure.string :as str] '[clojure.edn :as edn]
         '[kotobase.nonce-store :as nonce-store] '[kotobase.governed-read :as read] '[kotobase.signed-basis :as signed] '[kotobase.signed-basis-keys :as basis-keys]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[otehon.kotobase-forest-blocks :as fb] '[grant.causal-trust :as trust] '[identity.causal :as ic])
(load-file "nixos/otehon/storage/oracle/proof_location_common.clj")
(def frontier-output (or (System/getenv "FRONTIER_FOREST_OUTPUT") (throw (ex-info "frontier output required" {}))))
(def output (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "receipt output required" {}))))
(def frontier-backend (fb/open-backend (str frontier-output "/store") (str frontier-output "/stage") tool))
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
                         [{:key/id "fixture-key" :key/algorithm :ed25519
                           :key/material (ed/public-key (vec (repeat 32 3)))}] []))
  :algorithm :ed25519
  :issuer-authorized? (fn [{:keys [issuer key]}] (and (= issuer signer) (= (:key/id key) "fixture-key")))
  :verify-with (fn [{:keys [key payload signature-value]}]
                 (ed/verify (:key/material key) (payload-bytes payload) signature-value))})
(def principal (:principal request))
(def tenant (:tenant request))
(def resources (get-in query [:scope :resources]))
(def trust-input
 {:causal.trust/authority
  {:authority/principal {:principal/id principal :principal/kind :human :principal/authenticated? true :principal/tenant tenant :principal/assurance :high}
   :authority/actor {:actor/id :agent/reader :actor/kind :agent}
   :authority/intent {:intent/action :object/read :intent/resource resources}
   :authority/effects #{:object/read}
   :authority/grants [{:grant/id "fixture-grant" :grant/subject principal :grant/actions #{:object/read} :grant/resources #{resources} :grant/tenant tenant}]
   :authority/policy {:policy/public #{} :policy/deny-actions #{} :policy/min-assurance {} :policy/required-approvals {}}
   :authority/context {:context/now 1 :context/tenant tenant :context/approvals #{} :context/nonce-used? false}}
  :causal.trust/epoch (ic/epoch "fixture-epoch" principal {:sequence 0 :started-at "2026-08-27T00:00:00Z"})
  :causal.trust/claims [] :causal.trust/requirements []
  :causal.trust/policy-cid "fixture-policy" :causal.trust/intent-cid "fixture-intent" :causal.trust/basis-cid "fixture-basis" :causal.trust/now "2026-08-27T01:00:00Z"})
(def real-decision (trust/decide trust-input))
(def denied-decision (trust/decide (assoc-in trust-input [:causal.trust/authority :authority/policy :policy/deny-actions] #{:object/read})))
(defn run! [transform decision]
 (let [events (atom []) nonce (fn [_] (throw (ex-info "external nonce callback called" {}))) auth (:authorize! opts) ev (:evaluate! opts)
       request* (assoc request :nonce "fixture-post-reboot-new-nonce-20261114")
       req (-> opts (dissoc :commit!) (assoc :request request*
              :commit {:database (receipt-db) :expected-basis-cid (:commit (edn/read-string (slurp (str output "/result.edn")))) :receipt-cid-fn id/value-cid}
              :authority-decision decision)
            (assoc-in [:authority :consume-nonce!] (fn [n] (swap! events conj :nonce) (nonce n)))
            (assoc :authorize! (fn [q] (swap! events conj :authorize) (auth q))
                   :evaluate! (fn [q d] (swap! events conj :evaluate) (ev q d))))
       original causal/execution-receipt-sink
       result (try (with-redefs [causal/execution-receipt-sink (fn [db cfg] (let [sink (original db cfg)] (fn [r] (swap! events conj :commit) (sink r))))]
                     (read/read-registry-persistent! req (config events transform) (verification-config) {:backend (receipt-backend) :single-writer? true}))
                   (catch Exception e {:error (ex-data e)}))]
   {:events @events :result result}))
(let [before (:root (store/read-store (str output "/store"))) r (run! identity real-decision)
      after (:root (store/read-store (str output "/store"))) ids (edn/read-string (slurp (str output "/postboot-result.edn")))
      receipt (first (:receipt/records (causal/receipt-at (receipt-db) (:commit ids) (:receipt ids))))]

 (assert (= [:basis] (:events r)))
 (assert (= :nonce-replayed (get-in r [:result :error :kotobase.authority-window/reason])))
 (assert (= before after))
 (assert (= (:receipt ids) (id/value-cid receipt)))
 (assert (true? (ed/verify (ed/public-key (vec (repeat 32 4))) (payload-bytes (id/payload-cid receipt)) (get-in receipt [:signature :signature/value]))))
 (prn {:replay :refused :events (:events r) :root :unchanged :receipt (:receipt ids) :signature :valid :pass true}))
(let [r (run! identity denied-decision)]
 (assert (= :allow (:decision/status real-decision)))
 (assert (= :deny (:decision/status denied-decision)))
 (assert (empty? (:events r)))
 (assert (= :decision-not-allowed (get-in r [:result :error :kotobase.governed-read/reason])))
 (prn {:kernel :causal-trust :allow :nonce-replay-refused :deny :before-basis :pass true}))
(shutdown-agents)
