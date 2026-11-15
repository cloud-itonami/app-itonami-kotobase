(require '[kotobase.governed-atomic :as atomic] '[kotobase.nonce-store :as nonce] '[otehon.kotobase-forest-blocks :as fb] '[otehon.storage :as store])
(let [root (System/getenv "EXISTING_LEDGER")
      db (fb/open-backend (str root "/store") (str root "/unused-stage") "/nonexistent/forest-tool")
      request {:request/version 1 :principal "fixture-principal" :tenant "test" :graph "fixture-graph" :operation :read :query/digest "q" :base/commit "b" :authority/policy "p" :authority/epoch 7 :nonce "allow" :expires-at "2026-09-02T15:00:00Z"}
      scope (atomic/request-scope request) before (store/read-store (str root "/store"))]
  (assert (false? (nonce/available? db scope "allow")))
  (assert (false? (nonce/available? db scope "deny")))
  (assert (= before (store/read-store (str root "/store"))))
  (prn {:previous-ledger-replays :refused :root-unchanged true :writer-unavailable true :pass true}))
(shutdown-agents)
