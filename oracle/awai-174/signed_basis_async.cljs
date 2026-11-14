(ns signed-basis-async
 (:require [kotobase.signed-basis :as signed]
           [kotobase.engine.identity :as identity]
           [kotobase.engine.contract :as engine]
           [kotobase.engine.profile :as profile]))
(def commit {:format identity/logical-commit-format :database-id "db" :epoch 1 :parents [] :tx-root "tx"
             :logical-checkpoint-root "cp" :schema-root "schema" :model-contract-root "model" :admission-policy-root "policy"})
(defn digest [x] (str "digest:" x))
(def entry {:root (digest (identity/logical-commit-string commit)) :commit commit :physical-root "physical"
            :issuer "issuer" :key-id "key" :signature "signed"})
(def context {:entry entry :request {:base/commit "physical" :authority/policy "policy"}
              :manifest {:data/commit "physical" :authority/policy "policy"}})
(def checkpoint {:database-id "db" :epoch 1 :logical-checkpoint-root "cp" :physical-root "physical" :engine profile/prolly})
(def async-engine
  (reify engine/IEngine
    (-engine-profile [_] profile/prolly)
    (-empty-state [_ _] nil)
    (-restore-state [_ root _] (js/Promise.resolve {:root root}))
    (-transact [_ _ _] nil)
    (-open-snapshot [_ state _] (js/Promise.resolve state))
    (-scan [_ _ _ _] nil)
    (-history [_ _ _] nil)
    (-checkpoint [_ snapshot _]
      (js/Promise.resolve (assoc checkpoint :physical-root (:root snapshot))))))
(def calls (atom []))
(def config {:digest! (fn [x] (swap! calls conj :digest) (js/Promise.resolve (digest x)))
             :verify-location! (fn [_] (swap! calls conj :location) (js/Promise.resolve true))
             :checkpoint! (fn [_] (swap! calls conj :checkpoint) (js/Promise.resolve checkpoint))
             :verify-signature! (fn [_] (swap! calls conj :signature) (js/Promise.resolve true))})
(defn ensure! [x msg] (when-not x (throw (js/Error. msg))))
(defn -main []
 (-> ((signed/verifier config) context)
     (.then (fn [v] (ensure! (true? v) "valid proof")
              (ensure! (= [:digest :location :checkpoint :signature] @calls) "callback order")
              ((signed/checkpoint-reader async-engine) "physical")))
     (.then (fn [cp] (ensure! (= checkpoint cp) "async checkpoint")
              ((signed/verifier (assoc config :verify-signature! (fn [_] (js/Promise.resolve false)))) context)))
     (.then (fn [_] (throw (js/Error. "invalid proof admitted")))
            (fn [e] (ensure! (= :signature-refused (:kotobase.signed-basis/reason (ex-data e))) "wrong error")
              (println "CLJS async signed basis: valid and refused signature pass")))
     (.catch (fn [e] (println e) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
