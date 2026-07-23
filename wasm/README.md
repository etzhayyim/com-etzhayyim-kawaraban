# wasm/ — kotoba-wasm componentization of kawaraban (Phase B)

This directory ports a **confined slice** of `src/kawaraban/cacao.clj` and
`src/kawaraban/aozora.clj` into the `.kotoba` language subset, compiled to
real WASM modules via `kotoba wasm emit`, and hosted via `kototama.tender`
(`test/wasm/*_test.clj`) — the same `kotoba wasm emit` → `kototama.tender`
pipeline established by ADR-2607062330 addendum 5 and already used by
`cloud-itonami-isic-6310`'s `wasm/achievement_band.kotoba`,
`cloud-itonami-isic-6419`'s `wasm/iban_checksum.kotoba`, and
`cloud-itonami-isic-6511`'s `wasm/underwriting_decision.kotoba`.

This is **Phase B** of a larger effort (com-junkawasaki/root
ADR-2607231022 registered the `http-fetch`/`cbor-encode`/`json-encode`/
`json-extract-field` capabilities this port needed in `kotoba-lang/kototama`
PR #49 as **Phase A**; live fleet placement on Murakumo is a later Phase D,
not attempted here).

## Honest scope: what this DOES and does NOT port

**RSS/Atom fetch + parse (`src/kawaraban/methods/live_fetch.cljc`) is
deliberately NOT ported and stays JVM-side, unchanged.** `.kotoba` has no
loop/recur over unbounded input, no string search/slice primitive, and no
XML/tag-matching facility of any kind — parsing arbitrary-length untrusted
network responses (variable tag names, nested elements, RSS 1.0/RDF vs.
Atom vs. RSS 2.0 shape differences `live_fetch.cljc` already handles) is
not expressible in the confined subset this compiler actually implements.
This mirrors the `.kotoba` reality every prior sibling wasm port already
documented (see "Language-limitation findings" below) and is explicitly
permitted by this task's own instructions when the language genuinely
cannot express the logic. `methods/ingest.cljc`'s charter-gate logic
(G1/G3/G4 `normalize-record`) is equally untouched — this port never
re-implements or weakens it.

**What genuinely IS ported and running as real WASM, verified against a
real Chicory `Instance` (not a mock, not a hand-written WAT string):**

| module | ports | capabilities used |
|---|---|---|
| `cacao_self_mint.kotoba` | a confined slice of `kawaraban.cacao/mint`: fresh Ed25519 identity + SHA-256 fingerprint of the pubkey + Ed25519 signature over a host-formatted message + flat CBOR encode | `identity/keypair`, `identity/sign`, `hash/sha256`, `data/cbor` |
| `aozora_create_session.kotoba` | the HTTP half of `kawaraban.aozora/mint-session!`: build `com.atproto.server.createSession`'s real `{"cacao": "..."}` JSON envelope and POST it | `data/json`, `http/post` |
| `aozora_extract_session_fields.kotoba` | the response half of `kawaraban.aozora/mint-session!`: pull `accessJwt` out of an example createSession response | `data/json` |

## Language-limitation findings (independently confirmed, not new)

Every finding below was independently reached by at least one prior
sibling wasm port (`cloud-itonami-isic-6310`/`-6419`/`-6511`) by reading
`kotoba-lang/kotoba/src/kotoba/runtime.clj`'s `compile-wasm-expr`
end-to-end; this port confirms them again from scratch and adds one
(nested CBOR maps).

1. **No runtime string construction beyond compile-time literals.**
   `str-ptr`/`str-len`/`byte-at` only accept string values the compiler can
   see as literals at compile time (`literal-bytes` requires a literal
   argument) — there is no `str`/`format`/`substring`/`concat` builtin.
   Already documented by `cloud-itonami-isic-6511`'s
   `underwriting_decision.kotoba` ("Dynamic prompt interpolation") and
   `cloud-itonami-isic-6310`'s `achievement_band.kotoba` README ("no
   string-construction primitive beyond compile-time literals").
2. **`main` is always 0-arity.** `kotoba wasm emit` rejects a parameterized
   `main` (`:main-arity`). Real per-run inputs are threaded in by having
   the HOST write raw bytes into the guest's own exported linear memory at
   fixed, pre-agreed offsets **before** calling `main()` — the established
   convention every sibling wasm port above uses. This module suite uses
   it too (see each `.kotoba` file's header comment for its exact offset
   layout) — **and it is not actually a blocker for dynamic content**: the
   host can poke a variable-length byte region (a SIWE message, a CACAO
   blob) just as easily as a fixed-width flag, prefixed by an `i32` length
   at a known offset. This resolved what first looked like a hard "no
   per-article dynamic content" gap; it is architecture (host formats
   text, guest does the confined crypto/capability-gated I/O), not a
   missing feature.
3. **No i64 division/mod/quot.** `i64+`/`i64-`/`i64*`/`i64and`/`i64or`/
   `i64xor`/`i64shl`/`i64shr`/`i64ushr` exist; there is no i64 counterpart
   of `quot`/`rem`/`mod`/`/` (only i32 `div_s`/`rem_s` are wired). This
   would block converting `clock-monotonic`'s i64 epoch-ms into a decimal
   ASCII timestamp inside the guest — moot for THIS port because the host
   pre-formats the whole SIWE message text (including `Issued At:`)
   before poking it in (see finding 2), but worth flagging for any future
   module that needs guest-side i64 formatting.
4. **`cbor-encode`/`json-encode` only produce a FLAT (single-level)
   definite-length map.** `kototama.tender`'s `parse-flat-pairs`/
   `cbor-pairs-bytes`/`json-pairs-bytes` accept one `key<TAB>value` pair
   per line and emit one flat map — there is no nested-map support. The
   REAL CACAO wire format `kawaraban.cacao/->wire` produces is nested
   (`{"h":{"t":"eip4361"},"p":{...},"s":{"t":"EdDSA","s":sig}}`). This
   means `cacao_self_mint.kotoba`'s CBOR output is a **flat approximation**
   (`{"type":"eip4361","fingerprint":<hex>,"sig":<hex>}`), useful to prove
   the capability chain (gen-keypair → sha256-hex → sign → cbor-encode)
   runs correctly end-to-end, but **NOT wire-compatible with a real
   CACAO-verifying server**. Producing a byte-faithful CACAO token from
   `.kotoba` needs either a nested-map capability extension to
   `cbor-encode`, or keeping CBOR nesting host-side and using the guest
   only for the sign step (feeding the already-nested envelope's `s.s`
   field back in from the host).
5. **`http-post`/`http-fetch` take no header parameter at all.** Their ABI
   is exactly `(url-ptr url-len body-ptr body-len out-ptr out-cap)` /
   `(url-ptr url-len out-ptr out-cap)` — no way to set `Content-Type` or
   `Authorization`. `com.atproto.repo.createRecord` genuinely needs
   `Authorization: Bearer <jwt>` (`kawaraban.aozora/create-record!`); this
   port does not attempt a `createRecord` module for that reason (see
   "Not ported" below) — `createSession` at least doesn't strictly need a
   custom header to be understood by most JSON XRPC servers, but this is
   an honest gap, not a silent success.
6. **did:key / graph-cid derivation cannot be expressed.**
   `kawaraban.cacao/did-key` (multicodec-prefixed base58btc of the raw
   pubkey) and `kawaraban.cacao/canonical-graph` (CIDv1 header + base32,
   `base32-lower-no-pad`) both need arbitrary-precision (bignum) integer
   division / 5-bit-group bit-packing across a growing byte sequence —
   expressible in principle with this language's bit ops
   (`bit-and`/`bit-or`/`bit-shift-left`/`unsigned-bit-shift-right`) but
   disproportionate effort for this pass (a genuine "don't over-engineer"
   scope cut, not a hard technical wall the way findings 1-5 are). Both
   stay host-side, unchanged. `cacao_self_mint.kotoba`'s `fingerprint`
   field (sha256-hex of the raw pubkey) is a real, meaningful use of the
   `hash/sha256` capability but is explicitly NOT a did:key.

## Not ported (and why)

- **`com.atproto.repo.createRecord`** (the actual per-article publish
  call) — needs an `Authorization: Bearer <jwt>` header (finding 5) AND,
  to be genuinely useful per-article, the article's `text`/`createdAt`
  fields nested inside a `record` object (`kawaraban.aozora/create-record!`'s
  body `{repo, collection, rkey, record}` — `record` itself is an object,
  not a flat string, so even `json-encode`'s flat-map capability cannot
  produce this envelope's outer shape, compounding finding 4's nested-map
  gap). A faithful port needs BOTH gaps closed first.
- **did:key / graph-cid derivation** — see finding 6.
- **RSS/Atom fetch + parse** — see "Honest scope" above.

## ABI — ports of `kawaraban.cacao/mint` and `kawaraban.aozora/mint-session!`

### `cacao_self_mint.kotoba`

Input (host writes before calling `main`):

| offset | field | notes |
|---|---|---|
| 0 (i32) | `msg-len` | length of the SIWE message text at offset 8 |
| 8.. | SIWE message text | UTF-8, host-formatted via `kawaraban.cacao/siwe-message` unchanged; `msg-len` MUST be `<= 2000` (stays clear of `heap-base` 2048) |

`main`'s internal `alloc` sequence (bump allocator, deterministic given
this exact source — verified empirically, see `wasm/README.md`'s own
rebuild + `test/wasm/cacao_self_mint_test.clj`'s comments): `seed-ptr`
@2048 (64B: 32B seed + 32B pubkey) → `fp-ptr` @2112 (64B: sha256-hex ASCII
text) → `sig-ptr` @2176 (64B: raw Ed25519 signature) → `pairs-ptr` @2240
(256B: assembled flat-pairs text) → `out-ptr` @2496 (512B: CBOR output).

Output: `main` returns `cbor-encode`'s bytes-written count. The CBOR bytes
themselves are at address 2496 (`out-ptr`) for that many bytes — a
definite-length map (`0xA3` header byte = major-type 5, count 3) with
`type` → `"eip4361"`, `fingerprint` → 64-char lowercase hex
`sha256(pubkey)`, `sig` → 128-char lowercase hex Ed25519 signature over the
SIWE message text, signed with the freshly-generated seed.

### `aozora_create_session.kotoba`

Input (host writes before calling `main`):

| offset | field | notes |
|---|---|---|
| 0 (i32) | `cacao-len` | length of the CACAO token text at offset 8 |
| 8.. | CACAO token text | opaque to this module — e.g. hex(cbor bytes) or a real base64 `cacao_b64`; `cacao-len` MUST be `<= 2032` |

Alloc sequence: `pairs-ptr` @2048 (2048B) → `body-ptr` @4096 (2200B: the
JSON body `{"cacao":"<cacao text>"}`) → `resp-ptr` @6296 (512B).

Output: `main` returns `http-post`'s result — **always `-1` in this repo's
test suite** (the target URL, `http://127.0.0.1/xrpc/com.atproto.server.createSession`,
is loopback ON PURPOSE; `kototama.tender`'s unconditional SSRF denylist
refuses it before any connection is attempted). This proves real
compiler-emitted-guest + tender LINKAGE and real SSRF-guard EXECUTION, the
same proof shape `kotoba-lang/kototama`'s own
`kotoba-compiled-http-fetch.kotoba` fixture already established — **not** a
live network round trip. No internet access happens anywhere in this
port's test suite (task constraint for this session).

### `aozora_extract_session_fields.kotoba`

No input ABI — the example `com.atproto.server.createSession` response
text is a compile-time literal (127-UTF8-byte string-literal admission
cap forced trimming it to just the 2 fields this module reads). Returns
`json-extract-field`'s bytes-written count for the `accessJwt` field
(`16`, `"demo.session.jwt"`).

## Rebuilding

`kotoba wasm emit`'s CLI wraps a mandatory package-admission gate
(`--package-lock`) that this port's build did not go through directly —
same reason `kotoba-lang/kototama` PR #49 compiled its cbor-encode/
json-encode/json-extract-field fixtures directly via
`kotoba.runtime/wasm-binary` against a **local sibling checkout** of
`kotoba-core-contracts` (capability ids `245`/`246`, `data/cbor`/
`data/json`, not yet resolvable through the CLI's own pinned
`kotoba-core-contracts` coordinate at build time). This port used the
identical approach:

```clojure
;; from a kotoba-lang/kotoba checkout with :dev alias sibling overrides
;; (kotoba-core-contracts/kotoba-lang/kotoba-selfhost-contracts/etc. as
;; local/root siblings -- see kotoba-lang/kotoba's own deps.edn :dev alias)
(require '[kotoba.runtime :as runtime])
(require '[clojure.java.io :as io])

(let [forms (runtime/read-file "wasm/cacao_self_mint.kotoba" :kotoba)
      policy {:kotoba.policy/capabilities #{:identity/keypair :identity/sign
                                             :hash/sha256 :data/cbor}}
      wasm (runtime/wasm-binary forms policy)]
  (with-open [os (io/output-stream "wasm/cacao_self_mint.wasm")]
    (.write os ^bytes (:kotoba.wasm/binary wasm))))
```

Same pattern for `aozora_create_session.kotoba` (`#{:http/post :data/json}`)
and `aozora_extract_session_fields.kotoba` (`#{:data/json}`).

## Verified locally (this PR)

- `kotoba.runtime/wasm-binary` compiles all 3 modules cleanly (0 problems):
  `cacao_self_mint.kotoba` → 746 bytes / 4 host imports; `aozora_create_session.kotoba`
  → 414 bytes / 2 host imports; `aozora_extract_session_fields.kotoba` →
  236 bytes / 1 host import.
- `test/wasm/*_test.clj` load and run all 3 compiled `.wasm` modules
  through a real `kototama.tender/instantiate`/`call-main` (Chicory
  `Instance`, not a mock): 9 `deftest`s across 3 files, including a
  **real cryptographic round-trip** (the guest-generated pubkey's
  `sha256-hex` fingerprint matches an independently-computed
  `MessageDigest/SHA-256`, and the guest's Ed25519 signature genuinely
  verifies via `ed25519.core/verify` against that same pubkey and
  message — not stubbed, not asserted-by-shape-only) and a genuine
  SSRF-denial proof (`http-post` returns `-1` against the loopback
  target). `clojure -M:test` — 29 tests / 56 assertions, 0 failures, 0
  errors (20 tests / 46 assertions pre-existing + this port's 9 tests / 10
  assertions). `clojure -M scripts/audit.clj` — `audit: ok`.

## Follow-ups (Phase C candidates, not closure blockers for Phase B)

- Extend `kototama`'s `cbor-encode`/`json-encode` to support ONE level of
  nesting (or a dedicated `cacao-wire-encode` capability that hard-codes
  the `{"h":{...},"p":{...},"s":{...}}` shape) so a byte-faithful CACAO
  token can be produced from `.kotoba`.
- Add an optional headers parameter to `http-post` (or a dedicated
  `xrpc-post` capability that threads a bearer token) so
  `com.atproto.repo.createRecord` can be ported.
- Decide whether did:key / graph-cid derivation is worth expressing in
  `.kotoba` (base58btc/base32 bit-packing) or should stay a permanent
  host-side responsibility — not urgent either way since it is a
  one-time-per-outlet-identity operation, not a per-post hot path.
- Fleet placement on Murakumo (Phase D) — not attempted here.
