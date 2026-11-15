(with-redefs [clojure.core/shutdown-agents (fn [])] (load-file "oracle/awai-174/authority_link_atomic_sink_probe.clj"))
(require '[otehon.kotobase-forest-blocks :as fb] '[otehon.storage :as store]
         '[otehon.ipld-forest :as catalog] '[awai.fs.car :as car]
         '[kotobase.storage.core :as storage] '[kotobase.hosted-receipt-publication :as hosted]
         '[clojure.string :as str] '[clojure.edn :as edn])
(import '[java.security MessageDigest] '[java.util Random Arrays])
(def base (System/getenv "LINKED_FOREST_BASE"))
(def tool (System/getenv "FOREST_TOOL"))
(def mode (first *command-line-args*))
(def dir (str base "/store"))
(def stage (str base "/stage"))
(when (= mode "write") (assert (.mkdir (java.io.File. base))) (assert (.mkdir (java.io.File. stage))) (store/init! dir))
(def backend (fb/open-backend dir stage tool))
(defn db [backend] (core/open {:storage backend :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def rng (Random. 174532))
(def attachments (mapv (fn [_] (let [b (byte-array (* 600 1024)) _ (.nextBytes rng b) d (mapv #(bit-and 255 %) (.digest (MessageDigest/getInstance "SHA-256") b))] {:cid (car/cid-str (car/cid-bytes car/codec-raw d)) :bytes b})) (range 2)))
(defn read-db [get-block]
  (db (reify storage/IBlockStore
        (-put-blocks! [_ _] (throw (ex-info "read-only" {})))
        (-get-blocks [_ cids] (into {} (keep (fn [cid] (when-let [b (get-block cid)] [cid b]))) cids))
        storage/IRefStore (-read-ref [_ name] (storage/-read-ref backend name))
        (-compare-and-set-ref! [_ _ _ _] (throw (ex-info "read-only" {})))
        storage/IBackendCapabilities (-capabilities [_] (storage/-capabilities backend)))))
(def callbacks (hosted/callbacks {:stage-bundle! (fn [ports _ _] (doseq [{:keys [cid bytes]} attachments] ((:put! ports) cid bytes)))
                                 :encrypt! identity :open-read-db! read-db
                                 :verify-bundle! (fn [get-block _ _ _] (every? (fn [{:keys [cid bytes]}] (Arrays/equals ^bytes bytes ^bytes (get-block cid))) attachments))}))
(def publication (fb/receipt-publication-adapter backend "linked" nil (:build! callbacks) (:verify! callbacks)))
(if (= mode "write")
  (let [ack ((causal/published-linked-execution-receipt-sink (db backend) (merge sink-options publication)) receipt)
        commit (:receipt/commit-cid ack) cid (:receipt/cid ack)
        records (:receipt/records (causal/receipt-at (db backend) commit cid))
        groups (filter #(str/starts-with? % "awai.ipld-forest-group/kotobase-blocks/") (keys (:state (store/read-store dir))))]
    (assert (= 1 (count groups)))
    (assert (= 2 (count (get-in (catalog/recover-group dir (subs (first groups) (count "awai.ipld-forest-group/"))) [:group :archives]))))
    (assert (= 3 (count records))) (assert (= authority-receipt (second records)))
    (spit (str base "/ids.edn") (pr-str {:cid cid :commit commit}))
    (prn {:mode :write :actual-forest true :packs 2 :records 3 :pass true}))
  (let [{:keys [cid commit]} (edn/read-string (slurp (str base "/ids.edn")))
        records (:receipt/records (causal/receipt-at (db backend) commit cid))]
    (assert (= commit (:cid (storage/-read-ref backend "linked"))))
    (assert (= 3 (count records))) (assert (= receipt (first records))) (assert (= authority-receipt (second records)))
    (assert (link/verify! {:link (nth records 2) :request (:request req) :query (:query req) :execution-receipt (first records) :authority-receipt (second records) :value-cid id/value-cid :verify fixture/verify}))
    (doseq [{:keys [cid bytes]} attachments] (assert (Arrays/equals ^bytes bytes ^bytes (storage/get-block backend cid))))
    (prn {:mode :fresh-process-read :actual-forest true :records 3 :attachments :verified :pass true})))

(shutdown-agents)
