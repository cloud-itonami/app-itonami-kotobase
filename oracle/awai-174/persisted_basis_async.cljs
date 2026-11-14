(ns persisted-basis-async
  (:require [kotobase.persisted-basis :as basis]
            [kotobase.frontier-store :as fs]
            [kotobase.storage.memory :as memory]
            [kotobase.engine.identity :as identity]))
(def backend (memory/memory-store))
(def entry {:root "root" :commit {:format identity/logical-commit-format :database-id "db" :epoch 1
 :parents [] :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema"
 :model-contract-root "model" :admission-policy-root "policy"} :signature "signed"})
(def context {:request {:base/commit "physical" :authority/policy "policy"}
              :manifest {:data/commit "physical" :authority/policy "policy"}})
(def calls (atom []))
(def config {:backend backend :ref-name "r" :database-id "db"
             :fetch-entry! (fn [_] (swap! calls conj :fetch) (js/Promise.resolve entry))
             :verify-entry! (fn [{e :entry}] (swap! calls conj :verify) (js/Promise.resolve (= "signed" (:signature e))))})
(defn ensure! [x msg] (when-not x (throw (js/Error. msg))))
(defn -main []
  (-> (fs/accept-and-store! backend "r" "db" {:allow-initialize? true :candidate-root "root" :path [entry] :verify-entry (constantly true)})
      (.then (fn [_] ((basis/admission config) context)))
      (.then (fn [result] (ensure! (true? result) "valid admission")
               (ensure! (= [:fetch :verify] @calls) "order")
               ((basis/admission (assoc config :verify-entry! (fn [_] (js/Promise.resolve false)))) context)))
      (.then (fn [_] (throw (js/Error. "bad proof admitted")))
             (fn [e] (ensure! (= :unverified-entry (:kotobase.persisted-basis/reason (ex-data e))) "wrong rejection")
               (println "CLJS async persisted basis: valid and rejected signature pass")))
      (.catch (fn [e] (println e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
