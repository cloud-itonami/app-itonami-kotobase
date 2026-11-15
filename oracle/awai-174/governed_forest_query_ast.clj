;; Hosted reference: the request-bound query's actual clauses reach Datalog.
(require '[clojure.string :as str])
(def base-path (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "oracle root required" {}))))
(def source-text (slurp (str base-path "/oracle/awai-174/governed_host_scoped_memory.clj")))
(load-string (first (str/split source-text #"\(let \[before \(memory/snapshot memory-backend\)" 2)))

(def data-source
  (reify source/IPatternSource
    (-scan [_ pattern]
      (mapv (fn [{:keys [e a v]}] {:s e :p a :o v}) (eng/scan engine snap pattern)))))
(def visible (policy/visible-for p #{policy/read-protected-capability}))
(def clauses-base '[["file" :file/root ?root] ["file" :file/chunk ?c] [?c :chunk/cid ?cid]])
(defn run-actual! [nonce clauses limit tamper?]
  (let [events (atom [])
        q (assoc query :where clauses :limit limit)
        req-record (assoc request :nonce nonce :query/digest (if tamper? (id/value-cid query) (id/value-cid q)))
        req (-> opts (dissoc :commit!)
                (assoc :query q :request req-record :authority-decision real-decision
                       :commit {:database (receipt-db) :expected-basis-cid nil :receipt-cid-fn id/value-cid}
                       :evaluate! (fn [received _]
                                    (swap! events conj :evaluate)
                                    (assert (= received q))
                                    (mapv (fn [[root cid]] {:file/root root :chunk/cid cid})
                                          (datalog/q data-source {:find '[?root ?cid] :where (:where received)}
                                                     #(visible (update % :p str)))))))
        result (try (host/read-registry-signed-authority! req (config events identity) (verification-config)
                           {:backend (receipt-backend) :single-writer? false} host-config)
                    (catch Exception e {:error (ex-data e)}))]
    {:query q :request req-record :events @events :result result}))

(let [exact (run-actual! "ast-exact" (conj clauses-base '[?c :chunk/index 8]) 10 false)
      absent (run-actual! "ast-absent" (conj clauses-base '[?c :chunk/index 999999]) 10 false)
      head-before (core/head (receipt-db))
      tamper (run-actual! "ast-tamper" clauses-base 1 true)]
  (assert (= 1 (count (get-in exact [:result :rows]))))
  (assert (nil? (get-in absent [:result :error])))
  (assert (vector? (get-in absent [:result :rows])))
  (assert (empty? (get-in absent [:result :rows])))
  (doseq [r [exact absent]]
    (assert (= (id/value-cid (:request r)) (get-in r [:result :execution/receipt :request/digest])))
    (assert (string? (get-in r [:result :provenance :receipt-cid]))))
  (assert (some? (get-in tamper [:result :error])))
  (assert (= head-before (core/head (receipt-db))))
  (assert (not (some #{:evaluate} (:events tamper))))
  (assert (not= (id/value-cid (:query exact)) (id/value-cid (:query absent))))
  (prn {:source :forest-prolly :request-query-clauses :forwarded
        :index-8 (count (get-in exact [:result :rows])) :index-absent (count (get-in absent [:result :rows]))
        :query-digest-mismatch :refused :receipt-head :unchanged-on-mismatch
        :nonce-and-receipt-backend :memory :pass true}))
(shutdown-agents)
