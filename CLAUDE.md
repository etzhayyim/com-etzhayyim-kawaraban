# kawaraban 瓦版 — CLAUDE instructions

News medium. ADR-2606061900. **Read the root `/CLAUDE.md` Charter + substrate rules
first.** kawaraban-specific invariants below make the Charter concrete for this actor;
they weaken nothing.

## The one-sentence identity

kawaraban is a **news MEDIUM, kotoba-wasm-native, on the Murakumo fleet**: it **mirrors**
the world's real news media into the kotoba Datom log matching the SURFACE (面) of real
news media, and it is the **connective wire** that links etzhayyim actor to actor (each
actor's Datom as-of events project into the matching 面; every article carries
`:news.mention` edges). It is the **charter-clean inverse of a news app** — it links out,
never advertises, never engagement-optimizes, never profiles a reader, never republishes
full text, never adjudicates truth, never speaks AS anyone.

## Two faces, one substrate

- **Mirror** (`:article/kind :mirror`) — datafy a real outlet's public facing page:
  headline + canonical `:url` + bounded fair-use `:excerpt` + `:outlet` + `:as-of`. Link
  out. Never the body (G4). Never a verdict (G1).
- **Medium** (`:article/kind :actor-event`) — project a first-party actor's Datom as-of
  event into the matching 面 (feed-post membrane reuse, ADR-2605231902). The
  article×mention×面 graph IS the actor-to-actor wire: danjo finds → kawaraban carries →
  kanae renders; a chokepoint story links watari + watatsuna + mitooshi in one 面.

## The 11 gates — do NOT weaken

Each gate lives in **three places**: schema `:db/allowed`/enum + lexicon `const`/`enum` +
Python `ValueError`/refusal. Touch one, touch all three or you create a representable
charter violation.

- **G1 mirror-not-adjudicator** — `:article/verdict` + `:article/truth-rating` are
  `:db/allowed [false]` / `const false`. kawaraban records that outlet X published headline
  H at T; ruling H true/false is **ake's** (community-edit) and **danjo's** (discrepancy)
  job, never news.
- **G2 no-ads / no-engagement-rank** — `:news.rank/signal` enum is
  `{:recency :section-fit :source-diversity :actor-relevance :geo-proximity}`.
  `:paid-placement`/`:sponsored`/`:engagement`/`:dwell-time` are **not members**. Never add
  them (Charter §1.13 addictive design + Charter-Rider §2 ads).
- **G3 no-reader-surveillance** — there is **no `:reader` entity**;
  `:article/personalized-for` is `:db/allowed [false]`. The 面 is identical for everyone.
- **G4 copyright / link-out** — `:article/full-text` is `:db/allowed [false]`. Headline +
  link + ≤280-char excerpt + attribution only. Only **public facing pages**
  (`:outlet/access ∈ {:open :registration-wall}`) — paywall/terminal feeds unrepresentable.
  The PUBLIC invariant (never publish / never represent full text in the public ontology)
  is absolute and is **not** weakened by the PRIVATE `fulltext_cache` buffer
  (ADR-2607010930, 実装 not charter): `cells/fulltext_cache/` may hold a fetched article
  BODY for an analysis consumer (yomi 読み) in `data/ingest/fulltext-buffer/` (gitignored),
  PRIVATELY — never transacted to the public Datom log, never projected to a 面, never
  published; `article_mirror`'s `fullText` stays `false` in the public projection regardless.
  Access membrane unchanged: a body is cached only for `:open`/`:registration-wall` outlets.
- **G5 source-provenance-honest** · **G6 Murakumo-only** (LiteLLM `127.0.0.1:4000`) ·
  **G7 no-server-key** (`:server-held-key` const false; projection + publish member-signed) ·
  **G8 outward-gated** (live ingest/publish = Council Lv6+ + operator) ·
  **G9 mirror-not-impersonation** (`:article/speak-as` `:db/allowed [false]`) ·
  **G10 non-eschatological** (`:issue/final` `:db/allowed [false]`) ·
  **G11 connective-by-construction** (every article `:mirror` or `:actor-event`;
  `:original` does not exist — kawaraban is a medium, not a source).

## When editing

- `.solve()` still raises at R0 on every cell — full Pregel/fleet execution stays gated.
  **As of ADR-2607110200 (R1) the live-RSS-fetch code path exists**
  (`methods/live_fetch.cljc` + `data/outlets/allowlist.edn`) and the real aozora-publish
  path exists (`src/kawaraban/{cacao,aozora,publisher,publish}.clj(c)`) — the founder/operator
  set `KAWARABAN_ALLOW_LIVE_INGEST=1` and ran both live on 2026-07-10 (see MATURITY.md "Live
  since 2026-07-10"). In a fresh checkout the env var is still unset by default (refused) and
  `MockPublisher` is still the code default — real activation is always a deliberate,
  out-of-band operator step (setting the env var + injecting a real publisher), never
  something a code change does by itself.
- **Per-organization mirror actors** (`src/kawaraban/mirror_actor.clj`, same-day
  ADR-2607110200 addendum): each outlet gets its OWN self-sovereign identity
  (`.kawaraban/mirrors/<outlet-id>.edn`, gitignored) instead of posting under kawaraban's one
  shared root identity. `ensure-profile!` writes an `app.bsky.actor.profile` disclosing
  "Automated, UNOFFICIAL mirror of \<outlet\> ... not affiliated with or operated by
  \<outlet\>" BEFORE the identity ever publishes an article — this is what keeps a per-outlet
  identity from being G9 (mirror-not-impersonation) — never skip `ensure-profile!` when
  minting a new mirror identity.
- Tests are `bb`-runnable (`./run_tests.sh`, cwd = repo root, uses the self-referential
  `kawaraban -> .` symlink + `bb.edn` so the `kawaraban.*`-prefixed namespaces resolve
  against this repo's flat `cells/`/`methods/` layout) for the portable `.cljc` core, and
  `clojure -M:test` (JVM, `deps.edn`) for the new JVM-only `.clj` I/O (`cacao`/`aozora`).
- The actor→面 wire table lives in `methods/route.py` (`ACTOR_WIRE`). When a new first-party
  actor should feed a 面, add it there with an honest `basis`.

## Siblings / boundaries

- **kataribe 語部** — etzhayyim's OWN press (a primary voice, authors original copy).
  kawaraban is the opposite: it mirrors the world's press and wires the actors together,
  authoring no original first-person claim (G11). If you want etzhayyim to SAY something,
  that is kataribe; if you want to CARRY/CONNECT what actors already did, that is kawaraban.
- **ake 朱 / danjo 弾正** — own truth-correction and discrepancy. kawaraban never adjudicates
  (G1). An ake correction of a mirrored fact is an ake edit, not a kawaraban verdict.
- **feed-post membrane (ADR-2605231902)** — the projection substrate the `actor_project`
  cell reuses at R1 to turn an actor's Datom as-of event into an `app.bsky.feed.post`.
- **kanae 鼎 / watari / watatsuna / mitooshi / kabuto** — the actors kawaraban wires
  together; their events become `:actor-event` articles in the matching 面.
