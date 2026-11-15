(ns frontier-async
  (:require [kotobase.frontier-store :as s]
            [kotobase.engine.identity :as identity]
            [kotobase.storage.core :as storage]
            [kotobase.storage.memory :as memory]))
(def delegate (memory/memory-store))
(def backend
  (reify
    storage/IBlockStore
    (-put-blocks! [_ blocks] (js/Promise.resolve (storage/-put-blocks! delegate blocks)))
    (-get-blocks [_ cids] (js/Promise.resolve (storage/-get-blocks delegate cids)))
    storage/IRefStore
    (-read-ref [_ name] (js/Promise.resolve (storage/-read-ref delegate name)))
    (-compare-and-set-ref! [_ name expected next]
      (js/Promise.resolve (storage/-compare-and-set-ref! delegate name expected next)))
    storage/IBackendCapabilities
    (-capabilities [_] (storage/-capabilities delegate))))
(defn entry [n parent]
  {:root (str "root-" n) :commit {:format identity/logical-commit-format :database-id "db"
   :epoch n :parents (if parent [parent] []) :tx-root (str "tx-" n)
   :logical-checkpoint-root (str "checkpoint-" n) :schema-root "schema"
   :model-contract-root "model" :admission-policy-root "policy"} :signature (str "sig-" n)})
(defn valid? [{:keys [root commit signature]}]
  (and (= root (str "root-" (:epoch commit))) (= signature (str "sig-" (:epoch commit)))))
(defn opts [n parent] {:candidate-root (str "root-" n) :path [(entry n parent)] :verify-entry valid?})
(defn assert! [value label] (when-not value (throw (js/Error. label))))
(defn -main []
 (-> (js/Promise.resolve nil)
     (.then #(s/accept-and-store! backend "r" "db" (assoc (opts 1 "root-0") :allow-initialize? true)))
     (.then (fn [r] (assert! (= :initialized (:status r)) "initialize") (s/read-frontier backend "r" "db")))
     (.then (fn [r] (assert! (= 1 (get-in r [:frontier :epoch])) "read") (s/accept-and-store! backend "r" "db" (opts 2 "root-1"))))
     (.then (fn [r] (assert! (= :advanced (:status r)) "advance") (s/accept-and-store! backend "r" "db" (opts 1 "root-0"))))
     (.then (fn [_] (throw (js/Error. "rollback accepted")))
            (fn [e] (assert! (= :rollback (:problem (ex-data e))) "wrong rejection") (println "CLJS async frontier: initialize/read/advance/rollback pass")))
     (.catch (fn [e] (println e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
