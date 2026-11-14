(require '[clojure.string :as str] '[kotobase.signed-basis :as signed])
(binding [*command-line-args* ["read"]]
  (load-string (first (str/split (slurp "nixos/otehon/storage/oracle/signed_basis_multipack.clj") #"\(defn fixture-blocks"))))
(let [attempt (try ((signed/checkpoint-reader engine) "bafkreiaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") :accepted
                   (catch Exception _ :rejected))]
  (assert (= :rejected attempt))
  (prn {:missing-physical-root attempt :pass true}))
(shutdown-agents)
