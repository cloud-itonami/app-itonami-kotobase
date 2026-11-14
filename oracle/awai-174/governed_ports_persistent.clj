;; Hosted composition: fixture keys and authority, real Forest publication.
(require '[clojure.string :as str])
(def project (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "oracle root required" {}))))
(load-string (first (str/split (slurp (str project "/oracle/awai-174/signed_policy_ports_receipt.clj"))
                               #"\(case \(first \*command-line-args\*\)" 2)))
(require '[kotobase.governed-read :as governed-read] '[kotobase.frontier-store :as frontier])
(def receipt-root (str base "/" (or (System/getenv "GOVERNED_PORTS_LABEL") "governed-ports-receipts")))
(def receipt-dir (str receipt-root "/store"))
(def receipt-stage (str receipt-root "/staging"))
(defn rbackend [] (blocks/open-backend receipt-dir receipt-stage tool))
(defn verify-receipt-commit! [db get-block commit receipt receipt-id expected-basis]
  (let [read-only (reify
                    storage/IBlockStore
                    (-put-blocks! [_ _] (throw (ex-info "read-only verifier" {})))
                    (-get-blocks [_ cids] (into {} (keep (fn [cid]
                                                         (when-let [b (get-block cid)] [cid b]))) cids))
                    storage/IRefStore
                    (-read-ref [_ name] (storage/-read-ref db name))
                    (-compare-and-set-ref! [_ _ _ _] (throw (ex-info "read-only verifier" {})))
                    storage/IBackendCapabilities
                    (-capabilities [_] (storage/-capabilities db)))
        proof (causal/receipt-at (open-db read-only) commit receipt-id)]
    (ensure! (= {:receipt/basis-cid expected-basis :receipt/records [receipt]}
                (select-keys proof [:receipt/basis-cid :receipt/records])) "pre-CAS receipt mismatch")
    true))
(case (first *command-line-args*)
 "write"
 (do (ensure! (.mkdir (java.io.File. receipt-root)) "output exists")
     (ensure! (.mkdir (java.io.File. receipt-stage)) "stage exists")
     (store/init! receipt-dir)
     (let [ctx (source-context) root (get-in ctx [:entry :physical-root]) pcid (:policy-cid ctx)
           p (:policy ctx) src (source-for (:engine ctx) (:snapshot ctx)) db (rbackend)
           pair (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
           sign (fn [record] (let [s (Signature/getInstance "Ed25519")]
                               (.initSign s (.getPrivate pair)) (.update s (.getBytes ^String (id/payload-cid record) "UTF-8"))
                               (.encodeToString (Base64/getEncoder) (.sign s))))
           verify (fn [{:keys [payload-cid signature]}] (verify-jca (.getPublic pair) payload-cid signature))
           resource (str "kotoba://basis/" root)
           unsigned {:execution/version 1 :data/commit root :authority/policy pcid :authority/epoch 7
                     :location/manifest (id/value-cid (location-for ctx)) :schema/root "fixture-schema"
                     :parent nil :issued-at "2026-09-02T14:00:00Z"}
           manifest (assoc unsigned :signature (sign unsigned))
           q {:find [:file/name] :where '[[?e :file/name ?name]]
              :scope {:tenant "test" :resources #{resource} :purpose :audit :basis root} :limit 3}
           request {:request/version 1 :principal "fixture-principal" :tenant "test" :graph "fixture-graph"
                    :operation :read :query/digest (id/value-cid q) :base/commit root :authority/policy pcid
                    :authority/epoch 7 :nonce "governed-ports" :expires-at "2026-09-02T15:00:00Z"}
           calls (atom 0)
           publication {:publish! (fn [{:keys [receipt prepared]}]
                          (swap! calls inc)
                          (blocks/publish-ports! db "governed-receipt" nil
                            (fn [ports]
                              (doseq [v [request manifest]]
                                (let [b (encode-block v)] ((:put! ports) (:cid b) (:bytes b))))
                              (let [commit (peer/commit! (:put! ports) (:get-fn ports) (:tx-data prepared)
                                                         (:receipt/basis-cid prepared) identity)]
                                {:target commit :value commit}))
                            (fn [get-block target]
                              (let [read-block! (fn [cid]
                                                  (if (and (System/getenv "GOVERNED_MISSING_TARGET") (= cid target))
                                                    (throw (ex-info "verifier target missing" {:type :missing-verifier-target}))
                                                    (or (get-block cid)
                                                        (throw (ex-info "verifier block missing" {:type :missing-verifier-block})))))]
                                (and (not (System/getenv "GOVERNED_REJECT"))
                                     (verify-bundle! read-block! receipt ctx (.getPublic pair))
                                     (verify-receipt-commit! db read-block! target receipt
                                                             (:receipt/cid prepared)
                                                             (:receipt/basis-cid prepared)))))))
                        :read-ref! #(:cid (storage/-read-ref db "governed-receipt"))}
           entry (:entry ctx) database-id (get-in entry [:commit :database-id])
           _ (admit! ctx request manifest)
           _ (frontier/accept-and-store! db "governed-frontier" database-id
               {:allow-initialize? true :candidate-root (:root entry) :path [entry]
                :verify-entry (fn [candidate] (verify-entry! (:engine ctx) candidate pcid))})
           basis-config {:backend db :ref-name "governed-frontier" :database-id database-id
                         :fetch-entry! (fn [_] entry)
                         :verify-entry! (fn [{:keys [entry request manifest]}]
                                          (admit! (assoc ctx :entry entry) request manifest)
                                          true)}
           evaluations (atom 0)
           read-request
                   {:commit {:database (open-db db) :expected-basis-cid nil :receipt-cid-fn id/value-cid :publication publication}
                    :authority-decision {:decision/status :allow :decision/runtime-capability-spec
                      {:capability/action :object/read :capability/resource #{resource}
                       :capability/tenant "test" :capability/principal "fixture-principal"}}
                    :request request :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (constantly true)}
                    :value-cid id/value-cid :verify verify :plan-digest (constantly "fixture-plan")
                    :cost (constantly {:dependent-hops 0 :requests 0 :bytes 0 :cache-profile :unknown}) :implementation/build "fixture"
                    :sign sign :authorize! (fn [_] {:allowed? true :projection #{:file/name} :basis root :policy-cid pcid})
                    :schema {:file/name {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
                    :evaluate! (fn [received _] (swap! evaluations inc)
                      (let [visible (policy/visible-for p #{policy/read-protected-capability})]
                        (mapv (fn [[name]] {:file/name name})
                              (datalog/q src {:find '[?name] :where (:where received)} #(visible (update % :p str))))))}
           nonce-config {:backend db :single-writer? true}
           result (governed-read/read-persistent! read-request basis-config nonce-config)
           cid (get-in result [:provenance :receipt-cid]) commit (get-in result [:provenance :receipt-commit-cid])]
       (ensure! (= 3 (count (:rows result))) "wrong rows")
       (ensure! (= 1 @calls) "publication count")
       (ensure! (= :nonce-replayed
                   (try (governed-read/read-persistent! read-request basis-config nonce-config) nil
                        (catch Exception e (:kotobase.authority-window/reason (ex-data e))))) "replay accepted")
       (ensure! (and (= 1 @calls) (= 1 @evaluations)) "replay evaluated or published")
       (spit (str receipt-root "/ids.edn") (pr-str {:receipt cid :commit commit :basis (:basis-cid ctx)
             :public-key (.encodeToString (Base64/getEncoder) (.getEncoded (.getPublic pair)))}))
       (prn {:mode :write :governed-persistent true :rows 3 :forest-ports true :nonce-replay :refused :pass true})))
 "nonce-present"
 (let [scope (id/value-cid {:format :kotobase.nonce/request-scope-v1 :tenant "test"
                            :principal "fixture-principal" :graph "fixture-graph" :epoch 7})
       key (id/value-cid {:format :kotobase.nonce/key-v1 :scope scope :nonce "governed-ports"})]
   (ensure! (some? (storage/-read-ref (rbackend) (str "nonce/v1/" key))) "nonce ref absent")
   (prn {:mode :nonce-present :nonce :consumed :pass true}))
 "no-ref"
 (do (ensure! (nil? (storage/-read-ref (rbackend) "governed-receipt")) "failed publication left a ref")
     (prn {:mode :no-ref :publication :absent :pass true}))
 "read"
 (let [ctx (source-context) {:keys [receipt commit basis public-key]} (edn/read-string (slurp (str receipt-root "/ids.edn")))
       key (.generatePublic (KeyFactory/getInstance "Ed25519") (X509EncodedKeySpec. (.decode (Base64/getDecoder) ^String public-key)))
       db (rbackend) r (first (:receipt/records (causal/receipt-at (open-db db) commit receipt)))]
   (ensure! (= basis (:basis-cid ctx)) "basis changed")
   (ensure! (= commit (:cid (storage/-read-ref db "governed-receipt"))) "ref changed")
   (verify-bundle! #(storage/get-block db %) r ctx key)
   (prn {:mode :read :receipt :verified :signed-basis :verified :pass true}))
 (throw (ex-info "write or read required" {})))
(shutdown-agents)
