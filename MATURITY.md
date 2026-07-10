# kawaraban 瓦版 — Maturity

**Stage: R1, live** (live-ingest + aozora-publish activated by founder/operator 2026-07-10)
— ADR-2606061900, advanced by ADR-2607110200. News MEDIUM: mirror of the world's real news
media (headline + link-out + ≤280-char fair-use excerpt) + the actor-to-actor wire. The
charter-clean inverse of a news app. Central ingest actor of the ADR-2606161536 pipeline
(`utsushie-loop`).

## Live since 2026-07-10

`KAWARABAN_ALLOW_LIVE_INGEST=1` was set and kawaraban's own Ed25519 identity was minted
(`.kawaraban/identity.edn`, gitignored, did:key `z6MkeVjRzbvL4PHiEJi2SWUUdqt3cwUfY85iyntM5KThAySF`)
by the founder. First real run: 9 outlets fetched (BBC, DW, France 24, RTHK, ABC Australia,
NPR, PBS NewsHour, Al Jazeera, UN News), 25 real `:mirror` articles published to
`https://pds.aozora.app` under `com.etzhayyim.apps.kawaraban` (capped at 3/outlet for the
first activation — no flood). NHK World-Japan's guessed feed URL was wrong (returns HTML,
not XML) and CBC timed out from this environment — both left `:verified false` with a note
in `data/outlets/allowlist.edn` rather than guessed further. AP has no resolved feed URL at
all (`:feed-url nil`, unresolved by design).

## Per-organization mirror actors (owner instruction 2026-07-10, same day)

Same-day follow-up: the founder asked for news to be posted "per actor" — split by
organization rather than pooled under kawaraban's one root identity, keeping a clear
separation from any future "media"/writer actor that would analyze/interpret/translate this
primary-source content (that layer is a deliberate, not-yet-built follow-up, tracked
separately — see `docs/adr/` cross-reference in ADR-2607110200's addendum).

`src/kawaraban/mirror_actor.clj` (new) mints a DISTINCT self-sovereign identity per outlet
(`.kawaraban/mirrors/<outlet-id>.edn`, gitignored) and writes an `app.bsky.actor.profile`
to each one's aozora.app page BEFORE it ever publishes an article — the profile always
discloses, up front: "🪞 Automated, UNOFFICIAL mirror of \<outlet\> ... not affiliated with
or operated by \<outlet\>". This is the G9 (mirror-not-impersonation) guarantee made
structural: no per-outlet identity can be mistaken for that outlet's own official account,
because the disclosure is the first thing anyone sees on its profile. Every article gate
(G1/G3/G4) is unchanged — only WHICH key signs the post changes.

`src/kawaraban/aozora.clj` was refactored to expose `mint-session!`/`create-record!` as
reusable building blocks (previously inlined in one `publish!`), so `set-profile!` and
per-outlet publishing share the exact same session-mint path instead of duplicating it.

Second real run: all 9 verified outlets now each have their own did:key + disclosed
profile, 27 more `:mirror` articles published (3/outlet again) under their OWN identity
instead of the shared root one. The original 25 articles under the root kawaraban identity
were left as-is (no deletion) — they remain a legitimate aggregate view; new fetches route
through the per-outlet identities going forward.

Fixes landed while going live (found by actually running against real outlets, not
hypothetical): `jvm-http-get` now follows redirects + sends an honest identifying
User-Agent (BBC's feed 302-redirects http->https and was silently returning nothing);
`parse-feed`/`rss-item->record` now also handle RSS 1.0/RDF (`<rdf:RDF>` root, `dc:date`
instead of `pubDate` — Deutsche Welle's format); `fetch-outlet!` now catches a per-outlet
network failure instead of letting it abort the whole batch.

## R0 → R1 (ADR-2607110200)

- `methods/live_fetch.cljc` — RSS/Atom fetch + parse, feeds through the EXISTING
  `ingest/normalize-record`/`normalize-batch` gates (no new gate invented). 12 tests, all
  against local fixtures — no live network calls in this repo's test suite.
- `data/outlets/allowlist.edn` — 12 state/public-broadcaster + non-profit press outlets
  (NHK World-Japan, BBC, DW, France 24, RTHK, CBC, ABC Australia, NPR, PBS NewsHour, AP*, Al
  Jazeera, UN News). Each `:verified true/false` — unverified feed URLs are best-effort and
  MUST be confirmed before enabling live ingest for them (G5).
- `src/kawaraban/cacao.clj` + `src/kawaraban/aozora.clj` + `src/kawaraban/publisher.cljc` +
  `src/kawaraban/publish.cljc` — real app-aozora publish path (1:1 port of tashikame's proven
  `cacao`/`aozora`/`publisher` shape), collection `com.etzhayyim.apps.kawaraban` and PDS
  `https://pds.aozora.app` matching the RAD identity journal's pre-recorded tx-4 fields.
  MockPublisher stays the default; `aozora-publisher` is opt-in.
- `KAWARABAN_ALLOW_LIVE_INGEST` is NOT hardcoded or committed anywhere in this repo — it
  stays an operator-set environment variable (unset by default in any fresh checkout;
  refused unless explicitly set for a given run). ADR-2607110200 is the Council-ADR
  ratification the cells' own `solve()` comments ask for; the founder/operator then
  separately, explicitly set it and ran a real ingest (see "Live since 2026-07-10" above).
- All existing R0 tests unchanged and still green; `run_tests.sh` fixed to `cd` to its own
  directory (was cd-ing two levels too far up) and now also runs `test-live-fetch`.

| Dimension | State |
|---|---|
| Lexicons | ✅ 6 under `com.etzhayyim.kawaraban.*` (article/issue/outlet/section/mentionEdge/kawarabanReview) — the 11 gates `const`/`enum`-pinned (local `.edn` + central `.json`) |
| Cells | ✅ 5 Pregel cells (`.solve()` RuntimeError at R0; live ingest G8-gated) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G11) machine-readable |
| Tests | ✅ **54 green** — `methods/test_charter_gates.cljc` (**8**, added 2026-06-17) + route (11) + analyze (7) + ingest (8) + cells (20); `./run_tests.sh` aggregates all |
| Methods | ✅ route / analyze / ingest (offline); live RSS/firehose ingest = G8 (Council Lv6+ + operator) |

## The 11 gates pinned by the new charter-gate test (lexicon `const`/`enum`)

- **G1 mirror-not-adjudicator** — `article.verdict` const false + `article.truthRating` const 0.
- **G2 no ads / no engagement rank** — `issue.rankSignals` ∈ {recency, section-fit,
  source-diversity, actor-relevance, geo-proximity}; paid-placement/sponsored/engagement/
  dwell-time unrepresentable.
- **G3 no reader surveillance** — `article.personalizedFor` const false (面 identical for all).
- **G4 copyright / link-out** — `article.fullText` const false; `excerpt` maxLength 280;
  `outlet.access` ∈ {open, registration-wall} (no paywall/terminal feed).
- **G7 no-server-key** — `issue.serverHeldKey` const false.
- **G9 mirror-not-impersonation** — `article.speakAs` const false.
- **G10 non-eschatological** — `issue.final` const false.
- **G11 medium-not-source** — `article.kind` ∈ {mirror, actor-event} (no `:original`).

(G5 source-provenance / G6 Murakumo-only / G8 outward-gated are enforced in the cells/methods;
the manifest declares all G1–G11.)

## R0 → R1 gate

Council Lv6+ + operator for live RSS/firehose ingest (G8); R1 wires the `actor_project` cell
to the feed-post membrane (ADR-2605231902) for `app.bsky.feed.post` projection. Per the
pipeline ADR-2606161536, the CC-corpus → G4-bounded `:article` derivation (D1) lands here.

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `kawaraban.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
