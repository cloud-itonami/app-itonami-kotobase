(ns awai-oracle.query-async-compiled
  (:require [kotobase.authorized-query :as query]))

(def sample-query
  {:find ['?e] :where [['?e :name '?name]]
   :scope {:tenant "tenant" :resources #{"resource"} :purpose :read :basis "basis"}
   :limit 2})
(def allowed {:allowed? true :projection #{'?e} :basis "basis" :policy-cid "policy"})
(def calls (atom {:evaluations 0 :receipts 0}))
(defn check! [condition message]
  (when-not condition (throw (js/Error. message))))
(defn evaluate [rows]
  (fn [_ _] (swap! calls update :evaluations inc) (js/Promise.resolve rows)))
(defn receipt [_]
  (swap! calls update :receipts inc)
  (js/Promise.resolve {:receipt/durable? true :receipt/cid "receipt"}))
(defn expect-rejection [promise reason]
  (.then promise
         (fn [_] (throw (js/Error. (str "expected rejection: " reason))))
         (fn [error]
           (check! (= reason (:kotobase.query/reason (ex-data error)))
                   (str "unexpected rejection: " error)))))
(defn -main []
  (-> (query/compile-async! (fn [_] (js/Promise.resolve allowed)) sample-query)
      (.then (fn [compiled]
               (-> (query/execute-async! (evaluate [1 2]) receipt compiled)
                   (.then (fn [result]
                            (check! (= [1 2] (:rows result)) "rows changed")
                            (expect-rejection
                             (query/execute-async! (evaluate [1 2 3]) receipt compiled)
                             :result-limit-exceeded))))))
      (.then (fn []
               (expect-rejection
                (query/compile-async! (fn [_] (js/Promise.resolve (assoc allowed :allowed? false))) sample-query)
                :denied)))
      (.then (fn []
               (check! (= {:evaluations 2 :receipts 1} @calls) "unexpected evaluator or receipt call")
               (println "compiled async query: pass")))
      (.catch (fn [error] (.error js/console error) (set! (.-exitCode js/process) 1)))))
(set! *main-cli-fn* -main)
