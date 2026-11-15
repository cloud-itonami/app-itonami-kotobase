;; Hosted composition check; publication and execution are controlled ports.
(require '[kotobase.governed-atomic :as atomic]
         '[kotobase.causal-commit :as causal])
(defn reason [f]
  (try (f) nil (catch Exception e (:kotobase.governed-atomic/reason (ex-data e)))))
(let [seen (atom [])
      adapter {:publish! (fn [_] :commit)
               :read-ref! (fn [] :commit)
               :read-nonce-ref! (fn [_] {:cid :marker})}
      publication {:database :database :expected-basis-cid nil
                   :receipt-cid-fn identity :adapter adapter}
      host {:backend :backend :scope "scope" :admit-basis! (constantly true)}
      options {:request :request}
      verification {:registry :registry}
      receipt {:signed :receipt} marker {:marker :nonce}]
  (assert (= :invalid-publication-options
             (reason #(atomic/execute-published-registry-bound!
                        options host verification (update publication :adapter dissoc :read-nonce-ref!)))))
  (assert (= :invalid-publication-options
             (reason #(atomic/execute-published-registry-bound!
                        options (assoc host :publish! identity) verification publication))))
  (with-redefs [causal/published-atomic-execution-receipt-sink
                (fn [db config]
                  (swap! seen conj [:sink db config])
                  (fn [r m] (swap! seen conj [:receipt r m]) :ack))
                atomic/execute-registry-bound!
                (fn [o h v]
                  (swap! seen conj [:execution o (dissoc h :publish!) v])
                  ((:publish! h) receipt marker))]
    (assert (= :ack (atomic/execute-published-registry-bound! options host verification publication))))
  (assert (= [[:sink :database (merge {:expected-basis-cid nil :receipt-cid-fn identity} adapter)]
              [:execution options host verification] [:receipt receipt marker]] @seen))
  (prn {:hosted-composition :pass :invalid-adapter :refused :host-override :refused}))
