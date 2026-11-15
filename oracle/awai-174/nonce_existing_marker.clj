;; One-off hosted oracle for persisted nonce replay and races.
(require '[kotobase.nonce-store :as nonce] '[kotobase.storage.core :as s])
(defn check! [v msg] (when-not v (throw (ex-info msg {}))))
(defn reason [f] (try (f) nil (catch Exception e (:kotobase.nonce-store/reason (ex-data e)))))
(let [refs (atom {}) blocks (atom {}) puts (atom 0) racing (atom false)
      db (reify s/IBlockStore
           (-put-blocks! [_ xs] (swap! puts inc) (doseq [{:keys [cid bytes]} xs] (swap! blocks assoc cid bytes)) true)
           (-get-blocks [_ ids] (select-keys @blocks ids))
           s/IRefStore
           (-read-ref [_ n] (when-let [cid (get @refs n)] {:cid cid :version 1}))
           (-compare-and-set-ref! [_ n expected next]
             (if @racing (do (swap! refs assoc n next) {:published? false :current next :version 1})
               (if (= expected (get @refs n)) (do (swap! refs assoc n next) {:published? true :current next :version 1})
                 {:published? false :current (get @refs n) :version 1})))
           s/IBackendCapabilities (-capabilities [_] #{:immutable-blocks :cid-addressed-read :conditional-ref :single-writer-ref}))
      consume (nonce/consumer {:backend db :scope "scope" :single-writer? true})]
 (let [prepared (nonce/prepare-marker "scope" "a")]
  (check! (true? (nonce/available? db "scope" "a")) "fresh preflight refused")
  (check! (zero? @puts) "preflight wrote block")
  (check! (and (string? (:ref-name prepared)) (string? (:marker-cid prepared)) (:bytes prepared)) "invalid prepared marker"))
 (check! (true? (consume "a")) "fresh nonce refused")
 (check! (false? (nonce/available? db "scope" "a")) "consumed preflight allowed")
 (let [n @puts] (check! (false? (consume "a")) "replay accepted") (check! (= n @puts) "replay wrote block"))
 (let [cid (first (vals @refs)) original (get @blocks cid)]
  (swap! blocks dissoc cid)
  (check! (= :invalid-marker (reason #(consume "a"))) "missing marker accepted")
  (swap! blocks assoc cid (byte-array [1 2 3]))
  (check! (try (consume "a") false (catch Exception _ true)) "corrupt marker accepted")
  (swap! blocks assoc cid original)
  (let [k (first (keys @refs))] (swap! refs assoc k "wrong-cid")
   (check! (= :invalid-nonce-ref (reason #(consume "a"))) "wrong ref accepted")
   (swap! refs assoc k cid)))
 (reset! racing true)
 (check! (false? (consume "race")) "CAS race accepted")
 (reset! racing false)
 (prn {:fresh :accepted :replay :read-only :missing-or-corrupt-marker :refused :wrong-ref :refused :cas-race :refused :pass true}))
(shutdown-agents)
