;; Local one-off controls for the hosted bridge; this is not a Native gate.
(require '[kotobase.governed-atomic :as atomic]
         '[kotobase.governed-execution :as governed]
         '[kotobase.nonce-store :as nonce])
(defn check! [v] (when-not v (throw (ex-info "control failed" {}))))
(def opts {:request {:nonce "n"} :authority {}})
(def seen (atom []))
(def host {:backend :fixture :scope "tenant/principal/epoch"
           :publish! (fn [receipt marker] (swap! seen conj [receipt marker]) {:receipt/durable? true :receipt/cid "c"})
           :admit-basis! (constantly true)})
(defn run-with [available body]
  (with-redefs [nonce/available? (fn [_ _ _] available)
                governed/execute-bound! (fn [o admit] (body o admit))]
    (atomic/execute-bound! opts host)))
(let [ack (run-with true (fn [o admit]
                           (check! (true? (admit {})))
                           (check! (true? ((get-in o [:authority :consume-nonce!]) "n")))
                           ((:commit! o) {:receipt :ok})))
      marker (second (first @seen))]
  (check! (:receipt/durable? ack))
  (check! (= (select-keys marker [:ref-name :marker-cid :marker])
             (select-keys (nonce/prepare-marker (:scope host) "n") [:ref-name :marker-cid :marker]))))
(reset! seen [])
(run-with false (fn [o _] (check! (false? ((get-in o [:authority :consume-nonce!]) "n")))))
(check! (empty? @seen))
(check! (try (run-with true (fn [o _] ((get-in o [:authority :consume-nonce!]) "other"))) false
             (catch Exception e (= :nonce-mismatch (:kotobase.governed-atomic/reason (ex-data e))))))
(check! (try (run-with true (fn [o _] ((:commit! o) {}))) false
             (catch Exception e (= :unexpected-publication (:kotobase.governed-atomic/reason (ex-data e))))))
(check! (try (run-with true (fn [o _] ((get-in o [:authority :consume-nonce!]) "n") ((:commit! o) {}) ((:commit! o) {}))) false
             (catch Exception e (= :unexpected-publication (:kotobase.governed-atomic/reason (ex-data e))))))
(check! (try (run-with :non-boolean (fn [o _] ((get-in o [:authority :consume-nonce!]) "n"))) false
             (catch Exception e (= :async-backend (:kotobase.governed-atomic/reason (ex-data e))))))
(check! (try (atomic/execute-bound! (assoc-in opts [:authority :consume-nonce!] identity) host) false
             (catch Exception e (= :invalid-options (:kotobase.governed-atomic/reason (ex-data e))))))
(check! (try
          (with-redefs [nonce/available? (fn [_ _ _] true)
                        governed/execute-bound! (fn [o _]
                           ((get-in o [:authority :consume-nonce!]) "n")
                           ((:commit! o) {})
                           :rows-must-not-be-returned)]
            (atomic/execute-bound! opts (assoc host :publish! (fn [_ _] (throw (ex-info "publish failed" {}))))))
          false
          (catch Exception e (= "publish failed" (ex-message e)))))
(prn {:request-binding :checked :replay :refused :wrong-nonce :refused :early-and-repeat-publication :refused :async :refused})
(shutdown-agents)
