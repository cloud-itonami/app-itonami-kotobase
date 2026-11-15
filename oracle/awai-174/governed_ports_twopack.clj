;; Hosted composition: fixture keys and authority, real Forest publication.
(require '[clojure.string :as str])
(def project (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "oracle root required" {}))))
(load-string (first (str/split (slurp (str project "/oracle/awai-174/signed_policy_ports_receipt.clj"))
                               #"\(case \(first \*command-line-args\*\)" 2)))
(require '[clojure.set :as cset] '[awai.fs.car :as car] '[otehon.ipld-forest :as catalog]
         '[kotobase.governed-read :as governed-read] '[kotobase.frontier-store :as frontier]
         '[kotobase.hosted-receipt-publication :as hosted-receipt])
(import '[java.security MessageDigest] '[java.util Random Arrays])
(def attachments
  (let [rng (Random. 174532)]
    (mapv (fn [_]
            (let [bytes (byte-array (* 600 1024)) _ (.nextBytes rng bytes)
                  digest (mapv #(bit-and 255 %) (.digest (MessageDigest/getInstance "SHA-256") bytes))]
              {:cid (car/cid-str (car/cid-bytes car/codec-raw digest)) :bytes bytes})) (range 2))))
(defn location-for [ctx]
  {:format :awai.fixture/basis-location-v1 :signed-basis (:basis-cid ctx)
   :physical-root (get-in ctx [:entry :physical-root]) :policy (:policy-cid ctx)
   :attachments (mapv :cid attachments)})
(defn verify-location-blocks! [get-block ctx]
  (ensure! (= (location-for ctx) (read-block get-block (id/value-cid (location-for ctx)))) "location block mismatch")
  (doseq [{:keys [cid bytes]} attachments]
    (ensure! (Arrays/equals ^bytes bytes ^bytes (get-block cid)) "attachment bytes mismatch"))
  true)
(defn group-names [dir]
  (set (filter #(str/starts-with? % "awai.ipld-forest-group/kotobase-blocks/")
               (keys (:state (store/read-store dir))))))
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
           calls (atom 0) groups-before (atom nil)
           callbacks (hosted-receipt/callbacks
                       {:stage-bundle! (fn [ports _ _] (swap! calls inc)
                          (reset! groups-before (group-names receipt-dir))
                          (doseq [v [request manifest (location-for ctx)]]
                            (let [b (encode-block v)] ((:put! ports) (:cid b) (:bytes b))))
                          (doseq [{:keys [cid bytes]} attachments] ((:put! ports) cid bytes)))
                        :encrypt! identity
                        :verify-bundle! (fn [get-block _ receipt _]
                          (and (not (System/getenv "GOVERNED_REJECT"))
                               (verify-bundle! get-block receipt ctx (.getPublic pair))
                               (verify-location-blocks! get-block ctx)))
                        :open-read-db! (fn [get-block]
                          (open-db (reify
                            storage/IBlockStore
                            (-put-blocks! [_ _] (throw (ex-info "read only" {})))
                            (-get-blocks [_ cids] (into {} (keep (fn [cid]
                              (when (System/getenv "GOVERNED_MISSING_TARGET")
                                (throw (ex-info "verifier block missing" {:type :missing-verifier-block})))
                              (when-let [b (get-block cid)] [cid b]))) cids))
                            storage/IRefStore
                            (-read-ref [_ name] (storage/-read-ref db name))
                            (-compare-and-set-ref! [_ _ _ _] (throw (ex-info "read only" {})))
                            storage/IBackendCapabilities
                            (-capabilities [_] (storage/-capabilities db)))))})
           publication (blocks/receipt-publication-adapter db "governed-receipt" nil
                         (:build! callbacks) (:verify! callbacks))
           entry (:entry ctx) database-id (get-in entry [:commit :database-id])
           _ (admit! ctx request manifest)
           _ (frontier/accept-and-store! db "governed-frontier" database-id
               {:allow-initialize? true :candidate-root (:root entry) :path [entry]
                :verify-entry (fn [candidate] (verify-entry! (:engine ctx) candidate pcid))})
           basis-config {:backend db :ref-name "governed-frontier" :database-id database-id
                         :fetch-entry! (fn [_] entry)}
           verification {:digest! sha :checkpoint! (signed/checkpoint-reader (:engine ctx))
                         :verify-location! (fn [{:keys [entry manifest]}]
                           (let [location (location-for ctx)]
                             (and (= (:location/manifest manifest) (id/value-cid location))
                                  (= (:physical-root entry) (:physical-root location))
                                  (= (:policy location) (:policy-cid ctx)))))
                         :registry (fn [scope] (if (= scope {:role :issuer :tenant "test" :epoch 7})
                           [{:key/id "fixture-key" :key/algorithm :ed25519 :key/material public-key}] []))
                         :algorithm :ed25519
                         :issuer-authorized? (fn [{:keys [issuer key]}]
                                               (and (= issuer "fixture-issuer") (= "fixture-key" (:key/id key))))
                         :verify-with (fn [{:keys [key payload signature-value]}]
                                        (true? (ed/verify (:key/material key) (bytes-of payload) signature-value)))}
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
           bad-registry (assoc verification :registry (constantly []))
           _ (ensure! (= :no-key-registered
                         (try (governed-read/read-registry-persistent! read-request basis-config bad-registry nonce-config) nil
                              (catch Exception e (:kotobase.execution-keys/reason (ex-data e)))))
                      "unregistered basis key not specifically refused")
           _ (ensure! (and (zero? @calls) (zero? @evaluations)) "bad key reached evaluator or publisher")
           result (governed-read/read-registry-persistent! read-request basis-config verification nonce-config)
           cid (get-in result [:provenance :receipt-cid]) commit (get-in result [:provenance :receipt-commit-cid])]
       (ensure! (= 3 (count (:rows result))) "wrong rows")
       (ensure! (= 1 @calls) "publication count")
       (let [added (cset/difference (group-names receipt-dir) @groups-before)]
         (ensure! (= 1 (count added)) "expected one receipt group")
         (let [name (subs (first added) (count "awai.ipld-forest-group/"))]
           (ensure! (= 2 (count (get-in (catalog/recover-group receipt-dir name) [:group :archives]))) "expected two physical packs")))
       (ensure! (= :nonce-replayed
                   (try (governed-read/read-registry-persistent! read-request basis-config verification nonce-config) nil
                        (catch Exception e (:kotobase.authority-window/reason (ex-data e))))) "replay accepted")
       (ensure! (and (= 1 @calls) (= 1 @evaluations)) "replay evaluated or published")
       (spit (str receipt-root "/ids.edn") (pr-str {:receipt cid :commit commit :basis (:basis-cid ctx)
             :public-key (.encodeToString (Base64/getEncoder) (.getEncoded (.getPublic pair)))}))
       (prn {:mode :write :governed-registry-persistent true :rows 3 :packs 2 :attachments :verified :bad-key :refused :nonce-replay :refused :pass true})))
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
   (verify-location-blocks! #(storage/get-block db %) ctx)
   (prn {:mode :read :receipt :verified :signed-basis :verified :pass true}))
 (throw (ex-info "write or read required" {})))
(shutdown-agents)
