;; Hosted callback wiring controls; the separate GiB oracle exercises the real sink.
(require '[kotobase.causal-commit :as causal])
(defn check! [v] (when-not v (throw (ex-info "control failed" {}))))
(def marker {:ref-name "nonce/v1/test" :marker-cid "cid" :marker {:format :fixture} :bytes [1]})
(def seen (atom []))
(def observed (atom {:cid "cid"}))
(def options {:expected-basis-cid nil :receipt-cid-fn (constantly "receipt")
              :publish! (fn [x] (swap! seen conj x) "commit") :read-ref! (constantly "commit")
              :read-nonce-ref! (fn [_] @observed)})
(with-redefs [causal/published-execution-receipt-sink
              (fn [_ o] (fn [receipt]
                          (check! (= "commit" ((:publish! o) {:receipt receipt :prepared {:test true}})))
                          {:receipt/durable? true :receipt/cid "receipt"}))]
 (let [sink (causal/published-atomic-execution-receipt-sink :db options)]
  (check! (true? (:receipt/durable? (sink {:signed true} marker))))
  (check! (= marker (:nonce (first @seen))))
  (doseq [ref [nil {:cid "wrong"}]]
   (reset! observed ref)
   (check! (try (sink {:signed true} marker) false
                (catch Exception e (= :published-nonce-ref-mismatch (:kotobase.causal-commit/reason (ex-data e)))))))
  (check! (try (sink {:signed true} (dissoc marker :bytes)) false
               (catch Exception e (= :invalid-prepared-nonce (:kotobase.causal-commit/reason (ex-data e))))))))
(prn {:marker-forwarded true :missing-or-wrong-nonce-ref :refused :malformed-marker :refused})
(shutdown-agents)
