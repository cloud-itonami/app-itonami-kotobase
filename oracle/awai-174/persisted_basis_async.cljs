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
               (reset! calls [])
               ((basis/admission (assoc config :fetch-entry! (fn [_] (js/Promise.resolve (assoc entry :root "other"))))) context)))
      (.then (fn [_] (throw (js/Error. "stale entry admitted")))
             (fn [e] (ensure! (= :frontier-mismatch (:kotobase.persisted-basis/reason (ex-data e))) "wrong stale rejection")
               (ensure! (empty? @calls) "stale entry invoked verifier")
               ((basis/admission (assoc config :verify-entry!
                 (fn [_] (-> (fs/accept-and-store! backend "r" "db"
                              {:candidate-root "next" :path [(assoc entry :root "next" :commit (assoc (:commit entry) :epoch 2 :parents ["root"]))]
                               :verify-entry (constantly true)})
                             (.then (fn [_] true)))))) context)))
      (.then (fn [_] (throw (js/Error. "changed frontier admitted")))
             (fn [e] (ensure! (= :frontier-changed (:kotobase.persisted-basis/reason (ex-data e))) "wrong change rejection")
               (println "CLJS async persisted basis: valid, stale, signature and frontier race pass")))
      (.catch (fn [e] (println e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
