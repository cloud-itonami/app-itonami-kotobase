;; Hosted control against an existing separately published fixture; no writer is used.
(require '[clojure.string :as str] '[kotobase.governed-atomic :as atomic]
         '[kotobase.nonce-store :as nonce])
(import '[java.security MessageDigest] '[java.util HexFormat])
(def path (or (System/getenv "GIB_GOVERNED_PROBE") (throw (ex-info "probe required" {}))))
(def raw (java.nio.file.Files/readAllBytes (java.nio.file.Paths/get path (make-array String 0))))
(assert (= "b396c68621caf109c500bdb307e216b082b70e170475f1b20f048975ae220896"
           (.formatHex (HexFormat/of) (.digest (MessageDigest/getInstance "SHA-256") raw))))
(def original (first (str/split (String. raw "UTF-8") #"\(case \(first \*command-line-args\*\)" 2)))
(def old "(def basis-cid (:cid (storage/-read-ref (refs/open-ref-store (str out \"/store\")) \"gib-prolly\")))")
(def new "(def basis-cid (:basis (edn/read-string (slurp (str receipt-out \"/ids.edn\")))))")
(assert (= 1 (count (re-seq (re-pattern (java.util.regex.Pattern/quote old)) original))))
(load-string (str/replace original old new))
(let [db (rbackend) ids (edn/read-string (slurp (str receipt-out "/ids.edn")))
      get-block #(storage/get-block db %)
      commit (:cid (storage/-read-ref db "allow")) receipt-id (:allow ids)
      receipt (first (:receipt/records (causal/receipt-at (open-db db) commit receipt-id)))
      request (value-at get-block (:request/digest receipt))
      marker (nonce/prepare-marker (atomic/request-scope request) (:nonce request))
      before (store/read-store (str receipt-out "/store"))
      sink (causal/published-atomic-execution-receipt-sink
             (open-db db) {:expected-basis-cid nil :receipt-cid-fn id/value-cid
                           :publish! (fn [_] commit) :read-ref! (fn [] (:cid (storage/-read-ref db "allow")))
                           :read-nonce-ref! (fn [_] nil)})]
 (assert (try (sink receipt marker) false
              (catch Exception e (= :published-nonce-ref-mismatch (:kotobase.causal-commit/reason (ex-data e))))))
 (assert (= before (store/read-store (str receipt-out "/store"))))
 (prn {:valid-receipt-readback :verified :missing-nonce-ref :refused :root-unchanged true :pass true}))
(shutdown-agents)
