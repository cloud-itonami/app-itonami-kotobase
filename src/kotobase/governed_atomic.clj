(ns kotobase.governed-atomic
  "Hosted synchronous governed reads with request-bound atomic nonce publication.

  The host supplies an authenticated, stable scope and a publisher that atomically
  commits the exact prepared marker together with the durable receipt. This module
  does not implement that storage transaction, authority or distributed fencing."
  (:require [kotobase.governed-execution :as governed]
            [kotobase.nonce-store :as nonce]))

(defn- reject! [reason]
  (throw (ex-info "atomic governed execution refused"
                  {:kotobase.governed-atomic/reason reason})))

(defn execute-bound!
  "Run a synchronous governed read and pass its exact request-bound marker to
  PUBLISH! `(fn [receipt prepared-marker] durable-ack)`. The publisher is a
  trusted host component: it must verify marker semantics and atomically commit
  its absent ref with the receipt and all required blocks. SCOPE must be a stable
  authenticated replay domain. Preflight does not reserve a nonce; concurrent
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
