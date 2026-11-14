(require '[kotobase.authority-execution-link :as link]
         '[kotobase.execution-identity :as id]
         '[kotobase.governed-execution-test :as fixture]
         '[kotobase.causal-trust-test :as trust-fixture]
         '[kotobase.governed-read-test :as reads]
         '[kotobase.core :as core]
         '[kotobase.storage.memory :as memory]
         '[grant.causal-trust :as trust] '[kotobase.execution-keys :as keys])
(def db (core/open {:storage (memory/memory-store) :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(def basis (core/commit-at! db nil [["INV-42" "invoice/id" "INV-42"]]))
(def req (reads/request-at db basis (atom false)))
(require '[kotobase.governed-read :as governed])
(def receipt (:execution/receipt (governed/read! req)))
(def decision trust-fixture/reader-decision)
(def authority-receipt (trust/receipt (assoc trust-fixture/receipt-template
  :causal.receipt/id "fixture-decision" :causal.receipt/outcome {:outcome/status :served} :causal.receipt/at "2026-08-27T01:00:00Z")))
(def unsigned {:format link/format-id :request/digest (id/value-cid (:request req))
 :execution-receipt/cid (id/value-cid receipt) :authority-receipt/cid (id/value-cid authority-receipt)
 :tenant (get-in req [:request :tenant]) :epoch (get-in req [:request :authority/epoch])})
(def signed (assoc unsigned :signature (fixture/proof unsigned)))
(def options {:link signed :request (:request req) :query (:query req) :execution-receipt receipt
 :authority-receipt authority-receipt :value-cid id/value-cid :verify fixture/verify})
(assert (true? (link/verify! options)))
(doseq [bad [(assoc-in options [:link :tenant] "other")
             (assoc-in options [:link :authority-receipt/cid] "wrong")
             (assoc-in options [:query :scope :resources] #{"other"})
             (assoc-in options [:link :signature] "bad")]]
  (assert (try (link/verify! bad) false (catch Exception _ true))))
(let [signature {:key/id "executor" :key/algorithm :ed25519 :signature/value (fixture/proof unsigned)}
      checked (assoc unsigned :signature signature)
      registry (fn [scope] (if (= scope {:role :executor :tenant "acme" :epoch 7})
                            [{:key/id "executor" :key/algorithm :ed25519 :key/material "fixture"}] []))
      verifier (keys/verifier {:registry registry :verify-with (fn [{:keys [payload-cid signature-value]}]
                                                                  (= signature-value (str "sig:" payload-cid)))})]
 (assert (true? (link/verify! (assoc options :link checked :verify verifier))))
 (assert (try (link/verify! (assoc options :link checked :verify (keys/verifier {:registry (constantly []) :verify-with (constantly true)}))) false (catch Exception _ true))))
(prn {:linked true :tampering :refused :pass true})
(shutdown-agents)
