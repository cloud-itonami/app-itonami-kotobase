;; Hosted fixture with actual Prolly, Datalog, policy, signed proof and Forest sink.
(require '[clojure.string :as str] '[clojure.edn :as edn]
         '[kotobase.nonce-store :as nonce-store] '[kotobase.governed-read :as read] '[kotobase.signed-basis :as signed] '[kotobase.signed-basis-keys :as basis-keys]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[otehon.kotobase-forest-blocks :as fb] '[grant.causal-trust :as trust] '[identity.causal :as ic] '[kotobase.storage.memory :as memory] '[kotobase.authority-execution-link :as link] '[kotobase.authority-commit-attestation :as attest])
(load-file "nixos/otehon/storage/oracle/proof_location_common.clj")
(def frontier-output (or (System/getenv "FRONTIER_FOREST_OUTPUT") (throw (ex-info "frontier output required" {}))))
(def output (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "receipt output required" {}))))
(def frontier-backend (fb/open-backend (str frontier-output "/store") (str frontier-output "/stage") tool))
(def mode (or (System/getenv "LINKED_MODE") (throw (ex-info "mode required" {}))))
(def stage-root (or (System/getenv "LINKED_STAGE") (throw (ex-info "stage required" {}))))
(def ids-path (str output "/linked-ids.edn"))
(def ids (when (not= mode "write") (edn/read-string (slurp ids-path))))
(def previous-ids (edn/read-string (slurp (str output "/linked-after-reboot.edn"))))
(when (= mode "write")
 (assert (.mkdir (java.io.File. output)))
 (store/init! (str output "/store")))
(def memory-backend (fb/open-backend (str output "/store") stage-root tool))
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
(def authority-store memory-backend)
(def authority-db (core/open {:storage authority-store :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def authority-basis (if (= mode "write") (core/commit-at! authority-db nil [["authority" "scope" "fixture"]]) (:authority-basis ids)))
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
  :causal.trust/policy-cid "fixture-policy" :causal.trust/intent-cid "fixture-intent" :causal.trust/basis-cid authority-basis :causal.trust/now "2026-08-27T01:00:00Z"})
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
(def authority-ack (if (= mode "write") (causal/persist-decision! authority-db authority-receipt authority-basis) {:receipt/commit-cid (:authority-commit ids)}))
(def authority-commit (:receipt/commit-cid authority-ack))
(def authority-seed (vec (repeat 32 5)))
(def attestation-key "awai.fixture/scoped-authority-v1")
(def unsigned-attestation {:format attest/format-id :commit-cid authority-commit
 :issuer "did:key:fixture-authority" :tenant tenant :epoch 7})
(def attestation
 (if (= mode "advance")
  (let [candidate (assoc unsigned-attestation :signature (sign "authority" authority-seed unsigned-attestation))]
   (assert (nil? (get (:state (store/read-store (str output "/store"))) attestation-key)))
   (store/transact! (str output "/store") :put {:key attestation-key :value (pr-str candidate)})
   candidate)
  (edn/read-string (or (get (:state (store/read-store (str output "/store"))) attestation-key)
                       (throw (ex-info "missing persisted attestation" {}))))))
(def authority-entry {:key/id "authority" :key/algorithm :ed25519 :key/material (ed/public-key authority-seed)})
(def authority-registry (fn [scope] (if (= scope {:role :issuer :tenant tenant :epoch 7}) [authority-entry] [])))
(def verify-authority-commit (attest/verifier {:attestation attestation :registry authority-registry :algorithm :ed25519
 :issuer-authorized? (fn [{:keys [issuer key]}] (and (= issuer "did:key:fixture-authority") (= (:key/id key) "authority")))
 :verify-with (fn [{:keys [key payload-cid signature-value]}] (ed/verify (:key/material key) (payload-bytes payload-cid) signature-value))}))
(def evidence {:authority-receipt authority-receipt
 :verify-authority! (causal/decision-receipt-authenticator authority-db {:commit-cid authority-commit :verify-commit! verify-authority-commit})})
(def ^:dynamic *deny-query?* false)
(defn run! [transform decision evidence nonce-value invalid-link?]
 (let [events (atom []) nonce (fn [_] (throw (ex-info "external nonce callback called" {}))) auth (:authorize! opts) ev (:evaluate! opts)
       request* (assoc request :nonce nonce-value)
       req (-> opts (dissoc :commit!) (assoc :request request*
              :commit {:database (receipt-db) :expected-basis-cid (:commit previous-ids) :receipt-cid-fn id/value-cid}
              :authority-decision decision)
            (assoc-in [:authority :consume-nonce!] (fn [n] (swap! events conj :nonce) (nonce n)))
            (assoc :verify (fn [x] (if (and invalid-link? (= :authority-execution-link (:record x))) false ((:verify opts) x)))
                   :authorize! (fn [q] (swap! events conj :authorize) (cond-> (auth q) *deny-query?* (assoc :allowed? false)))
                   :evaluate! (fn [q d] (swap! events conj :evaluate) (ev q d))))
       original causal/execution-receipt-sink
       result (try (with-redefs [causal/execution-receipt-sink (fn [db cfg] (let [sink (original db cfg)] (fn [r] (swap! events conj :commit) (sink r))))]
                     (read/read-registry-linked-persistent! req (config events transform) (verification-config) {:backend (receipt-backend) :single-writer? true} evidence))
                   (catch Exception e {:error (ex-data e)}))]
   {:events @events :result result}))
(let [nonce "guest-scoped-authority-after-reboot-20261114"
      result-path (str output "/linked-scoped-authority.edn")
      before (:root (store/read-store (str output "/store")))
      r (run! identity real-decision evidence nonce false)]
 (if (= mode "advance")
  (let [result (:result r) cid (get-in result [:provenance :receipt-cid]) commit (get-in result [:provenance :receipt-commit-cid])
        records (:receipt/records (causal/receipt-at (receipt-db) commit cid))
        previous (:receipt/records (causal/receipt-at (receipt-db) (:commit previous-ids) (:receipt previous-ids)))]
   (when (:error result) (throw (ex-info "postboot linked read failed" (:error result))))
   (assert (= [:basis :authorize :evaluate] (:events r)))
   (assert (= 1 (count (:rows result)))) (assert (= 3 (count records))) (assert (= 3 (count previous)))
   (assert (= authority-receipt (second records)))
   (assert (true? (link/verify! {:link (nth records 2) :request (assoc request :nonce nonce) :query query
     :execution-receipt (first records) :authority-receipt (second records) :value-cid id/value-cid :verify (:verify opts)})))
   (assert (= (:commit previous-ids) (:receipt/basis-cid (causal/receipt-at (receipt-db) commit cid))))
   (spit result-path (pr-str {:receipt cid :commit commit :previous-commit (:commit previous-ids)}))
   (prn {:mode :advance :issuer-scope :verified :new-nonce :accepted :rows 1 :records 3 :previous :readable :receipt cid :commit commit :pass true}))
  (let [latest (edn/read-string (slurp result-path)) after (:root (store/read-store (str output "/store")))
        records (:receipt/records (causal/receipt-at (receipt-db) (:commit latest) (:receipt latest)))
        previous (:receipt/records (causal/receipt-at (receipt-db) (:commit previous-ids) (:receipt previous-ids)))]
   (assert (= [:basis] (:events r)))
   (assert (= :nonce-replayed (get-in r [:result :error :kotobase.authority-window/reason])))
   (assert (= before after)) (assert (= 3 (count records))) (assert (= 3 (count previous)))
   (assert (= (:commit previous-ids) (:receipt/basis-cid (causal/receipt-at (receipt-db) (:commit latest) (:receipt latest)))) )
   (assert (true? (link/verify! {:link (nth records 2) :request (assoc request :nonce nonce) :query query
     :execution-receipt (first records) :authority-receipt (second records) :value-cid id/value-cid :verify (:verify opts)})))
   (assert (ed/verify (ed/public-key executor) (payload-bytes (id/payload-cid (first records))) (get-in (first records) [:signature :signature/value])))
   (prn {:mode :replay-after-advance :issuer-scope :verified :replay :refused :root :unchanged :old-and-new :verified :pass true}))))
(shutdown-agents)
