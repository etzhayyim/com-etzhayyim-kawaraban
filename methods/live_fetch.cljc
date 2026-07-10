(ns kawaraban.methods.live-fetch
  "live_fetch.cljc — kawaraban 瓦版 R1: live RSS/Atom outlet fetch (G8 gate).
  ADR-2607110200 (R0->R1). Companion to `ingest.cljc`: this namespace only fetches +
  parses a feed into the SAME string-keyed record shape `ingest/normalize-record` already
  validates, so every structural gate (G1 no-verdict, G3 no-reader, G4 no-full-body /
  open-access-only / 280-char excerpt bound) is INHERITED, not reimplemented here.

  Self-contained: a minimal, dependency-free RSS 2.0 / Atom 1.0 scanner (regex-based, not a
  full XML parser — sufficient for the flat <item>/<entry> shape real feeds use), matching
  the house style of `ingest.cljc`'s own minimal JSON reader (no clojure.data.xml / cheshire
  dependency).

  G8 — actually issuing the HTTP GET is gated behind `ingest/live-allowed` exactly like the
  offline CLI's `--live` flag. This namespace's pure parsing fns have no gate (parsing text
  you already have is not a live fetch); `fetch-outlet!` (the #?(:clj) HTTP edge) refuses
  unless the gate is open, mirroring `ingest/-main`'s existing refusal message."
  (:require [clojure.string :as str]
            [kawaraban.methods.ingest :as ingest]))

;; ── XML entity / CDATA handling ────────────────────────────────────────────

(defn unescape-xml
  "Decode the 5 predefined XML entities + numeric character references.
  Sufficient for RSS/Atom text nodes (no DTD-defined entities in practice)."
  [^String s]
  (if (nil? s)
    ""
    (-> s
        (str/replace #"&#x([0-9a-fA-F]+);" (fn [[_ hex]] (str (char (Integer/parseInt hex 16)))))
        (str/replace #"&#(\d+);" (fn [[_ dec]] (str (char (Integer/parseInt dec)))))
        (str/replace "&lt;" "<")
        (str/replace "&gt;" ">")
        (str/replace "&quot;" "\"")
        (str/replace "&apos;" "'")
        (str/replace "&amp;" "&"))))

(defn strip-cdata
  "Unwrap a `<![CDATA[ … ]]>`-wrapped text node, else pass through."
  [^String s]
  (if (nil? s)
    ""
    (let [s (str/trim s)]
      (if (and (str/starts-with? s "<![CDATA[") (str/ends-with? s "]]>"))
        (subs s 9 (- (count s) 3))
        s))))

(defn clean-text
  "strip-cdata then unescape-xml then trim — the standard text-node cleanup."
  [s]
  (-> s strip-cdata unescape-xml str/trim))

;; ── minimal block/tag scanner (regex-based, non-validating) ────────────────

(defn- blocks
  "All `<tag …>…</tag>` block bodies (non-greedy, DOTALL) for a given tag name."
  [^String xml tag]
  (mapv second (re-seq (re-pattern (str "(?s)<" tag "(?:\\s[^>]*)?>(.*?)</" tag ">")) xml)))

(defn- first-tag-text
  "First `<tag …>text</tag>` body inside `block`, cleaned. nil if absent."
  [^String block tag]
  (when-let [m (re-find (re-pattern (str "(?s)<" tag "(?:\\s[^>]*)?>(.*?)</" tag ">")) block)]
    (clean-text (second m))))

(defn- self-closing-attr
  "Value of `attr` on the first self-closing/opening `<tag … attr=\"v\" …>` in `block`."
  [^String block tag attr]
  (when-let [m (re-find (re-pattern (str "<" tag "\\s[^>]*" attr "=\"([^\"]*)\"")) block)]
    (unescape-xml (second m))))

(defn- atom-link-href
  "Atom `<link href=\"…\" rel=\"alternate\"?/>` — prefer rel=alternate, else the first link."
  [^String block]
  (or (when-let [m (re-find #"<link\s+[^>]*rel=\"alternate\"[^>]*href=\"([^\"]*)\"" block)]
        (unescape-xml (second m)))
      (when-let [m (re-find #"<link\s+[^>]*href=\"([^\"]*)\"[^>]*rel=\"alternate\"" block)]
        (unescape-xml (second m)))
      (self-closing-attr block "link" "href")))

;; ── RFC-822 / ISO-8601 pubDate → epoch seconds (best-effort, no timezone db) ─

(def ^:private months
  {"Jan" 1 "Feb" 2 "Mar" 3 "Apr" 4 "May" 5 "Jun" 6 "Jul" 7 "Aug" 8 "Sep" 9 "Oct" 10 "Nov" 11 "Dec" 12})

(defn- ldt->epoch
  "java.time.LocalDateTime (UTC-assumed) -> epoch seconds. bb/JVM only — java.time is
  available in babashka's sci, unlike java.util.GregorianCalendar."
  [y mo d hh mm ss]
  #?(:clj (.toEpochSecond (java.time.LocalDateTime/of (int y) (int mo) (int d) (int hh) (int mm) (int ss))
                          java.time.ZoneOffset/UTC)
     :cljs 0))

(defn parse-date->epoch
  "Best-effort epoch-seconds parse of an RFC-822 (`Mon, 02 Jan 2006 15:04:05 GMT`) or
  ISO-8601 (`2006-01-02T15:04:05Z`) date string. Returns 0 on anything unrecognized —
  never throws (a malformed date is not a G1/G3/G4 gate concern, `asOf` merely defaults)."
  [s]
  (or
   (when-let [[_ d mon y hh mm ss]
              (re-find #"(?:\w{3},\s*)?(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})" (or s ""))]
     (when-let [mo (get months mon)]
       (ldt->epoch (Integer/parseInt y) mo (Integer/parseInt d)
                   (Integer/parseInt hh) (Integer/parseInt mm) (Integer/parseInt ss))))
   (when-let [[_ y mo d hh mm ss]
              (re-find #"(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})" (or s ""))]
     (ldt->epoch (Integer/parseInt y) (Integer/parseInt mo) (Integer/parseInt d)
                 (Integer/parseInt hh) (Integer/parseInt mm) (Integer/parseInt ss)))
   0))

;; ── feed-item → ingest-record shape (the string-keyed map ingest/normalize-record wants) ─

(defn rss-item->record
  "One RSS 2.0 `<item>` block -> the ingest.cljc input-record shape."
  [outlet block]
  {"outlet"  (:id outlet)
   "section" (:section outlet "sec.front")
   "headline" (or (first-tag-text block "title") "")
   "excerpt"  (or (first-tag-text block "description") "")
   "url"      (or (first-tag-text block "link") "")
   "lang"     (:lang outlet "en")
   "asOf"     (parse-date->epoch (first-tag-text block "pubDate"))
   "access"   "open"})

(defn atom-entry->record
  "One Atom `<entry>` block -> the ingest.cljc input-record shape."
  [outlet block]
  {"outlet"  (:id outlet)
   "section" (:section outlet "sec.front")
   "headline" (or (first-tag-text block "title") "")
   "excerpt"  (or (first-tag-text block "summary") (first-tag-text block "content") "")
   "url"      (or (atom-link-href block) "")
   "lang"     (:lang outlet "en")
   "asOf"     (parse-date->epoch (or (first-tag-text block "published") (first-tag-text block "updated")))
   "access"   "open"})

(defn parse-feed
  "Parse RSS 2.0 or Atom 1.0 XML text into a vector of ingest-record maps for `outlet`
  (a map with :id/:section/:lang, see `data/outlets/allowlist.edn`). Auto-detects the feed
  kind by looking for `<rss` vs `<feed`. Returns [] for anything unrecognized (never throws
  — feed-format detection failure is not a charter-gate concern, it's an empty result)."
  [^String xml outlet]
  (cond
    (re-find #"(?i)<rss[\s>]" xml)
    (mapv #(rss-item->record outlet %) (blocks xml "item"))

    (re-find #"(?i)<feed[\s>]" xml)
    (mapv #(atom-entry->record outlet %) (blocks xml "entry"))

    :else []))

;; ── the G8 edge: actually fetching a URL is gated ───────────────────────────

#?(:clj
   (defn jvm-http-get
     "Default fetch-fn: JDK HttpClient GET (no dependency), 10s timeout."
     [^String url]
     (let [req (-> (java.net.http.HttpRequest/newBuilder (java.net.URI/create url))
                   (.timeout (java.time.Duration/ofSeconds 10))
                   (.GET)
                   (.build))
           resp (.send (java.net.http.HttpClient/newHttpClient) req
                       (java.net.http.HttpResponse$BodyHandlers/ofString))]
       (.body resp))))

(defn fetch-outlet!
  "G8 edge: fetch `outlet`'s :feed-url and normalize every item through the EXISTING
  ingest/normalize-batch (G1/G3/G4 gates inherited). Refuses (returns a refusal map, throws
  nothing) unless `ingest/live-allowed` — same operator-gate semantics as `ingest/-main
  --live`. `fetch-fn` is injectable (default `jvm-http-get`) so tests never touch the
  network."
  ([outlet] (fetch-outlet! outlet #?(:clj jvm-http-get :cljs (fn [_] ""))))
  ([outlet fetch-fn]
   (if-not (ingest/live-allowed)
     {:refused true
      :reason "live RSS/Atom fetch is Council Lv6+ + operator gated (G8, ADR-2607110200). Set KAWARABAN_ALLOW_LIVE_INGEST=1 + Council attestation to enable."}
     (let [xml (fetch-fn (:feed-url outlet))
           records (parse-feed xml outlet)
           [ok refused] (ingest/normalize-batch records)]
       {:refused false :outlet (:id outlet) :ok ok :gate-refused refused}))))

#?(:clj
   (defn load-allowlist
     "Read the outlet allowlist EDN (a vector of {:id :name :kind :access :feed-url
     :verified :section :lang …} maps). File I/O only at this edge."
     [path]
     (clojure.edn/read-string (slurp (str path)))))
