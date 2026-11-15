(ns kotobase.persisted-basis-test
  (:require [clojure.test :refer [deftest is]]
            [kotobase.persisted-basis :as basis]
            [kotobase.frontier-store :as fs]
            [kotobase.storage.memory :as memory]
            [kotobase.engine.identity :as identity]))
(def commit {:format identity/logical-commit-format :database-id "db" :epoch 1
             :parents [] :tx-root "tx" :logical-checkpoint-root "cp" :schema-root "schema"
             :model-contract-root "model" :admission-policy-root "policy"})
(def entry {:root "root" :commit commit :signature "signed"})
(def context {:request {:base/commit "physical" :authority/policy "policy"}
              :manifest {:data/commit "physical" :authority/policy "policy"}})
(defn reason [f] (try (f) nil (catch Exception e (:kotobase.persisted-basis/reason (ex-data e)))))
(defn config [backend fetch verify]
  {:backend backend :ref-name "r" :database-id "db" :fetch-entry! fetch :verify-entry! verify})
(deftest verifies-every-read-and-never-advances-frontier
  (let [backend (memory/memory-store) calls (atom [])
        _ (fs/accept-and-store! backend "r" "db" {:allow-initialize? true :candidate-root "root" :path [entry] :verify-entry (constantly true)})
        saved (memory/snapshot backend)
        fetch (fn [_] (swap! calls conj :fetch) entry)
        verify (fn [{e :entry}] (swap! calls conj :verify) (= "signed" (:signature e)))
        admit (basis/admission (config backend fetch verify))]
    (is (true? (admit context)))
    (is (= [:fetch :verify] @calls))
    (is (= saved (memory/snapshot backend)))
    (is (= :request-manifest-mismatch (reason #(admit (assoc-in context [:request :base/commit] "other")))))
    (is (= :entry-mismatch (reason #((basis/admission (config backend (constantly (assoc-in entry [:commit :admission-policy-root] "wrong")) verify)) context))))
    (is (= :unverified-entry (reason #((basis/admission (config backend (constantly entry) (constantly false))) context))))
    (is (= :frontier-mismatch (reason #((basis/admission (config backend (constantly (assoc entry :root "other")) (constantly true))) context))))
    (is (= :frontier-mismatch (reason #((basis/admission (config backend (constantly (assoc-in entry [:commit :epoch] 2)) (constantly true))) context))))
    (is (= saved (memory/snapshot backend)))))
(deftest missing-state-and-bad-config-refused
  (is (= :invalid-config (reason #(basis/admission {}))))
  (is (= :missing-frontier (reason #((basis/admission (config (memory/memory-store) (constantly entry) (constantly true))) context)))))

(deftest stale-entry-is-rejected-before-verifier-and-change-during-verify-refuses
  (let [backend (memory/memory-store) calls (atom [])
        _ (fs/accept-and-store! backend "r" "db" {:allow-initialize? true :candidate-root "root" :path [entry] :verify-entry (constantly true)})
        verify (fn [_] (swap! calls conj :verify) true)]
    (is (= :frontier-mismatch (reason #((basis/admission (config backend (constantly (assoc entry :root "other")) verify)) context))))
    (is (= [] @calls))
    (let [old (fs/read-frontier backend "r" "db")
          next-entry (assoc entry :root "next" :commit (assoc commit :epoch 2 :parents ["root"]))
          advance (fn [_] (fs/accept-and-store! backend "r" "db" {:candidate-root "next" :path [next-entry] :verify-entry (constantly true)}) true)]
      (is (= :frontier-changed (reason #((basis/admission (config backend (constantly entry) advance)) context))))
      (is (not= old (fs/read-frontier backend "r" "db"))))))
