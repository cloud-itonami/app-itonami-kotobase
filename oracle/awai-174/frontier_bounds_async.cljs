(ns frontier-bounds-async
  (:require [kotobase.frontier-store :as frontier]
            [kotobase.storage.core :as storage]
            [kotobase.storage.memory :as memory]))

(defn ensure! [condition message]
  (when-not condition (throw (js/Error. message))))
(defn check-refusal [bytes limit]
  (let [events (atom []) delegate (memory/memory-store)
        backend (reify
                  storage/IBlockStore
                  (-put-blocks! [_ _] (swap! events conj :put) (js/Promise.resolve []))
                  (-get-blocks [_ cids]
                    (swap! events conj :read-block)
                    (js/Promise.resolve {(first cids) bytes}))
                  storage/IRefStore
                  (-read-ref [_ _] (swap! events conj :read-ref)
                    (js/Promise.resolve {:cid "untrusted-cid"}))
                  (-compare-and-set-ref! [_ _ _ _]
                    (swap! events conj :cas) (js/Promise.resolve {}))
                  storage/IBackendCapabilities
                  (-capabilities [_] (storage/-capabilities delegate)))
        result (frontier/accept-and-store!
                backend "frontier" "db"
                {:candidate-root "candidate" :path []
                 :verify-entry (fn [_] (swap! events conj :verify) true)})]
    (.then result
           (fn [_] (throw (js/Error. "invalid frontier accepted")))
           (fn [error]
             (ensure! (= :cbor/resource-limit (:type (ex-data error))) "wrong rejection type")
             (ensure! (= limit (:limit (ex-data error))) "wrong limit")
             (ensure! (= [:read-ref :read-block] @events) "verification or write occurred")))))
(defn -main []
  (let [deep (js/Uint8Array. (clj->js (conj (vec (repeat 20 0x81)) 0)))
        oversized (js/Uint8Array. 16385)]
    (-> (check-refusal deep :max-depth)
        (.then #(check-refusal oversized :max-bytes))
        (.then #(println "CLJS frontier bounds: depth and bytes rejected before verify/write"))
        (.catch (fn [error] (.error js/console error) (set! (.-exitCode js/process) 1))))))
(set! *main-cli-fn* -main)
