;; Two-process hosted Prolly/Forest fixture. Neither identity nor policy is production.
(require '[clojure.edn :as edn] '[otehon.storage :as store]
         '[otehon.kotobase-forest-blocks :as blocks] '[kotobase.storage.core :as storage]
         '[kotobase.engine.prolly :as prolly] '[kotobase.engine.contract :as engine]
         '[datom.source :as source] '[datalog.core :as datalog]
         '[kotobase.governed-execution :as governed] '[kotobase.execution-identity :as id]
         '[kotobase.causal-commit :as causal] '[kotobase.core :as core] '[kotobase.storage.memory :as memory])
(import '[java.security MessageDigest KeyPairGenerator Signature] '[java.util Base64 HexFormat])
(def base (or (System/getenv "MULTI_FOREST_OUTPUT") (throw (ex-info "output required" {}))))
(def tool (or (System/getenv "FOREST_TOOL") (throw (ex-info "tool required" {}))))
(def directory (str base "/store"))
(def staging (str base "/staging"))
(defn ensure! [x message] (when-not x (throw (ex-info message {}))))
(defn sha [s] (.formatHex (HexFormat/of) (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (str s) "UTF-8"))))
(defn backend [] (blocks/open-backend directory staging tool))
(defn make-engine [ports]
  (prolly/prolly-engine {:put! (:put! ports) :get-fn (:get-fn ports) :blind-fn #(str "fixture-blind:" %)
                         :encrypt-fn identity :decrypt-fn identity :digest-fn sha}))
(defn source-for [eng snapshot]
  (reify source/IPatternSource
    (-scan [_ pattern] (mapv (fn [{:keys [e a v]}] {:s e :p a :o v}) (engine/scan eng snapshot pattern)))))
(case (first *command-line-args*)
  "write"
  (do (ensure! (.mkdir (java.io.File. base)) "output already exists")
      (ensure! (.mkdir (java.io.File. staging)) "staging already exists")
      (store/init! directory)
      (let [db (backend) tx (blocks/open-transaction db) eng (make-engine tx)
            state (engine/empty-state eng "forest/multi-query")
            changes (mapv (fn [n] [:db/add (str "file-" n) :file/name (str "name-" n)]) (range 3))
            result (engine/transact eng state {:database-id "forest/multi-query" :request-id "multi-1" :tx-data changes})
            root (get-in result [:receipt :physical-root])]
        (ensure! (:published? ((:publish! tx) "db" nil root)) "publish failed")
        (spit (str base "/root.edn") (pr-str {:root root}))
        (prn {:mode :write :root root :facts 3 :published true :pass true})))
  "read"
  (let [db (backend) root (:root (edn/read-string (slurp (str base "/root.edn"))))
        _ (ensure! (= root (:cid (storage/-read-ref db "db"))) "ref mismatch")
        eng (make-engine (storage/ports db)) snapshot (engine/open-snapshot eng (engine/restore-state eng root))
        src (source-for eng snapshot) receipt-backend (memory/memory-store)
        receipt-db (core/open {:storage receipt-backend :encrypt-fn identity :decrypt-fn identity
                               :blind-fn pr-str :visible? (constantly true)})
        pair (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
        sign (fn [record] (let [s (Signature/getInstance "Ed25519")]
                            (.initSign s (.getPrivate pair)) (.update s (.getBytes ^String (id/payload-cid record) "UTF-8"))
                            (.encodeToString (Base64/getEncoder) (.sign s))))
        verify (fn [{:keys [payload-cid signature]}]
                 (let [s (Signature/getInstance "Ed25519")]
                   (.initVerify s (.getPublic pair)) (.update s (.getBytes ^String payload-cid "UTF-8"))
                   (.verify s (.decode (Base64/getDecoder) ^String signature))))
        unsigned {:execution/version 1 :data/commit root :authority/policy "fixture-policy" :authority/epoch 7
                  :location/manifest "fixture-location" :schema/root "fixture-schema" :parent nil
                  :issued-at "2026-09-02T14:00:00Z"}
        manifest (assoc unsigned :signature (sign unsigned))
        calls (atom 0)
        execute (fn [limit nonce]
                  (let [q {:find [:file/name] :where '[[?e :file/name ?name]]
                           :scope {:tenant "test" :resources #{"fixture"} :purpose :audit :basis root} :limit limit}
                        request {:request/version 1 :principal "fixture-principal" :tenant "test" :graph "fixture-graph"
                                 :operation :read :query/digest (id/value-cid q) :base/commit root
                                 :authority/policy "fixture-policy" :authority/epoch 7 :nonce nonce
                                 :expires-at "2026-09-02T15:00:00Z"}
                        sink (causal/execution-receipt-sink receipt-db {:expected-basis-cid nil :receipt-cid-fn id/value-cid})]
                    (try
                      (governed/execute!
                       {:request request :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (constantly true)}
                        :value-cid id/value-cid :verify verify :plan-digest (constantly "fixture-plan")
                        :cost (constantly {:dependent-hops 0 :requests 0 :bytes 0 :cache-profile :unknown})
                        :implementation/build "fixture" :sign sign
                        :commit! (fn [value] (swap! calls inc) (sink value))
                        :authorize! (fn [_] {:allowed? true :projection #{:file/name} :basis root :policy-cid "fixture-policy"})
                        :schema {:file/name {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
                        :evaluate! (fn [received _]
                                     (mapv (fn [[name]] {:file/name name})
                                           (datalog/q src {:find '[?name] :where (:where received)} (constantly true))))})
                      (catch Exception e {:error (ex-data e)}))))
        ok (execute 3 "multi-ok") head (core/head receipt-db) before @calls
        denied (execute 2 "multi-over")]
    (ensure! (= #{"name-0" "name-1" "name-2"} (set (map :file/name (:rows ok)))) "wrong rows")
    (ensure! (= 1 before @calls) "unexpected receipt calls")
    (ensure! (= :result-limit-exceeded (get-in denied [:error :kotobase.query/reason])) "overflow was not refused")
    (ensure! (= head (core/head receipt-db)) "overflow changed receipt head")
    (ensure! (= root (:cid (storage/-read-ref db "db"))) "query changed source ref")
    (prn {:mode :read :source :persisted-forest-prolly :rows 3 :limit-3 :accepted
          :limit-2 :refused :receipt :signed-and-committed-to-memory :source-ref :unchanged :pass true}))
  (throw (ex-info "write or read required" {})))
(shutdown-agents)
