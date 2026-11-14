(ns kotobase.nonce-store-test
 (:require [clojure.test :refer [deftest is]] [kotobase.nonce-store :as nonce]
           [kotobase.storage.core :as s] [kotobase.storage.memory :as m]))
(defn reason [f] (try (f) nil (catch Exception e (:kotobase.nonce-store/reason (ex-data e)))))
(defn single-writer [b]
 (reify s/IBlockStore (-put-blocks! [_ xs] (s/-put-blocks! b xs)) (-get-blocks [_ xs] (s/-get-blocks b xs))
        s/IRefStore (-read-ref [_ n] (s/-read-ref b n)) (-compare-and-set-ref! [_ n x y] (s/-compare-and-set-ref! b n x y))
        s/IBackendCapabilities (-capabilities [_] #{:immutable-blocks :cid-addressed-read :conditional-ref :single-writer-ref})))
(defn consumer [b scope] (nonce/consumer {:backend b :scope scope :single-writer? false}))
(defn faulty [b mode]
 (reify s/IBlockStore
   (-put-blocks! [_ xs] (s/-put-blocks! b xs))
   (-get-blocks [_ xs] (if (= mode :missing) {} (s/-get-blocks b xs)))
  s/IRefStore (-read-ref [_ n] (s/-read-ref b n))
   (-compare-and-set-ref! [_ n x y] (if (= mode :bad-cas) {:published? true :current "wrong"} (s/-compare-and-set-ref! b n x y)))
  s/IBackendCapabilities (-capabilities [_] #{:immutable-blocks :cid-addressed-read :conditional-ref :linearizable-ref})))
(deftest persistence-scope-and-concurrent-cas
 (let [backend (m/memory-store) c (consumer backend "tenant/alice")]
  (is (true? (c "nonce-1")))
  (is (false? ((consumer backend "tenant/alice") "nonce-1")))
  (is (true? ((consumer backend "tenant/bob") "nonce-1")))
  (let [gate (promise) a (future @gate (c "race")) b (future @gate (c "race"))]
   (deliver gate true) (is (= #{true false} #{(deref a 10000 :timeout) (deref b 10000 :timeout)})))
  (is (= :invalid-nonce (reason #(c ""))))
  (is (= :invalid-nonce (reason #(c (apply str (repeat 1025 "x"))))))))
(deftest refuses-weak-configuration-and-failed-storage
 (let [backend (single-writer (m/memory-store))]
  (is (= :single-writer-not-asserted (reason #(nonce/consumer {:backend backend :scope "x" :single-writer? false}))))
  (is (true? ((nonce/consumer {:backend backend :scope "x" :single-writer? true}) "a")))
  (is (= :invalid-options (reason #(nonce/consumer {:backend backend :scope "" :single-writer? true}))))
  (let [missing (consumer (faulty (m/memory-store) :missing) "x")
        bad-cas (consumer (faulty (m/memory-store) :bad-cas) "x")]
   (is (= :invalid-marker (reason #(missing "missing"))))
   (is (= :invalid-cas-result (reason #(bad-cas "bad-cas")))))))
