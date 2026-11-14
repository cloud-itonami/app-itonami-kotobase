(ns kotobase.frontier-store-test
  (:require [clojure.test :refer [deftest is]]
            [kotobase.frontier-store :as s]
            [kotobase.engine.identity :as identity]
            [kotobase.storage.core :as storage]
            [kotobase.storage.memory :as memory]
            [multiformats.core :as mf]))
(defn entry [epoch parent]
  {:root (str "root-" epoch)
   :commit {:format identity/logical-commit-format :database-id "db" :epoch epoch
            :parents (if parent [parent] []) :tx-root (str "tx-" epoch)
            :logical-checkpoint-root (str "checkpoint-" epoch) :schema-root "schema"
            :model-contract-root "model" :admission-policy-root "policy"}
   :signature (str "sig-" epoch)})
(defn verified? [{:keys [root commit signature]}]
  (and (= root (str "root-" (:epoch commit))) (= signature (str "sig-" (:epoch commit)))))
(defn opts [n parent] {:candidate-root (str "root-" n) :path [(entry n parent)] :verify-entry verified?})
(defn problem [f] (try (f) nil (catch Exception e (:problem (ex-data e)))))
(deftest test-persistence
  (let [backend (memory/memory-store)]
    (is (= :initialization-not-authorized (problem #(s/accept-and-store! backend "r" "db" (opts 1 "root-0")))))
    (is (nil? (s/read-frontier backend "r" "db")))
    (is (= :initialized (:status (s/accept-and-store! backend "r" "db" (assoc (opts 1 "root-0") :allow-initialize? true)))))
    (is (= 1 (get-in (s/read-frontier backend "r" "db") [:frontier :epoch])))
    (is (= :advanced (:status (s/accept-and-store! backend "r" "db" (opts 2 "root-1")))))
    (is (= :rollback (problem #(s/accept-and-store! backend "r" "db" (opts 1 "root-0")))))
    (is (= :unverified-commit (problem #(s/accept-and-store! backend "r" "db" (assoc (opts 3 "root-2") :verify-entry (constantly false))))))
    (is (= 2 (get-in (s/read-frontier backend "r" "db") [:frontier :epoch])))
    (is (= :database-mismatch (problem #(s/read-frontier backend "r" "other"))))
    (let [cid (:cid (s/read-frontier backend "r" "db"))]
      (swap! (:state backend) update :blocks dissoc cid)
      (is (= :missing-frontier-block (problem #(s/read-frontier backend "r" "db")))))))

(deftest corrupt-block-and-competing-writer
  (let [backend (memory/memory-store)]
    (s/accept-and-store! backend "r" "db" (assoc (opts 1 "root-0") :allow-initialize? true))
    (let [cid (:cid (s/read-frontier backend "r" "db"))]
      (swap! (:state backend) assoc-in [:blocks cid] (byte-array [0]))
      (is (some? (try (s/read-frontier backend "r" "db") nil (catch Exception e e))))))
  (let [delegate (memory/memory-store)
        backend (reify
                  storage/IBlockStore
                  (-put-blocks! [_ blocks] (storage/-put-blocks! delegate blocks))
                  (-get-blocks [_ cids] (storage/-get-blocks delegate cids))
                  storage/IRefStore
                  (-read-ref [_ name] (storage/-read-ref delegate name))
                  (-compare-and-set-ref! [_ name expected next]
                    (storage/-compare-and-set-ref! delegate name expected "other-cid")
                    (storage/-compare-and-set-ref! delegate name expected next))
                  storage/IBackendCapabilities
                  (-capabilities [_] (storage/-capabilities delegate)))]
    (is (= :ref-conflict (problem #(s/accept-and-store! backend "r" "db" (assoc (opts 1 "root-0") :allow-initialize? true)))))
    (is (= "other-cid" (:cid (storage/-read-ref delegate "r"))))))

(deftest noncanonical-and-failed-write-do-not-publish
  (let [backend (memory/memory-store)
        bad (byte-array (map unchecked-byte [0x82 0x18 0x00 0xf6]))
        cid (mf/cidv1-dag-cbor bad)]
    (storage/-put-blocks! backend [{:cid cid :bytes bad}])
    (storage/-compare-and-set-ref! backend "r" nil cid)
    (is (= :value/non-canonical-bytes (problem #(s/read-frontier backend "r" "db")))))
  (let [delegate (memory/memory-store)
        backend (reify
                  storage/IBlockStore
                  (-put-blocks! [_ _] (throw (ex-info "write failed" {:problem :write-failed})))
                  (-get-blocks [_ cids] (storage/-get-blocks delegate cids))
                  storage/IRefStore
                  (-read-ref [_ name] (storage/-read-ref delegate name))
                  (-compare-and-set-ref! [_ name expected next]
                    (storage/-compare-and-set-ref! delegate name expected next))
                  storage/IBackendCapabilities
                  (-capabilities [_] (storage/-capabilities delegate)))]
    (is (= :write-failed (problem #(s/accept-and-store! backend "r" "db" (assoc (opts 1 "root-0") :allow-initialize? true)))))
    (is (nil? (storage/-read-ref delegate "r")))))
