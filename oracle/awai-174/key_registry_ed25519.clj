;; Local one-time reference only; no production key material or runtime fallback.
(require '[kotobase.execution-keys :as keys] '[ed25519.sign :as ed])
(def seed-a (vec (repeat 32 3)))
(def seed-b (vec (repeat 32 4)))
(def payload "bafk-fixture-payload")
(def bytes (mapv #(bit-and 255 %) (.getBytes payload "UTF-8")))
(def sig (ed/sign (ed/secret-key! seed-a) bytes))
(def key-a {:key/id "fixture-key" :key/algorithm :ed25519 :key/material (ed/public-key seed-a)})
(def key-b (assoc key-a :key/material (ed/public-key seed-b)))
(def context {:record :execution-manifest :tenant "fixture" :epoch 7
              :payload-cid payload
              :signature {:key/id "fixture-key" :key/algorithm :ed25519 :signature/value sig}})
(defn attempt [entries ctx]
  (let [calls (atom 0)
        verify (keys/verifier
                {:registry (fn [scope]
                             (if (= scope {:role :issuer :tenant "fixture" :epoch 7}) entries []))
                 :verify-with (fn [{:keys [key payload-cid signature-value]}]
                                (swap! calls inc)
                                (ed/verify (:key/material key)
                                           (mapv #(bit-and 255 %) (.getBytes payload-cid "UTF-8"))
                                           signature-value))})]
    {:result (try (verify ctx) (catch clojure.lang.ExceptionInfo e
                                (:kotobase.execution-keys/reason (ex-data e))))
     :calls @calls}))
(assert (= {:result true :calls 1} (attempt [key-a] context)))
(assert (= {:result false :calls 1} (attempt [key-b] context)))
(assert (= {:result false :calls 1} (attempt [key-a] (assoc context :payload-cid "different"))))
(assert (= {:result :no-key-registered :calls 0} (attempt [key-a] (assoc context :epoch 8))))
(assert (= {:result :duplicate-key-id :calls 0} (attempt [key-b key-a] context)))
(assert (= {:result :duplicate-key-id :calls 0} (attempt [key-a key-b] context)))
(prn {:status :passed :real-ed25519 true :controls 6})
