;; Hosted fault injection: the process is killed after nonce CAS, before policy.
(require '[clojure.string :as str])
(def root (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "oracle root required" {}))))
(def source (slurp (str root "/oracle/awai-174/governed_persistent_forest.clj")))
(def needle ":authorize! (fn [q] (swap! events conj :authorize) (auth q))")
(assert (= 1 (count (re-seq (re-pattern (java.util.regex.Pattern/quote needle)) source))))
(def injected (str/replace source needle
  ":authorize! (fn [_] (spit (str output \"/nonce-committed.signal\") \"ready\") (loop [] (Thread/sleep 1000) (recur)))"))
(load-string (str/replace injected "fixture-persistent-entry-20261114" "fixture-nonce-crash-20261114"))
