(ns kawaraban.methods.test-ingest
  "kawaraban — tests for the offline outlet normalizer membrane (ingest.clj). 1:1 port of test_ingest.py."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [kawaraban.methods.ingest :as ingest]))

(defn- sample-batch-path []
  (or (some-> (io/resource "kawaraban/data/ingest/sample-batch.json") str)
      "20-actors/kawaraban/data/ingest/sample-batch.json"))

(defn- load-records []
  (json/parse-stream (io/reader (sample-batch-path)) false))

(defn- refused? [thunk]
  (try
    (thunk)
    false
    (catch clojure.lang.ExceptionInfo e
      (= :kawaraban/ingest-refused (-> e ex-data :kawaraban/error)))))

(defn- refusal-message [thunk]
  (try
    (thunk)
    nil
    (catch clojure.lang.ExceptionInfo e
      (.getMessage e))))

(deftest test-batch-accepts-clean-refuses-violations
  (let [[ok refused] (ingest/normalize-batch (load-records))]
    (is (= 2 (count ok)) ok)
    (is (= 3 (count refused)) refused)
    (let [blob (str/join " " refused)]
      (is (str/includes? blob "G4") blob)
      (is (str/includes? blob "G1") blob))))

(deftest test-refuses-full-body
  (is (refused? #(ingest/normalize-record {"outlet" "o" "url" "u" "body" "the whole thing"})))
  (is (str/includes? (or (refusal-message #(ingest/normalize-record {"outlet" "o" "url" "u" "body" "the whole thing"})) "") "G4")))

(deftest test-refuses-paywall
  (is (refused? #(ingest/normalize-record {"outlet" "o" "url" "u" "access" "paywall"})))
  (is (str/includes? (or (refusal-message #(ingest/normalize-record {"outlet" "o" "url" "u" "access" "paywall"})) "") "G4")))

(deftest test-refuses-verdict
  (is (refused? #(ingest/normalize-record {"outlet" "o" "url" "u" "verdict" true})))
  (is (str/includes? (or (refusal-message #(ingest/normalize-record {"outlet" "o" "url" "u" "verdict" true})) "") "G1")))

(deftest test-requires-url
  (is (refused? #(ingest/normalize-record {"outlet" "o" "headline" "h"})))
  (is (str/includes? (str/lower-case (or (refusal-message #(ingest/normalize-record {"outlet" "o" "headline" "h"})) "")) "url")))

(deftest test-excerpt-truncated-to-280
  (let [rec (ingest/normalize-record {"outlet" "o" "url" "u" "excerpt" (apply str (repeat 500 "x"))})]
    (is (= 280 (count (get rec ":news.article/excerpt"))))
    (is (true? (get rec "_excerpt_truncated")))))

(deftest test-normalized-is-mirror-kind
  (let [rec (ingest/normalize-record {"outlet" "outlet.nhk" "url" "https://x" "headline" "h" "asOf" 5})]
    (is (= ":mirror" (get rec ":news.article/kind")))
    (is (= ":representative" (get rec ":news.article/sourcing")))))

(deftest test-live-refused-at-r0
  (is (false? (ingest/live-allowed))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (clojure.test/run-tests (quote kawaraban.methods.test-ingest))]
    (System/exit (if (zero? (+ fail error)) 0 1))))
