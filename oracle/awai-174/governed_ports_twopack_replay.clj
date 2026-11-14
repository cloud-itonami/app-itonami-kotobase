;; Fresh-process registry admission and nonce replay against the saved Forest.
(require '[clojure.string :as str] '[clojure.edn :as edn])
(def project (or (System/getenv "KOTOBASE_ORACLE_ROOT") (throw (ex-info "oracle root required" {}))))
(load-string (first (str/split (slurp (str project "/oracle/awai-174/governed_ports_twopack.clj"))
                               #"\(case \(first \*command-line-args\*\)" 2)))
(let [ctx (source-context) {:keys [receipt commit basis public-key]} (edn/read-string (slurp (str receipt-root "/ids.edn")))
      key (.generatePublic (KeyFactory/getInstance "Ed25519") (X509EncodedKeySpec. (.decode (Base64/getDecoder) ^String public-key)))
      db (rbackend) get-block #(storage/get-block db %)
      record (first (:receipt/records (causal/receipt-at (open-db db) commit receipt)))
      request (read-block get-block (:request/digest record))
      manifest (read-block get-block (:execution/manifest record))
      root (get-in ctx [:entry :physical-root]) pcid (:policy-cid ctx) resource (str "kotoba://basis/" root)
      q {:find [:file/name] :where '[[?e :file/name ?name]]
         :scope {:tenant "test" :resources #{resource} :purpose :audit :basis root} :limit 3}
      events (atom []) before (storage/-read-ref db "governed-receipt")
      basis-config {:backend db :ref-name "governed-frontier" :database-id (get-in ctx [:entry :commit :database-id])
                    :fetch-entry! (fn [_] (swap! events conj :basis) (:entry ctx))}
      verification {:digest! sha :checkpoint! (signed/checkpoint-reader (:engine ctx))
                    :verify-location! (fn [{:keys [entry manifest]}]
                       (let [location (location-for ctx)]
                         (and (= (:location/manifest manifest) (id/value-cid location))
                              (= (:physical-root entry) (:physical-root location))
                              (= (:policy location) (:policy-cid ctx)))))
                    :registry (fn [scope] (if (= scope {:role :issuer :tenant "test" :epoch 7})
                        [{:key/id "fixture-key" :key/algorithm :ed25519 :key/material (ed/public-key seed)}] []))
                    :algorithm :ed25519
                    :issuer-authorized? (fn [{:keys [issuer key]}] (and (= issuer "fixture-issuer") (= "fixture-key" (:key/id key))))
                    :verify-with (fn [{:keys [key payload signature-value]}]
                                   (true? (ed/verify (:key/material key) (bytes-of payload) signature-value)))}
      no-effect (fn [& _] (swap! events conj :unexpected-effect) (throw (ex-info "unexpected effect" {})))
      opts {:commit {:database (open-db db) :expected-basis-cid nil :receipt-cid-fn id/value-cid
                     :publication {:publish! no-effect :read-ref! no-effect}}
            :authority-decision {:decision/status :allow :decision/runtime-capability-spec
              {:capability/action :object/read :capability/resource #{resource} :capability/tenant "test" :capability/principal "fixture-principal"}}
            :request request :manifest manifest :authority {:now "2026-09-02T14:30:00Z" :epoch 7 :consume-nonce! no-effect}
            :value-cid id/value-cid :verify (fn [{:keys [payload-cid signature]}] (verify-jca key payload-cid signature))
            :plan-digest no-effect :cost no-effect :implementation/build "fixture" :sign no-effect
            :authorize! no-effect :schema {:file/name {:class :public}} :grant {:granted #{:public} :scopes #{}}
            :query q :evaluate! no-effect}]
  (ensure! (= basis (:basis-cid ctx)) "basis changed")
  (verify-bundle! get-block record ctx key)
  (ensure! (= :nonce-replayed
              (try (governed-read/read-registry-persistent! opts basis-config verification {:backend db :single-writer? true}) nil
                   (catch Exception e (:kotobase.authority-window/reason (ex-data e))))) "replay not refused")
  (ensure! (= [:basis] @events) "unexpected policy/evaluation/publication")
  (ensure! (= before (storage/-read-ref db "governed-receipt")) "receipt ref changed")
  (prn {:fresh-process true :registry-admission :verified :nonce-replay :refused :receipt-ref :unchanged :pass true}))
(shutdown-agents)
