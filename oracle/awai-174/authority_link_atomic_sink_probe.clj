(load-file "oracle/awai-174/authority_execution_link_probe.clj")
(require '[kotobase.causal-commit :as causal])
(def sink-options {:expected-basis-cid nil :value-cid id/value-cid
 :request (:request req) :query (:query req) :authority-receipt authority-receipt
 :verify-authority! (constantly true) :sign fixture/proof :verify fixture/verify})
(def storage (memory/memory-store))
(def db2 (core/open {:storage storage :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def before (memory/snapshot storage))
(doseq [overrides [{:verify-authority! (constantly false)} {:sign (constantly "bad")}
                   {:request (assoc (:request req) :tenant "other")}]]
 (let [sink (causal/linked-execution-receipt-sink db2 (merge sink-options overrides))]
  (assert (try (sink receipt) false (catch Exception _ true)))
  (assert (= before (memory/snapshot storage)))))
(def ack ((causal/linked-execution-receipt-sink db2 sink-options) receipt))
(def records (:receipt/records (causal/receipt-at db2 (:receipt/commit-cid ack) (:receipt/cid ack))))
(assert (= 3 (count records)))
(assert (= receipt (first records)))
(assert (= authority-receipt (second records)))
(assert (link/verify! (assoc options :link (nth records 2))))
(assert (true? (:receipt/durable? ack)))
(prn {:atomic-commit :one :records 3 :readback :verified :failure-before-commit :verified :pass true})
(shutdown-agents)
