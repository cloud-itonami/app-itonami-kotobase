;; Hosted fixture with actual Prolly, Datalog, policy, signed proof and Forest sink.
(require '[clojure.string :as str] '[clojure.edn :as edn]
         '[kotobase.governed-read :as read] '[kotobase.signed-basis :as signed]
         '[kotobase.core :as core] '[kotobase.causal-commit :as causal]
         '[otehon.kotobase-forest-blocks :as fb])
(load-file "nixos/otehon/storage/oracle/proof_location_common.clj")
(def frontier-output (or (System/getenv "FRONTIER_FOREST_OUTPUT") (throw (ex-info "frontier output required" {}))))
(def output (or (System/getenv "BOUND_RECEIPT_OUTPUT") (throw (ex-info "receipt output required" {}))))
(def frontier-backend (fb/open-backend (str frontier-output "/store") (str frontier-output "/stage") tool))
(assert (.mkdir (java.io.File. output)))
(assert (.mkdir (java.io.File. (str output "/stage"))))
(store/init! (str output "/store"))
(defn receipt-backend [] (fb/open-backend (str output "/store") (str output "/stage") tool))
(defn receipt-db [] (core/open {:storage (receipt-backend) :encrypt-fn identity :decrypt-fn identity :blind-fn pr-str :visible? (constantly true)}))
(defn config [events transform]
 {:backend frontier-backend :ref-name "frontier" :database-id (:database-id checkpoint)
  :fetch-entry! (fn [{m :manifest}]
                  (swap! events conj :basis)
                  (verify-location! m location signed-proof-cid signed-basis signed-policy)
                  (let [raw (or (catalog/get-group-block h signed-proof-cid) (throw (ex-info "missing proof" {})))]
                    (writer/validate-ipld-block {:cid signed-proof-cid :bytes raw})
                    (transform (edn/read-string (String. ^bytes raw "UTF-8")))))
  :verify-entry! (signed/verifier {:digest! sha :checkpoint! (signed/checkpoint-reader engine)
    :verify-location! (fn [{m :manifest}] (verify-location! m location signed-proof-cid signed-basis signed-policy))
    :verify-signature! (fn [{:keys [issuer key-id payload signature]}]
      (and (= issuer signer) (= key-id "fixture-key")
           (ed/verify (ed/public-key (vec (repeat 32 3))) (payload-bytes payload) signature)))})})
(defn run! [transform]
 (let [events (atom []) nonce (get-in opts [:authority :consume-nonce!]) auth (:authorize! opts) ev (:evaluate! opts)
       request* (assoc request :nonce "fixture-bound-prolly-receipt-20261114")
       req (-> opts (dissoc :commit!) (assoc :request request*
              :commit {:database (receipt-db) :expected-basis-cid nil :receipt-cid-fn id/value-cid}
              :authority-decision {:decision/status :allow :decision/runtime-capability-spec
                {:capability/action :object/read :capability/resource (get-in query [:scope :resources])
                 :capability/tenant (:tenant request*) :capability/principal (:principal request*)}})
            (assoc-in [:authority :consume-nonce!] (fn [n] (swap! events conj :nonce) (nonce n)))
            (assoc :authorize! (fn [q] (swap! events conj :authorize) (auth q))
                   :evaluate! (fn [q d] (swap! events conj :evaluate) (ev q d))))
       original causal/execution-receipt-sink
       result (try (with-redefs [causal/execution-receipt-sink (fn [db cfg] (let [sink (original db cfg)] (fn [r] (swap! events conj :commit) (sink r))))]
                     (read/read-bound! req (config events transform)))
                   (catch Exception e {:error (ex-data e)}))]
   {:events @events :result result}))
(let [before (:root (store/read-store (str output "/store")))
      bad (run! #(assoc % :signature (vec (repeat 64 0))))
      after (:root (store/read-store (str output "/store")))]
 (assert (= [:basis] (:events bad))) (assert (some? (get-in bad [:result :error]))) (assert (= before after)))
(let [r (run! identity) result (:result r) commit (get-in result [:provenance :receipt-commit-cid]) cid (get-in result [:provenance :receipt-cid])]
 (assert (= [:basis :nonce :authorize :evaluate :commit] (:events r)))
 (assert (= [{:file/root "bafyreidqkmghsd65dwq22umpkrqecyamfhevsbq7xemjfe7tb6wnhvxsny" :chunk/cid "bafkreidxq3azg4pwxcuurzkrmjha3uuzhstmefbzx6dw6z5bjaqya64mra"}] (:rows result)))
 (assert (= [(:execution/receipt result)] (:receipt/records (causal/receipt-at (receipt-db) commit cid))))
 (spit (str output "/result.edn") (pr-str {:commit commit :receipt cid :frontier-output frontier-output}))
 (prn {:events (:events r) :receipt cid :commit commit :prolly true :signed-basis true :policy :fixture-dango :datalog true :fresh-readback true :bad-signature :rejected :pass true}))
(shutdown-agents)
