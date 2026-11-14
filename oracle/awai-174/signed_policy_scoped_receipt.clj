;; Hosted composition; no claim of trusted identity or independently witnessed state.
(require '[clojure.string :as str])
(def project (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "oracle root required" {}))))
(load-string (first (str/split (slurp (str project "/oracle/awai-174/signed_policy_forest_multi.clj")) #"\(case \(first \*command-line-args\*\)" 2)))
(require '[kotobase.governed-execution :as governed] '[kotobase.execution-contract :as contract]
         '[kotobase.causal-commit :as causal] '[kotobase.core :as core])
(import '[java.security KeyFactory Signature KeyPairGenerator] '[java.security.spec X509EncodedKeySpec] '[java.util Base64])
(def receipt-root (str base "/scoped-receipts"))
(def receipt-dir (str receipt-root "/store"))
(def receipt-stage (str receipt-root "/staging"))
(defn rbackend [] (blocks/open-backend receipt-dir receipt-stage tool))
(defn open-db [backend] (core/open {:storage backend :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(defn verify-jca [public-key payload signature]
  (let [v (Signature/getInstance "Ed25519")]
    (.initVerify v public-key) (.update v (.getBytes ^String payload "UTF-8"))
    (.verify v (.decode (Base64/getDecoder) ^String signature))))
(defn source-context []
  (let [db (backend) basis-cid (:cid (storage/-read-ref db "signed-basis")) get-block #(storage/get-block db %)
        entry (read-block get-block basis-cid) eng (make-engine {:put! (fn [& _] (throw (ex-info "readonly" {}))) :get-fn get-block})
        snap (engine/open-snapshot eng (engine/restore-state eng (:physical-root entry)))
        p (policy-at eng snap) pcid (policy/policy-cid p)]
    (ensure! (and basis-cid p pcid) "missing basis or policy")
    {:basis-cid basis-cid :entry entry :engine eng :snapshot snap :policy p :policy-cid pcid :source-db db}))
(defn location-for [ctx]
  {:format :awai.fixture/basis-location-v1 :signed-basis (:basis-cid ctx)
   :physical-root (get-in ctx [:entry :physical-root]) :policy (:policy-cid ctx)})
(defn admit! [ctx request manifest]
  (let [location (location-for ctx) entry (:entry ctx)
        verify (signed/verifier {:digest! sha :checkpoint! (signed/checkpoint-reader (:engine ctx))
                  :verify-location! (fn [{:keys [entry manifest]}]
                                      (and (= (:location/manifest manifest) (id/value-cid location))
                                           (= (:physical-root entry) (:physical-root location))
                                           (= (:policy location) (:policy-cid ctx))))
                  :verify-signature! (fn [{:keys [issuer key-id payload signature]}]
                                       (and (= issuer "fixture-issuer") (= key-id "fixture-key")
                                            (true? (ed/verify public-key (bytes-of payload) signature))))})]
    (ensure! (true? (verify {:entry entry :request request :manifest manifest})) "basis admission failed")))
(defn verify-bundle! [get-block receipt ctx public-key]
  (let [request (read-block get-block (:request/digest receipt))
        manifest (read-block get-block (:execution/manifest receipt))]
    (contract/validate-manifest-request! manifest request)
    (ensure! (verify-jca public-key (id/payload-cid manifest) (:signature manifest)) "manifest signature invalid")
    (ensure! (verify-jca public-key (id/payload-cid receipt) (:signature receipt)) "receipt signature invalid")
    (admit! ctx request manifest)
    true))
(case (first *command-line-args*)
 "write"
 (do (ensure! (.mkdir (java.io.File. receipt-root)) "receipt output already exists")
     (ensure! (.mkdir (java.io.File. receipt-stage)) "receipt staging already exists")
     (store/init! receipt-dir)
     (let [ctx (source-context) root (get-in ctx [:entry :physical-root]) pcid (:policy-cid ctx)
           p (:policy ctx) src (source-for (:engine ctx) (:snapshot ctx)) db (rbackend)
           pair (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
           sign (fn [record] (let [s (Signature/getInstance "Ed25519")]
                               (.initSign s (.getPrivate pair)) (.update s (.getBytes ^String (id/payload-cid record) "UTF-8"))
                               (.encodeToString (Base64/getEncoder) (.sign s))))
           verify (fn [{:keys [payload-cid signature]}] (verify-jca (.getPublic pair) payload-cid signature))
           resource (str "kotoba://basis/" root) dseed (vec (repeat 32 11))
           token (chain/root {:issuer-seed dseed :grant {:caps #{[:datom/read-protected resource]} :before 1800000000}
                              :caveats [] :next-seed (vec (repeat 32 12))})
           check (fn [r] (chain/verify (chain/present token) {:roots #{(ed/public-key dseed)}
                             :request {:req/kind :datom/read-protected :req/resource r :req/now 1700000000}}))
           allow (check resource) deny (check "kotoba://basis/wrong")
           _ (ensure! (and (:dango/allowed? allow) (not (:dango/allowed? deny))) "Dango fixture failed")
           unsigned {:execution/version 1 :data/commit root :authority/policy pcid :authority/epoch 7
                     :location/manifest (id/value-cid (location-for ctx)) :schema/root "fixture-schema"
                     :parent nil :issued-at "2026-09-02T14:00:00Z"}
           manifest (assoc unsigned :signature (sign unsigned)) evaluations (atom 0) commits (atom 0)
           execute (fn [limit nonce decision ref-name reject-semantic?]
            (let [q {:find [:file/name] :where '[[?e :file/name ?name]]
                     :scope {:tenant "test" :resources #{resource} :purpose :audit :basis root} :limit limit}
                  request {:request/version 1 :principal "fixture-principal" :tenant "test" :graph "fixture-graph"
                           :operation :read :query/digest (id/value-cid q) :base/commit root :authority/policy pcid
                           :authority/epoch 7 :nonce nonce :expires-at "2026-09-02T15:00:00Z"}
                  sink (fn [receipt]
                         (let [ack (blocks/publish-scoped! db ref-name nil
                                     (fn [staged]
                                       (storage/-put-blocks! staged (mapv encode-block [request manifest]))
                                       (let [internal ((causal/execution-receipt-sink (open-db staged)
                                                         {:expected-basis-cid nil :receipt-cid-fn id/value-cid}) receipt)]
                                         {:target (:receipt/commit-cid internal) :value internal}))
                                     (fn [get-block _]
                                       (and (not reject-semantic?) (verify-bundle! get-block receipt ctx (.getPublic pair)))))
                               commit (:receipt/commit-cid ack)]
                           (ensure! (= [receipt] (:receipt/records (causal/receipt-at (open-db db) commit (:receipt/cid ack)))) "receipt readback mismatch")
                           (verify-bundle! #(storage/get-block db %) receipt ctx (.getPublic pair))
                           (swap! commits inc) ack))]
              (try
                (admit! ctx request manifest)
                (governed/execute! {:request request :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (constantly true)}
                  :value-cid id/value-cid :verify verify :plan-digest (constantly "fixture-plan")
                  :cost (constantly {:dependent-hops 0 :requests 0 :bytes 0 :cache-profile :unknown}) :implementation/build "fixture"
                  :sign sign :commit! sink :authorize! (fn [_] {:allowed? (true? (:dango/allowed? decision)) :projection #{:file/name} :basis root :policy-cid pcid})
                  :schema {:file/name {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
                  :evaluate! (fn [received _] (swap! evaluations inc)
                    (let [visible (policy/visible-for p #{policy/read-protected-capability})]
                      (mapv (fn [[name]] {:file/name name})
                            (datalog/q src {:find '[?name] :where (:where received)} #(visible (update % :p str))))))})
                (catch Exception e {:error (ex-data e)}))))
           allowed (execute 3 "allowed" allow "receipt-allow" false)
           over (execute 2 "over" allow "receipt-over" false)
           failed (execute 3 "failed" allow "receipt-failed" true)
           denied (execute 3 "denied" deny "receipt-deny" false)
           a-id (get-in allowed [:provenance :receipt-cid]) d-id (get-in denied [:error :kotobase.governed-execution/deny-receipt-cid])]
       (ensure! (= 3 (count (:rows allowed))) "wrong allowed rows")
       (ensure! (= :result-limit-exceeded (get-in over [:error :kotobase.query/reason])) "overflow not refused")
       (ensure! (= :authority-denied (get-in denied [:error :kotobase.governed-execution/reason])) "denial missing")
       (ensure! (= :closure-rejected (get-in failed [:error :type])) "semantic failure not refused")
       (ensure! (nil? (get-in failed [:rows])) "semantic failure returned rows")
       (ensure! (= 3 @evaluations) "invalid evaluation count")
       (ensure! (= 2 @commits) "invalid commit count")
       (ensure! (nil? (storage/-read-ref db "receipt-over")) "overflow published a ref")
       (ensure! (nil? (storage/-read-ref db "receipt-failed")) "semantic failure published a ref")
       (ensure! (and (string? a-id) (string? d-id)) "receipt identifiers missing")
       (spit (str receipt-root "/ids.edn") (pr-str {:allow-id a-id :deny-id d-id :basis (:basis-cid ctx)
                      :public-key (.encodeToString (Base64/getEncoder) (.getEncoded (.getPublic pair)))}))
       (prn {:mode :write :signed-basis :verified :policy :recovered :rows 3 :overflow :refused
             :dango-denial :receipted :semantic-failure :refused :receipts :scoped-forest-ref :pass true})))
 "read"
 (let [ctx (source-context) {:keys [allow-id deny-id basis public-key]} (edn/read-string (slurp (str receipt-root "/ids.edn")))
       key (.generatePublic (KeyFactory/getInstance "Ed25519") (X509EncodedKeySpec. (.decode (Base64/getDecoder) ^String public-key)))
       db (rbackend) get-block #(storage/get-block db %)]
   (ensure! (= basis (:basis-cid ctx)) "basis ref changed")
   (doseq [[kind ref-name cid] [[:allow "receipt-allow" allow-id] [:deny "receipt-deny" deny-id]]]
     (let [commit (:cid (storage/-read-ref db ref-name)) receipt (first (:receipt/records (causal/receipt-at (open-db db) commit cid)))]
       (ensure! (= kind (:authority/decision receipt)) "receipt decision mismatch")
       (verify-bundle! get-block receipt ctx key)
       (let [request (read-block get-block (:request/digest receipt))
             manifest (read-block get-block (:execution/manifest receipt))]
         (ensure! (denied? #(admit! (assoc ctx :basis-cid "other") request manifest)) "wrong basis location accepted")
         (ensure! (denied? #(admit! (assoc-in ctx [:entry :signature] (vec (repeat 64 0))) request manifest)) "bad basis signature accepted")
         (ensure! (denied? #(admit! (assoc ctx :policy-cid "other") request manifest)) "wrong policy accepted"))
       (ensure! (denied? #(verify-bundle! (fn [c] (when-not (= c (:request/digest receipt)) (get-block c))) receipt ctx key)) "missing request accepted")))
   (ensure! (nil? (storage/-read-ref db "receipt-over")) "overflow ref appeared")
   (ensure! (nil? (storage/-read-ref db "receipt-failed")) "failed ref appeared")
   (prn {:mode :read :signed-basis :verified :policy :recovered :allow :verified :deny :verified
         :missing-request :refused :wrong-basis-policy-signature :refused :overflow-ref :absent :failed-ref :absent :pass true}))
 (throw (ex-info "write or read required" {})))
(shutdown-agents)
