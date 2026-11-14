;; Hosted fixture with actual Prolly, Datalog, policy, signed proof and Forest sink.
(require '[clojure.string :as str] '[clojure.edn :as edn]
         '[kotobase.nonce-store :as nonce-store] '[kotobase.governed-read :as read] '[kotobase.signed-basis :as signed] '[kotobase.signed-basis-keys :as basis-keys]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[otehon.kotobase-forest-blocks :as fb] '[grant.causal-trust :as trust] '[identity.causal :as ic] '[kotobase.storage.memory :as memory] '[kotobase.authority-execution-link :as link] '[kotobase.authority-commit-attestation :as attest] '[kotobase.governed-host :as host])
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
(def authority-store (memory/memory-store))
(def authority-db (core/open {:storage authority-store :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def authority-basis (core/commit-at! authority-db nil [["authority" "scope" "fixture"]]))
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
(def authority-ack (causal/persist-decision! authority-db authority-receipt authority-basis))
(def authority-commit (:receipt/commit-cid authority-ack))
(def authority-seed (vec (repeat 32 5)))
(def unsigned-attestation {:format attest/format-id :commit-cid authority-commit
 :issuer "did:key:fixture-authority" :tenant tenant :epoch 7})
(def attestation (assoc unsigned-attestation :signature (sign "authority" authority-seed unsigned-attestation)))
(def authority-signature (get-in attestation [:signature :signature/value]))
(def authority-entry {:key/id "authority" :key/algorithm :ed25519 :key/material (ed/public-key authority-seed)})
(def authority-registry (fn [scope] (if (= scope {:role :issuer :tenant tenant :epoch 7}) [authority-entry] [])))
(def attestation-config {:attestation attestation :registry authority-registry :algorithm :ed25519
 :issuer-authorized? (fn [{:keys [issuer key]}] (and (= issuer "did:key:fixture-authority") (= (:key/id key) "authority")))
 :verify-with (fn [{:keys [key payload-cid signature-value]}] (ed/verify (:key/material key) (payload-bytes payload-cid) signature-value))})
(def verify-authority-commit (attest/verifier attestation-config))
(def evidence {:authority-receipt authority-receipt
 :verify-authority! (causal/decision-receipt-authenticator authority-db {:commit-cid authority-commit :verify-commit! verify-authority-commit})})
(def ^:dynamic *deny-query?* false)
(def host-config {:authority-database authority-db :authority-receipt authority-receipt
 :attestation attestation :registry authority-registry :algorithm :ed25519
 :issuer-authorized? (:issuer-authorized? attestation-config) :verify-with (:verify-with attestation-config)})
(defn run-host! [nonce-value host-config invalid-link? deny-query?]
 (let [events (atom []) auth (:authorize! opts) ev (:evaluate! opts)
       request* (assoc request :nonce nonce-value)
       req (-> opts (dissoc :commit!)
        (assoc :request request* :commit {:database (receipt-db) :expected-basis-cid nil :receipt-cid-fn id/value-cid}
               :authority-decision real-decision
               :verify (fn [x] (if (and invalid-link? (= :authority-execution-link (:record x))) false ((:verify opts) x)))
               :authorize! (fn [q] (swap! events conj :authorize) (cond-> (auth q) deny-query? (assoc :allowed? false)))
               :evaluate! (fn [q d] (swap! events conj :evaluate) (ev q d))))
       result (try (host/read-registry-signed-authority! req (config events identity) (verification-config)
                    {:backend (receipt-backend) :single-writer? false} host-config)
                   (catch Exception e {:error (ex-data e)}))]
  {:events @events :result result}))
(let [before (memory/snapshot memory-backend)
      bad-key (run-host! "bad-key" (assoc host-config :registry (constantly [])) false false)
      bad-issuer (run-host! "bad-issuer" (assoc host-config :issuer-authorized? (constantly false)) false false)
      override (run-host! "override" (assoc host-config :verify-authority! (constantly true)) false false)]
 (doseq [r [bad-key bad-issuer override]] (assert (empty? (:events r))) (assert (some? (get-in r [:result :error]))))
 (assert (= before (memory/snapshot memory-backend))))
(let [bad (run-host! "bad-link-host" host-config true false)]
 (assert (= [:basis :authorize :evaluate] (:events bad)))
 (assert (= :signature-refused (get-in bad [:result :error :kotobase.authority-execution-link/reason])))
 (assert (nil? (get-in bad [:result :rows]))))
(let [r (run-host! "host-allow" host-config false false) result (:result r)
      cid (get-in result [:provenance :receipt-cid]) commit (get-in result [:provenance :receipt-commit-cid])
      records (:receipt/records (causal/receipt-at (receipt-db) commit cid))
      replay (run-host! "host-allow" host-config false false)]
 (when (:error result) (throw (ex-info "host read refused" (:error result))))
 (assert (= [:basis :authorize :evaluate] (:events r))) (assert (= 1 (count (:rows result))))
 (assert (= 3 (count records))) (assert (= authority-receipt (second records)))
 (assert (= :nonce-replayed (get-in replay [:result :error :kotobase.authority-window/reason]))))
(let [r (run-host! "host-deny" host-config false true) err (get-in r [:result :error])
      cid (:kotobase.governed-execution/deny-receipt-cid err)
      commit (:kotobase.governed-execution/deny-receipt-commit-cid err)
      records (:receipt/records (causal/receipt-at (receipt-db) commit cid))]
 (assert (= [:basis :authorize] (:events r)))
 (assert (= :authority-denied (:kotobase.governed-execution/reason err)))
 (assert (= :deny (:authority/decision (first records)))) (assert (= 3 (count records))))
(prn {:host-composition :verified :signed-authority :scoped :allow :verified :deny :receipted :override :refused :replay :refused :backend :memory :pass true})
(shutdown-agents)
