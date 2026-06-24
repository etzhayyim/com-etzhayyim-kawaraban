(ns kawaraban.methods.ingest
  "kawaraban 瓦版 — offline outlet/headline normalizer (G4 membrane, G8 --live gate)."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn IngestRefused
  "Returns an ex-info instance representing the Python IngestRefused exception."
  [msg]
  (ex-info msg {:kawaraban/error :kawaraban/ingest-refused
                :message msg}))

(defn ingest-refused? [e]
  (= :kawaraban/ingest-refused (-> e ex-data :kawaraban/error)))

(def FORBIDDEN_BODY_KEYS
  ["body" "fullText" "full_text" "content" "articleBody"])

(def FORBIDDEN_FIELDS
  {"verdict" "G1 (mirror-not-adjudicator)"
   "truthRating" "G1 (no fact-check score)"
   "personalizedFor" "G3 (no per-reader feed)"
   "readerId" "G3 (no reader surveillance)"})

(def OPEN_ACCESS
  #{"open" "registration-wall"})

(defn live-allowed
  "G8 — live fetch needs the operator gate. Always false at R0."
  []
  (= (System/getenv "KAWARABAN_ALLOW_LIVE_INGEST") "1"))

(defn normalize-record
  "Normalizes one raw public-facing page record into a :mirror datom.
   Raises (IngestRefused ...) for any gate breach."
  [rec]
  (let [oid (get rec "outlet" "?")]
    ;; G4 — no full body may be ingested.
    (doseq [k FORBIDDEN_BODY_KEYS]
      (when (get rec k)
        (throw (IngestRefused (str oid ": field '" k "' present — full body is unrepresentable (G4 link-out)")))))
    ;; G1 / G3 — no verdict / no reader.
    (doseq [[k gate] FORBIDDEN_FIELDS]
      (when (get rec k)
        (throw (IngestRefused (str oid ": field '" k "' present — violates " gate)))))
    ;; G4 — only public/open facing pages.
    (let [access (get rec "access" "open")]
      (when-not (OPEN_ACCESS access)
        (throw (IngestRefused (str oid ": access '" access "' is not public — paywall/terminal not mirrored (G4)")))))
    ;; G4/G5 — a canonical URL is required.
    (when-not (seq (str (get rec "url" "")))
      (throw (IngestRefused (str oid ": a :mirror article requires a canonical :url (G4/G5 link-out)"))))
    (let [excerpt (or (get rec "excerpt") "")
          excerpt-str (str excerpt)
          excerpt-280 (subs excerpt-str 0 (min (count excerpt-str) 280))
          truncated (> (count excerpt-str) 280)
          as-of (or (get rec "asOf") 0)]
      {":news.article/id" (or (get rec "id") (str "art." oid "." as-of))
       ":news.article/kind" ":mirror"
       ":news.article/section" (get rec "section" "sec.front")
       ":news.article/outlet" oid
       ":news.article/url" (get rec "url")
       ":news.article/headline" (or (get rec "headline") "")
       ":news.article/excerpt" excerpt-280
       ":news.article/lang" (get rec "lang" "en")
       ":news.article/as-of" (int as-of)
       ":news.article/sourcing" ":representative"
       "_excerpt_truncated" truncated})))

(defn normalize-batch
  "Returns [ok refused] — refused records are reported, never silently dropped (G5)."
  [records]
  (loop [records records
         ok []
         refused []]
    (if (empty? records)
      [ok refused]
      (let [rec (first records)
            result (try
                     {:ok (normalize-record rec)}
                     (catch clojure.lang.ExceptionInfo e
                       (if (ingest-refused? e)
                         {:refused (.getMessage e)}
                         (throw e))))]
        (if (contains? result :ok)
          (recur (rest records) (conj ok (:ok result)) refused)
          (recur (rest records) ok (conj refused (:refused result))))))))

(defn -main
  [& argv]
  (let [argv (or argv [])]
    (if (some #(= % "--live") argv)
      (if-not (live-allowed)
        (do (binding [*out* *err*]
              (println "REFUSED: live RSS/sitemap ingest is Council Lv6+ + operator gated (G8)."
                       "Set KAWARABAN_ALLOW_LIVE_INGEST=1 + Council attestation to enable."))
            2)
        (do (binding [*out* *err*]
              (println "REFUSED: R0 has no live fetcher wired (G8 design boundary)."))
            2))
      (let [args (filter #(not (str/starts-with? % "--")) (rest argv))
            batch (if (seq args)
                    (first args)
                    "20-actors/kawaraban/data/ingest/sample-batch.json")
            records (json/parse-stream (io/reader batch) false)]
        (let [[ok refused] (normalize-batch records)]
          (println (str "normalized " (count ok) " mirror article(s); refused " (count refused) " (gate violations)"))
          (doseq [r refused]
            (println (str "  REFUSED: " r))))
        0))))
