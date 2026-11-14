;; Hosted fixture with actual Prolly, Datalog, policy, signed proof and Forest sink.
(require '[clojure.string :as str] '[clojure.edn :as edn]
         '[kotobase.nonce-store :as nonce-store] '[kotobase.governed-read :as read] '[kotobase.signed-basis :as signed] '[kotobase.signed-basis-keys :as basis-keys]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[otehon.kotobase-forest-blocks :as fb] '[grant.causal-trust :as trust] '[identity.causal :as ic] '[kotobase.storage.memory :as memory] '[kotobase.authority-execution-link :as link])
(load-file "nixos/otehon/storage/oracle/proof_location_common.clj")
(def frontier-output (or (System/getenv "FRONTIER_FOREST_OUTPUT") (throw (ex-info "frontier output required" {}))))
(def output (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "receipt output required" {}))))
(def frontier-backend (fb/open-backend (str frontier-output "/store") (str frontier-output "/stage") tool))
(def memory-backend (memory/memory-store))
(defn receipt-backend [] memory-backend)
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
(def authority-receipt
 (trust/receipt {:causal.receipt/id "fixture-decision-receipt"
  :causal.receipt/intent-cid (:decision/trust-intent-cid real-decision)
  :causal.receipt/principal principal :causal.receipt/epoch-cid (:decision/trust-epoch-cid real-decision)
  :causal.receipt/policy-cid (:decision/trust-policy-cid real-decision)
  :causal.receipt/basis-cid (:decision/trust-basis-cid real-decision)
  :causal.receipt/claim-cids (:decision/trust-claim-cids real-decision)
  :causal.receipt/decision real-decision :causal.receipt/outcome {:outcome/status :allowed}
  :causal.receipt/at "2026-08-27T01:00:00Z"}))
(def evidence {:authority-receipt authority-receipt :verify-authority! (constantly true)})
(def ^:dynamic *deny-query?* false)
(defn run! [transform decision evidence nonce-value invalid-link?]
 (let [events (atom []) nonce (fn [_] (throw (ex-info "external nonce callback called" {}))) auth (:authorize! opts) ev (:evaluate! opts)
       request* (assoc request :nonce nonce-value)
       req (-> opts (dissoc :commit!) (assoc :request request*
              :commit {:database (receipt-db) :expected-basis-cid nil :receipt-cid-fn id/value-cid}
              :authority-decision decision)
            (assoc-in [:authority :consume-nonce!] (fn [n] (swap! events conj :nonce) (nonce n)))
            (assoc :verify (fn [x] (if (and invalid-link? (= :authority-execution-link (:record x))) false ((:verify opts) x)))
                   :authorize! (fn [q] (swap! events conj :authorize) (cond-> (auth q) *deny-query?* (assoc :allowed? false)))
                   :evaluate! (fn [q d] (swap! events conj :evaluate) (ev q d))))
       original causal/execution-receipt-sink
       result (try (with-redefs [causal/execution-receipt-sink (fn [db cfg] (let [sink (original db cfg)] (fn [r] (swap! events conj :commit) (sink r))))]
                     (read/read-registry-linked-persistent! req (config events transform) (verification-config) {:backend (receipt-backend) :single-writer? false} evidence))
                   (catch Exception e {:error (ex-data e)}))]
   {:events @events :result result}))
(let [before (memory/snapshot memory-backend)
      mismatch (run! identity real-decision (assoc-in evidence [:authority-receipt :causal.receipt/decision] denied-decision) "deny" false)
      refused (run! identity real-decision (assoc evidence :verify-authority! (constantly false)) "refuse" false)]
 (assert (empty? (:events mismatch))) (assert (= :authority-receipt-decision-mismatch (get-in mismatch [:result :error :kotobase.governed-read/reason])))
 (assert (empty? (:events refused)))
 (assert (= :authority-receipt-refused (get-in refused [:result :error :kotobase.governed-read/reason])))
 (assert (= before (memory/snapshot memory-backend))))
(let [calls (atom 0) flaky (assoc evidence :verify-authority! (fn [_] (= 1 (swap! calls inc))))
      failed (run! identity real-decision flaky "auth-recheck" false)]
 (assert (= 2 @calls))
 (assert (= :authority-receipt-refused (get-in failed [:result :error :kotobase.causal-commit/reason])))
 (assert (nil? (get-in failed [:result :rows]))))
(let [failed (run! identity real-decision evidence "bad-link" true)]
 (assert (= [:basis :authorize :evaluate] (:events failed)))
 (assert (= :signature-refused (get-in failed [:result :error :kotobase.authority-execution-link/reason])))
 (assert (nil? (get-in failed [:result :rows]))))
(let [r (run! identity real-decision evidence "linked-success" false) result (:result r)
      cid (get-in result [:provenance :receipt-cid]) commit (get-in result [:provenance :receipt-commit-cid])
      records (:receipt/records (causal/receipt-at (receipt-db) commit cid))
      before (memory/snapshot memory-backend) replay (run! identity real-decision evidence "linked-success" false)]
 (when (:error result) (throw (ex-info "linked read refused" (:error result))))
 (assert (= [:basis :authorize :evaluate] (:events r)))
 (assert (= 1 (count (:rows result))))
 (assert (= 3 (count records)))
 (assert (= (:execution/receipt result) (first records)))
 (assert (= authority-receipt (second records)))
 (assert (true? (link/verify! {:link (nth records 2) :request (assoc request :nonce "linked-success") :query query
  :execution-receipt (first records) :authority-receipt (second records) :value-cid id/value-cid :verify (:verify opts)})))
 (assert (= [:basis] (:events replay)))
 (assert (= :nonce-replayed (get-in replay [:result :error :kotobase.authority-window/reason])))
 (assert (= before (memory/snapshot memory-backend)))
 (prn {:linked-read :allowed :rows 1 :records 3 :tamper :withheld :authority-refusal :before-basis :replay :refused :backend :memory :pass true}))
(let [denied (binding [*deny-query?* true] (run! identity real-decision evidence "query-denial" false))
      err (get-in denied [:result :error])
      receipt-cid (:kotobase.governed-execution/deny-receipt-cid err)
      commit-cid (:kotobase.governed-execution/deny-receipt-commit-cid err)
      records (:receipt/records (causal/receipt-at (receipt-db) commit-cid receipt-cid))
      prior (memory/snapshot memory-backend)
      replay (binding [*deny-query?* true] (run! identity real-decision evidence "query-denial" false))]
 (assert (= :authority-denied (:kotobase.governed-execution/reason err)))
 (assert (= [:basis :authorize] (:events denied)))
 (assert (= 3 (count records)))
 (assert (= :deny (:authority/decision (first records))))
 (assert (nil? (:result/root (first records))))
 (assert (= authority-receipt (second records)))
 (assert (true? (link/verify! {:link (nth records 2) :request (assoc request :nonce "query-denial") :query query
  :execution-receipt (first records) :authority-receipt (second records) :value-cid id/value-cid :verify (:verify opts)})))
 (assert (= :nonce-replayed (get-in replay [:result :error :kotobase.authority-window/reason])))
 (assert (= prior (memory/snapshot memory-backend)))
 (prn {:query-policy :denied :evaluator :not-called :linked-records 3 :readback :verified :replay :refused :backend :memory :pass true}))
(shutdown-agents)
