;; Hosted experiment: real Forest storage and Dango/policy with fixture identity.
(require '[clojure.string :as str])
(def project (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "KOTOBASE_ORACLE_ROOT required" {}))))
(load-string (first (str/split (slurp (str project "/oracle/awai-174/governed_forest_multiline.clj")) #"\(case \(first \*command-line-args\*\)" 2)))
(require '[dango.chain :as chain] '[ed25519.sign :as ed] '[kotobase-peer.policy :as policy])
(import '[java.security KeyFactory] '[java.security.spec X509EncodedKeySpec])
(def out (str base "/durable-receipts"))
(def receipt-dir (str out "/store"))
(def receipt-stage (str out "/staging"))
(defn db [] (core/open {:storage (blocks/open-backend receipt-dir receipt-stage tool)
                         :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(defn verify-signature [public-key payload signature]
  (let [v (Signature/getInstance "Ed25519")]
    (.initVerify v public-key) (.update v (.getBytes ^String payload "UTF-8"))
    (.verify v (.decode (Base64/getDecoder) ^String signature))))
(case (first *command-line-args*)
 "write"
 (do (ensure! (.mkdir (java.io.File. out)) "receipt output already exists")
     (ensure! (.mkdir (java.io.File. receipt-stage)) "receipt staging already exists")
     (store/init! receipt-dir)
     (let [source-db (backend) root (:root (edn/read-string (slurp (str base "/root.edn"))))
           _ (ensure! (= root (:cid (storage/-read-ref source-db "db"))) "source root mismatch")
           eng (make-engine (storage/ports source-db)) snap (engine/open-snapshot eng (engine/restore-state eng root))
           src (source-for eng snap) receipt-db (db)
           pair (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
           sign (fn [record] (let [s (Signature/getInstance "Ed25519")]
                               (.initSign s (.getPrivate pair)) (.update s (.getBytes ^String (id/payload-cid record) "UTF-8"))
                               (.encodeToString (Base64/getEncoder) (.sign s))))
           verify (fn [{:keys [payload-cid signature]}] (verify-signature (.getPublic pair) payload-cid signature))
           p {:protected-prefixes [":file/"]} pcid (policy/policy-cid p)
           resource (str "kotoba://basis/" root) seed (vec (repeat 32 11))
           token (chain/root {:issuer-seed seed :grant {:caps #{[:datom/read-protected resource]} :before 1800000000}
                              :caveats [] :next-seed (vec (repeat 32 12))})
           check (fn [resource-value] (chain/verify (chain/present token) {:roots #{(ed/public-key seed)}
                                          :request {:req/kind :datom/read-protected :req/resource resource-value :req/now 1700000000}}))
           allow (check resource) deny (check "kotoba://basis/wrong")
           _ (ensure! (and (:dango/allowed? allow) (not (:dango/allowed? deny))) "Dango control failed")
           unsigned {:execution/version 1 :data/commit root :authority/policy pcid :authority/epoch 7
                     :location/manifest "fixture-location" :schema/root "fixture-schema" :parent nil :issued-at "2026-09-02T14:00:00Z"}
           manifest (assoc unsigned :signature (sign unsigned)) calls (atom 0) evaluations (atom 0)
           execute (fn [limit nonce decision]
                     (let [q {:find [:file/name] :where '[[?e :file/name ?name]]
                              :scope {:tenant "test" :resources #{resource} :purpose :audit :basis root} :limit limit}
                           request {:request/version 1 :principal "fixture-principal" :tenant "test" :graph "fixture-graph"
                                    :operation :read :query/digest (id/value-cid q) :base/commit root
                                    :authority/policy pcid :authority/epoch 7 :nonce nonce :expires-at "2026-09-02T15:00:00Z"}
                           sink (causal/execution-receipt-sink receipt-db {:expected-basis-cid nil :receipt-cid-fn id/value-cid})]
                       (try
                         (governed/execute!
                          {:request request :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! (constantly true)}
                           :value-cid id/value-cid :verify verify :plan-digest (constantly "fixture-plan")
                           :cost (constantly {:dependent-hops 0 :requests 0 :bytes 0 :cache-profile :unknown})
                           :implementation/build "fixture" :sign sign :commit! (fn [r] (swap! calls inc) (sink r))
                           :authorize! (fn [_] {:allowed? (true? (:dango/allowed? decision)) :projection #{:file/name} :basis root :policy-cid pcid})
                           :schema {:file/name {:class :public}} :grant {:granted #{:public} :scopes #{}} :query q
                           :evaluate! (fn [received _] (swap! evaluations inc)
                                        (let [visible (policy/visible-for p #{policy/read-protected-capability})]
                                          (mapv (fn [[name]] {:file/name name})
                                                (datalog/q src {:find '[?name] :where (:where received)} #(visible (update % :p str))))))})
                         (catch Exception e {:error (ex-data e)}))))
           allowed (execute 3 "allowed" allow)
           overflow (execute 2 "overflow" allow)
           calls-after-over @calls evals-after-over @evaluations
           denied (execute 3 "denied" deny)
           allow-id (get-in allowed [:provenance :receipt-cid]) allow-commit (get-in allowed [:provenance :receipt-commit-cid])
           deny-id (get-in denied [:error :kotobase.governed-execution/deny-receipt-cid])
           deny-commit (get-in denied [:error :kotobase.governed-execution/deny-receipt-commit-cid])]
       (ensure! (= #{"name-0" "name-1" "name-2"} (set (map :file/name (:rows allowed)))) "allowed rows mismatch")
       (ensure! (= :result-limit-exceeded (get-in overflow [:error :kotobase.query/reason])) "overflow not refused")
       (ensure! (= 1 calls-after-over) "overflow wrote receipt")
       (ensure! (= :authority-denied (get-in denied [:error :kotobase.governed-execution/reason])) "deny not refused")
       (ensure! (= 2 @calls) "deny receipt missing")
       (ensure! (= evals-after-over @evaluations 2) "deny evaluated")
       (ensure! (every? string? [allow-id allow-commit deny-id deny-commit]) "missing receipt ids")
       (spit (str out "/ids.edn") (pr-str {:allow-id allow-id :allow-commit allow-commit :deny-id deny-id :deny-commit deny-commit
                                             :root root :public-key (.encodeToString (Base64/getEncoder) (.getEncoded (.getPublic pair)))}))
       (prn {:mode :write :rows 3 :overflow :refused :dango-denial :receipted :deny-evaluation :skipped
             :receipt-backend :forest :policy :fixture :pass true})))
 "read"
 (let [{:keys [allow-id allow-commit deny-id deny-commit root public-key]} (edn/read-string (slurp (str out "/ids.edn")))
       key (.generatePublic (KeyFactory/getInstance "Ed25519") (X509EncodedKeySpec. (.decode (Base64/getDecoder) ^String public-key)))
       database (db)
       allow (first (:receipt/records (causal/receipt-at database allow-commit allow-id)))
       deny (first (:receipt/records (causal/receipt-at database deny-commit deny-id)))]
   (ensure! (= :allow (:authority/decision allow)) "allow receipt missing")
   (ensure! (= :deny (:authority/decision deny)) "deny receipt missing")
   (doseq [receipt [allow deny]]
     (ensure! (verify-signature key (id/payload-cid receipt) (:signature receipt)) "receipt signature invalid"))
   (ensure! (= root (:cid (storage/-read-ref (backend) "db"))) "source ref changed")
   (prn {:mode :read :allow :verified :deny :verified :signatures :verified :receipt-backend :forest :source-ref :unchanged :pass true}))
 (throw (ex-info "write or read required" {})))
(shutdown-agents)
