(ns kotobase.signed-basis-test
  (:require [clojure.test :refer [deftest is]]
            [kotobase.signed-basis :as signed]
            [kotobase.engine.identity :as identity]
            [kotobase.engine.profile :as profile]))
(def commit {:format identity/logical-commit-format :database-id "db" :epoch 1 :parents []
             :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema"
             :model-contract-root "model" :admission-policy-root "policy"})
(defn digest [x] (str "digest:" x))
(def entry {:root (digest (identity/logical-commit-string commit)) :commit commit
            :issuer "issuer" :key-id "key" :signature "signed" :physical-root "physical"})
(def context {:entry entry :request {:base/commit "physical" :authority/policy "policy"}
              :manifest {:data/commit "physical" :authority/policy "policy"}})
(def checkpoint {:database-id "db" :epoch 1 :logical-checkpoint-root "cp" :physical-root "physical" :engine profile/prolly})
(defn verify-signature [{:keys [issuer key-id payload signature]}]
  (and (= issuer "issuer") (= key-id "key") (= signature "signed")
       (= payload (identity/signature-payload {:logical-commit-root (:root entry) :issuer issuer :key-id key-id}))))
(def config {:digest! digest :checkpoint! (constantly checkpoint)
             :verify-location! (constantly true) :verify-signature! verify-signature})
(defn reason [f] (try (f) nil (catch Exception e (:kotobase.signed-basis/reason (ex-data e)))))
(deftest all-binds-are-required
  (is (true? ((signed/verifier config) context)))
  (is (= :invalid-config (reason #(signed/verifier {}))))
  (is (= :invalid-envelope (reason #((signed/verifier config) (assoc-in context [:entry :physical-root] "other")))))
  (is (= :policy-mismatch (reason #((signed/verifier config) (assoc-in context [:entry :commit :admission-policy-root] "other")))))
  (is (= :root-mismatch (reason #((signed/verifier config) (assoc-in context [:entry :root] "other")))))
  (is (= :location-refused (reason #((signed/verifier (assoc config :verify-location! (constantly false))) context))))
  (doseq [change [{:physical-root "other"} {:database-id "other"} {:epoch 2} {:logical-checkpoint-root "other"}]]
    (is (= :checkpoint-mismatch (reason #((signed/verifier (assoc config :checkpoint! (constantly (merge checkpoint change)))) context)))))
  (is (= :signature-refused (reason #((signed/verifier config) (assoc-in context [:entry :signature] "wrong")))))
  (is (= :signature-refused (reason #((signed/verifier config) (assoc-in context [:entry :key-id] "wrong"))))))

(deftest refused-signature-never-opens-checkpoint
  (let [calls (atom [])
        check (signed/verifier (assoc config
                                :checkpoint! (fn [_] (swap! calls conj :checkpoint) checkpoint)
                                :verify-signature! (fn [_] (swap! calls conj :signature) false)))]
    (is (= :signature-refused (reason #(check context))))
    (is (= [:signature] @calls))))
