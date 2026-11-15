(ns kotobase.governed-atomic
  "Hosted synchronous governed reads with request-bound atomic nonce publication.

  The host supplies an authenticated, stable scope and a publisher that atomically
  commits the exact prepared marker together with the durable receipt. This module
  does not implement that storage transaction, authority or distributed fencing."
  (:require [kotobase.execution-contract :as contract]
            [kotobase.causal-commit :as causal]
            [kotobase.execution-keys :as keys]
            [kotobase.governed-execution :as governed]
            [kotobase.nonce-store :as nonce]
            [kotoba.value.codec :as codec]))

(defn- reject! [reason]
  (throw (ex-info "atomic governed execution refused"
                  {:kotobase.governed-atomic/reason reason})))

(defn request-scope
  "Derive the versioned replay domain from a structurally valid request.
  This does not authenticate its principal, tenant, graph or epoch. The trusted
  host must authenticate these fields and ensure its durable ledger uses this
  exact domain. Different graphs or epochs deliberately have distinct scopes."
  [request]
  (contract/validate-request! request)
  (codec/value-cid {:format :kotobase.nonce/request-scope-v1
                    :tenant (:tenant request) :principal (:principal request)
                    :graph (:graph request) :epoch (:authority/epoch request)}))

(defn execute-bound!
  "Run a synchronous governed read and pass its exact request-bound marker to
  PUBLISH! `(fn [receipt prepared-marker] durable-ack)`. The publisher is a
  trusted host component: it must verify marker semantics and atomically commit
  its absent ref with the receipt and all required blocks. SCOPE must equal the
  request-derived domain; the host must authenticate its fields and use the
  same durable ledger. Existing ledgers in another domain need explicit migration.
  Preflight does not reserve a nonce; concurrent
  evaluations are possible, but the losing publisher must fail closed. This is
  not a Native execution path or a distributed/anti-rollback guarantee."
  [options {:keys [backend scope publish! admit-basis!] :as host}]
  (when-not (and (map? options) (map? host)
                 (= #{:backend :scope :publish! :admit-basis!} (set (keys host)))
                 (fn? publish!) (fn? admit-basis!)
                 (not (contains? options :commit!))
                 (not (contains? (:authority options) :consume-nonce!)))
    (reject! :invalid-options))
  (let [request (:request options)
        request-nonce (:nonce request)
        derived-scope (request-scope request)
        _ (when-not (= scope derived-scope) (reject! :scope-mismatch))
        marker (nonce/prepare-marker scope request-nonce)
        checked (atom false)
        publishing (atom false)
        consume! (fn [n]
                   (when-not (= request-nonce n) (reject! :nonce-mismatch))
                   (let [available (nonce/available? backend scope n)]
                     (when-not (boolean? available) (reject! :async-backend))
                     (when available (reset! checked true))
                     available))
        commit! (fn [receipt]
                  (when-not (and @checked (compare-and-set! publishing false true))
                    (reject! :unexpected-publication))
                  (publish! receipt marker))]
    (governed/execute-bound!
      (-> options
          (assoc :commit! commit!)
          (assoc-in [:authority :consume-nonce!] consume!))
      admit-basis!)))

(defn execute-registry-bound!
  "Run the bound hosted path with the existing scoped execution-key verifier.
  VERIFICATION is passed to `execution-keys/verifier`. The host owns registry
  authenticity, cryptographic implementation, principal admission, time and
  ledger durability; key lookup alone does not establish authority."
  [options host verification]
  (when-not (and (map? options) (not (contains? options :verify)))
    (reject! :invalid-verifier-options))
  (execute-bound! (assoc options :verify (keys/verifier verification)) host))

(defn execute-published-registry-bound!
  "Compose a governed read with canonical receipt and nonce readback.

  PUBLICATION supplies the receipt read database, canonical receipt CID,
  expected receipt basis and the three callbacks of a trusted atomic adapter.
  The adapter must verify the complete candidate closure and atomically publish
  both references. Registry provenance, caller identity, basis admission,
  policy, transaction durability and stable readback remain host obligations."
  [options host verification {:keys [database expected-basis-cid receipt-cid-fn adapter] :as publication}]
  (when-not (and (map? host) (= #{:backend :scope :admit-basis!} (set (keys host)))
                 (map? publication)
                 (= #{:database :expected-basis-cid :receipt-cid-fn :adapter} (set (keys publication)))
                 (fn? receipt-cid-fn) (map? adapter)
                 (= #{:publish! :read-ref! :read-nonce-ref!} (set (keys adapter)))
                 (every? fn? (vals adapter)))
    (reject! :invalid-publication-options))
  (let [sink (causal/published-atomic-execution-receipt-sink
               database (merge {:expected-basis-cid expected-basis-cid
                                :receipt-cid-fn receipt-cid-fn} adapter))]
    (execute-registry-bound! options (assoc host :publish! sink) verification)))
