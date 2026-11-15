;; Hosted signed multi-row Prolly basis with a policy stored in that basis.
(require '[clojure.string :as str])
(def project (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "oracle root required" {}))))
(load-string (first (str/split (slurp (str project "/oracle/awai-174/governed_forest_multiline.clj")) #"\(case \(first \*command-line-args\*\)" 2)))
(require '[kotobase.engine.identity :as ident] '[kotobase.signed-basis :as signed]
         '[kotoba.value.codec :as codec] '[kotobase-peer.policy :as policy]
         '[dango.chain :as chain] '[ed25519.sign :as ed])
(def seed (vec (repeat 32 17)))
(def public-key (ed/public-key seed))
(defn bytes-of [s] (mapv #(bit-and 255 %) (.getBytes ^String s "UTF-8")))
(defn policy-at [eng snapshot]
  (policy/policy-of (mapv (fn [{:keys [e a v]}] {:e e :a (str a) :v_edn (pr-str v)})
                          (engine/scan eng snapshot ["kotobase.policy/read" nil nil]))))
(defn encode-block [x] {:cid (codec/value-cid x) :bytes (byte-array (map unchecked-byte (codec/encode-value x)))})
(defn read-block [get-block cid]
  (let [b (get-block cid)]
    (ensure! b "missing signed basis block")
    (let [x (codec/decode-value b)] (ensure! (= cid (codec/value-cid x)) "basis block CID mismatch") x)))
(defn verify-entry! [eng entry expected-policy]
  (let [root (:physical-root entry)
        verify (signed/verifier {:digest! sha :checkpoint! (signed/checkpoint-reader eng)
                                 :verify-location! (fn [_] true)
                                 :verify-signature! (fn [{:keys [issuer key-id payload signature]}]
                                                      (and (= issuer "fixture-issuer") (= key-id "fixture-key")
                                                           (true? (ed/verify public-key (bytes-of payload) signature))))})]
    (true? (verify {:entry entry :request {:base/commit root :authority/policy expected-policy}
                    :manifest {:data/commit root :authority/policy expected-policy}}))))
(defn denied? [f] (try (f) false (catch Exception _ true)))
(case (first *command-line-args*)
 "write"
 (do (ensure! (.mkdir (java.io.File. base)) "output exists")
     (ensure! (.mkdir (java.io.File. staging)) "staging exists")
     (store/init! directory)
     (let [db (backend) tx (blocks/open-transaction db) eng (make-engine tx)
           state (engine/empty-state eng "forest/signed-policy-multi")
           changes (conj (mapv (fn [n] [:db/add (str "file-" n) :file/name (str "name-" n)]) (range 3))
                         [:db/add "kotobase.policy/read" :kotobase.policy/protected-prefixes (pr-str [":file/"])])
           result (engine/transact eng state {:database-id "forest/signed-policy-multi" :request-id "multi-policy-1" :tx-data changes})
           physical (get-in result [:receipt :physical-root]) snapshot (engine/open-snapshot eng (:state result))
           checkpoint (engine/checkpoint eng snapshot) p (policy-at eng snapshot) pcid (policy/policy-cid p)
           _ (ensure! (and p pcid) "persisted policy missing")
           commit (ident/logical-commit {:format ident/logical-commit-format :database-id (:database-id checkpoint)
                                         :epoch (:epoch checkpoint) :parents [] :tx-root (get-in result [:receipt :tx-root])
                                         :logical-checkpoint-root (:logical-checkpoint-root checkpoint)
                                         :schema-root "fixture-schema" :model-contract-root "fixture-model" :admission-policy-root pcid})
           logical (sha (ident/logical-commit-string commit)) issuer "fixture-issuer" key-id "fixture-key"
           payload (ident/signature-payload {:logical-commit-root logical :issuer issuer :key-id key-id})
           entry {:root logical :commit commit :issuer issuer :key-id key-id :physical-root physical
                  :signature (ed/sign (ed/secret-key! seed) (bytes-of payload))}
           b (encode-block entry)]
       ((:put! tx) (:cid b) (:bytes b))
       (ensure! (:published? ((:publish! tx) "signed-basis" nil (:cid b)
                             (fn [get-block root]
                               (let [entry (read-block get-block root)
                                     check-engine (make-engine {:put! (fn [& _] (throw (ex-info "readonly" {}))) :get-fn get-block})
                                     recovered (engine/open-snapshot check-engine (engine/restore-state check-engine (:physical-root entry)))
                                     recovered-policy (policy-at check-engine recovered)]
                                 (and (= pcid (policy/policy-cid recovered-policy)) (verify-entry! check-engine entry pcid))))))
                "signed basis publication failed")
       (prn {:mode :write :basis (:cid b) :physical physical :policy pcid :rows 3 :pass true})))
 "read"
 (let [db (backend) cid (:cid (storage/-read-ref db "signed-basis")) get-block #(storage/get-block db %)
       entry (read-block get-block cid) eng (make-engine {:put! (fn [& _] (throw (ex-info "readonly" {}))) :get-fn get-block})
       snapshot (engine/open-snapshot eng (engine/restore-state eng (:physical-root entry)))
       p (policy-at eng snapshot) pcid (policy/policy-cid p) _ (ensure! p "policy absent")
       _ (ensure! (verify-entry! eng entry pcid) "signature or checkpoint invalid")
       _ (ensure! (denied? #(verify-entry! eng (assoc entry :signature (vec (repeat 64 0))) pcid)) "bad signature accepted")
       _ (ensure! (denied? #(verify-entry! eng entry (policy/policy-cid {:protected-prefixes [":other/"]}))) "changed policy accepted")
       src (source-for eng snapshot) resource (str "kotoba://basis/" (:physical-root entry))
       dseed (vec (repeat 32 11)) token (chain/root {:issuer-seed dseed :grant {:caps #{[:datom/read-protected resource]} :before 1800000000}
                                                   :caveats [] :next-seed (vec (repeat 32 12))})
       decision (chain/verify (chain/present token) {:roots #{(ed/public-key dseed)}
                         :request {:req/kind :datom/read-protected :req/resource resource :req/now 1700000000}})
       _ (ensure! (:dango/allowed? decision) "Dango refused authorized token")
       run (fn [caps] (let [visible (policy/visible-for p caps)]
                        (datalog/q src '{:find [?name] :where [[?e :file/name ?name]]} #(visible (update % :p str)))))
       allowed (run #{policy/read-protected-capability}) denied (run #{})]
   (ensure! (= #{["name-0"] ["name-1"] ["name-2"]} allowed) "authorized rows mismatch")
   (ensure! (empty? denied) "policy leaked protected rows")
   (prn {:mode :read :signed-basis :verified :policy :recovered-and-bound :allowed (count allowed)
         :no-capability (count denied) :tamper :refused :pass true}))
 (throw (ex-info "write or read required" {})))
(shutdown-agents)
