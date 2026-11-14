(ns signed-basis-keys-node
  (:require [kotobase.signed-basis-keys :as basis-keys]
            [kotobase.engine.identity :as identity]))

(def key-a {:key/id "key-a" :key/algorithm :ed25519 :key/material "public"})
(def entry {:root "root" :issuer "issuer" :key-id "key-a" :signature "sig"})
(def context {:entry entry :request {:tenant "tenant" :authority/epoch 7}
              :issuer "issuer" :key-id "key-a" :signature "sig"
              :payload (identity/signature-payload
                        {:logical-commit-root "root" :issuer "issuer" :key-id "key-a"})})
(defn probe [entries issuer-result]
  (let [events (atom [])
        verifier (basis-keys/verifier
                  {:registry (fn [_] (swap! events conj :registry) entries)
                   :algorithm :ed25519
                   :issuer-authorized? (fn [_] (swap! events conj :issuer) issuer-result)
                   :verify-with (fn [_] (swap! events conj :crypto) true)})
        result (try (verifier context)
                    (catch :default e
                      (or (:kotobase.signed-basis-keys/reason (ex-data e))
                          (:kotobase.execution-keys/reason (ex-data e)))))]
    {:result result :events @events}))
(assert (= {:result true :events [:registry :issuer :crypto]} (probe [key-a] true)))
(assert (= {:result :duplicate-key-id :events [:registry]} (probe [key-a key-a] true)))
(assert (= {:result :issuer-not-authorized :events [:registry :issuer]} (probe [key-a] (js/Promise.resolve true))))
(println "signed-basis-key-node-controls-passed")
