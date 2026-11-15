;; Hosted governed query with fixture authority; nonce/receipt publication shares one local root.
(require '[clojure.string :as str])
(import '[java.security MessageDigest] '[java.util HexFormat])
(def path (or (System/getenv "GIB_GOVERNED_PROBE") (throw (ex-info "governed probe required" {}))))
(def raw (java.nio.file.Files/readAllBytes (java.nio.file.Paths/get path (make-array String 0))))
(when-not (= "b396c68621caf109c500bdb307e216b082b70e170475f1b20f048975ae220896"
             (.formatHex (HexFormat/of) (.digest (MessageDigest/getInstance "SHA-256") raw)))
 (throw (ex-info "governed probe digest mismatch" {})))
(defn replace-one [s old new]
 (when-not (= 1 (count (re-seq (re-pattern (java.util.regex.Pattern/quote old)) s)))
  (throw (ex-info "replacement marker mismatch" {:marker old})))
 (str/replace s old new))
(def helper
 "(require '[otehon.ipld-forest :as catalog] '[otehon.forest :as forest] '[kotobase.nonce-store :as nonce] '[kotobase.governed-atomic :as atomic-governed])
(defn nonce-scope [] (codec/value-cid {:format :kotobase.nonce/request-scope-v1 :tenant \"test\" :principal \"fixture-principal\" :graph \"fixture-graph\" :epoch 7}))
(def fail-atomic? (atom true))
(defn atomic-publish-ports! [_ ref-name expected receipt prepared marker build verify]
 (let [prefix \"awai.ipld-forest-group/\" s (store/read-store (str out \"/store\")) dir (str out \"/store\")
       cids (vec (distinct (for [k (keys (:state s)) :when (str/starts-with? k prefix) a (:archives (catalog/recover-group dir (subs k (count prefix)))) c (if (get-in a [:forest :file-backed?]) (get-in a [:forest :cids]) (map :cid (:blocks (car/carv1-decode (:carv1 (forest/open (store/read-forest dir (get-in a [:ref :archive-sha]))))))))] (car/cid-str c))))
       result (atom nil)]
  (assert (not (:degraded? s))) (assert (seq cids))
  (when @fail-atomic? (throw (ex-info \"injected publication failure\" {:forced-publish true})))
  (let [adapter (fb/atomic-stream-receipt-publication-adapter (str receipt-out \"/store\") (str receipt-out \"/stage\") tool ref-name expected
     (fn [ports _ _]
      (doseq [cid cids] ((:put! ports) cid (or (data-get cid) (throw (ex-info \"missing basis block\" {})))))
      (let [x (build ports)] (reset! result (:value x)) (:target x)))
     (fn [get-block target _ _]
      (and (= (:marker marker) (codec/verify-value-cid-structurally-bounded (:marker-cid marker) (get-block (:marker-cid marker)) {:max-bytes 4096 :max-depth 8 :max-items 32}))
           (verify! get-block basis-cid) (verify get-block target))) marker)]
   ((:publish! adapter) {:receipt receipt :prepared prepared}) @result)))
")
(def source (String. raw "UTF-8"))
(def barrier-code "(def race-ready (java.util.concurrent.CountDownLatch. 2))")
(def prepared (-> source
 (replace-one "sink (fn [receipt]" "sink (fn [receipt marker]")
 (replace-one " :consume-nonce! (constantly true)" "")
 (replace-one ":nonce ref-name" ":nonce \"shared\"")
 (replace-one "(fb/publish-ports! db ref-name nil" "(atomic-publish-ports! db ref-name nil receipt prepared marker")
 (replace-one "(try (admit! request manifest)\n             (governed/execute!" "(try (atomic-governed/execute-bound!")
 (replace-one ":sign sig :commit! sink :authorize!" ":sign sig :authorize!")
 (replace-one ":evaluate! (fn [_ _] (swap! evals inc) (query))})"
  ":evaluate! (fn [_ _] (swap! evals inc) (.countDown race-ready) (when-not (.await race-ready 180 java.util.concurrent.TimeUnit/SECONDS) (throw (ex-info \"preflight barrier timed out\" {}))) (query))} {:backend db :scope (nonce-scope) :publish! sink :admit-basis! (fn [{:keys [request manifest]}] (admit! request manifest) true)})")
 (replace-one "(case (first *command-line-args*)" (str helper barrier-code "\n(case (first *command-line-args*)"))))
(def start (str/index-of prepared "        allow (execute (decision resource) \"allow\")"))
(def end (when start (str/index-of prepared "\n \"read\"" start)))
(when-not (and start end) (throw (ex-info "race fixture tail not found" {})))
(def race-tail
 "        _ (reset! fail-atomic? false)
        a (future (execute (decision resource) \"race-a\"))
        b (future (execute (decision resource) \"race-b\"))
        results [@a @b]
        winners (filterv #(= [{:file/logical-cid file-cid}] (:rows %)) results)
        losers (filterv #(contains? % :error) results)
        before (store/read-store (str receipt-out \"/store\"))
        refs (mapv #(storage/-read-ref db %) [\"race-a\" \"race-b\"])
        marker (nonce/prepare-marker (nonce-scope) \"shared\")
        nonce-ref (storage/-read-ref db (:ref-name marker))
        replay (execute (decision resource) \"race-replay\")
        after (store/read-store (str receipt-out \"/store\"))]
   (assert (= 2 @evals))
   (assert (= 1 (count winners))) (assert (= 1 (count losers)))
   (assert (nil? (:rows (first losers))))
   (assert (contains? #{:writer-busy :stale-generation :nonce-replayed} (get-in (first losers) [:error :type])))
   (assert (= 1 (count (filter some? refs))))
   (assert (= (:marker-cid marker) (:cid nonce-ref)))
   (assert (= :nonce-replayed (get-in replay [:error :kotobase.authority-window/reason])))
   (assert (= before after))
   (let [log (edn/read-string (slurp (str receipt-out \"/store/\" (get-in before [:root :log]) \".edn\")))
         frames (get-in log [:value :frames])
         puts (mapv first (get-in (first frames) [:payload :puts]))]
     (assert (= 1 (count frames))) (assert (= 3 (count puts)))
     (assert (some #(= % (str \"awai.kotobase-ref/\" (:ref-name marker))) puts))
     (assert (some #(str/starts-with? % \"awai.ipld-forest-group/\") puts))
     (assert (= 1 (count (filter #{\"awai.kotobase-ref/race-a\" \"awai.kotobase-ref/race-b\"} puts)))))
   (prn {:evaluations 2 :winners 1 :losers 1 :loser-reason (get-in (first losers) [:error :type]) :nonce-replay :refused :root-unchanged true :frame-puts 3 :pass true})))")
(when-not (= "write" (first *command-line-args*)) (throw (ex-info "write required" {})))
(load-string (str (subs prepared 0 start) race-tail (subs prepared end)))
