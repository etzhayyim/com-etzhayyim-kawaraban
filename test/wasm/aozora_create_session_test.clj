(ns wasm.aozora-create-session-test
  "Hosts wasm/aozora_create_session.wasm via kototama.tender -- proves the
  confined slice of kawaraban.aozora/mint-session!'s HTTP half (build the
  com.atproto.server.createSession JSON body via json-encode, then http-post
  it) runs as a real WASM guest, and that kototama's SSRF denylist actually
  fires against a compiler-emitted (not hand-written WAT) guest -- the same
  proof shape kototama's own kotoba-compiled-http-fetch.wasm fixture
  established for http-fetch (ADR-2607230943 second wave).

  This session makes NO real internet calls (task constraint): the target
  URL is loopback on purpose, so http-post is GUARANTEED to return -1."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kototama.contract :as contract]
            [kototama.tender :as tender]))

(defn- wasm-bytes []
  (.readAllBytes (io/input-stream (io/file "wasm/aozora_create_session.wasm"))))

(def ^:private caps
  (contract/host-caps {:grants [:http-post :json-encode]
                        :limits {:max-http-posts 1}}))

(defn- run-create-session [cacao-text]
  (let [instance (tender/instantiate (wasm-bytes) [:http-post :json-encode] caps)
        memory (.memory instance)
        cacao-bytes (.getBytes ^String cacao-text "UTF-8")]
    (.writeI32 memory 0 (count cacao-bytes))
    (.write memory 8 cacao-bytes 0 (count cacao-bytes))
    (let [written (tender/call-main instance)]
      ;; alloc order in this module: pairs-ptr@2048 (2048B) ->
      ;; body-ptr@4096 (2200B) -> resp-ptr@6296 (512B). See
      ;; wasm/README.md "Output buffer offset".
      {:written written
       :json-body (tender/read-memory-string
                   instance 4096
                   (count (str "{\"cacao\":\"" cacao-text "\"}")))})))

(deftest aozora-create-session-loopback-is-refused
  (testing "http-post's unconditional SSRF denylist blocks the loopback
            destination -- real compiler+tender LINKAGE + real guard
            EXECUTION, not a live network round trip (no internet access in
            this session, matching Phase A's http-fetch fixture proof)"
    (let [{:keys [written]} (run-create-session "deadbeef-demo-cacao-blob")]
      (is (= -1 written)))))

(deftest aozora-create-session-body-shape-matches-xrpc-envelope
  (testing "the JSON body json-encode built is exactly
            com.atproto.server.createSession's real flat envelope shape
            (kawaraban.aozora/mint-session!'s (json-write {:cacao cacao}))"
    (let [{:keys [json-body]} (run-create-session "hello-cacao-token-text")]
      (is (= "{\"cacao\":\"hello-cacao-token-text\"}" json-body)))))

(deftest aozora-create-session-carries-arbitrary-host-provided-cacao-text
  (testing "the cacao text is genuinely dynamic (host-poked memory), not a
            compile-time literal -- proves per-run parameterization works
            under kototama's 0-arity main convention"
    (let [{:keys [json-body]} (run-create-session "another-different-token")]
      (is (= "{\"cacao\":\"another-different-token\"}" json-body)))))
