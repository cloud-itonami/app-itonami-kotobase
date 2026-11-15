(require '[clojure.string :as str]
         '[kotobase.persisted-basis :as pb]
         '[otehon.kotobase-forest-blocks :as forest-blocks])
(def original (slurp "nixos/otehon/storage/oracle/proof_location_admission_replay.clj"))
(load-string (first (str/split original #"\(defn guard")))
(def frontier-output (System/getenv "FRONTIER_FOREST_OUTPUT"))
(def fb (forest-blocks/open-backend (str frontier-output "/store") (str frontier-output "/stage") tool))
(defn guard-config [events change-proof]
   {:backend fb :ref-name "frontier" :database-id (:database-id checkpoint)
    :fetch-entry! (fn [{m :manifest r :request}]
                    (swap! events conj :basis)
                    (verify-location! m location signed-proof-cid signed-basis signed-policy)
                    (when-not (= (:base/commit r) signed-basis) (throw (ex-info "basis mismatch" {})))
                    (let [raw (or (catalog/get-group-block h signed-proof-cid) (throw (ex-info "missing proof" {})))]
                      (writer/validate-ipld-block {:cid signed-proof-cid :bytes raw})
                      (change-proof (edn/read-string (String. ^bytes raw "UTF-8")))))
    :verify-entry! (fn [{:keys [entry]}] (verify-proof entry checkpoint signed-policy))})
(load-string (-> (str "(defn checked-options" (second (str/split original #"\(defn checked-options" 2))) (str/replace "governed/execute-bound!" "pb/execute!") (str/replace "(guard events " "(guard-config events ")))
