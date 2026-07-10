# kawaraban 瓦版 — News Medium

> Real-media **mirror** + actor-to-actor **wire**. kotoba-wasm-native, runs on the
> Murakumo fleet. ADR-2606061900.

瓦版 was the original Japanese news medium — Edo-period clay-block broadsheets cried and
sold in the street. It is a **sheet (面)**, not an oracle: it carries *what was said* and
*who it concerns*, it does not rule what is true.

## What it is

A **news MEDIUM** with two faces over one kotoba Datom log:

1. **Mirror** — datafies the world's real news media (outlets · 面/sections · headlines ·
   bylines · links) into the Datom log as an append-only as-of trail, matching the
   **surface (面)** of actual news media: 一面 / 政治 / 経済 / 国際 / 社会 / 文化 / 科学 /
   スポーツ. Each mirrored article is `headline + canonical link + bounded fair-use excerpt
   + outlet`. **It links out; it never stores the body and never rules truth.**

2. **Medium** — the connective wire **between etzhayyim actors**. Each first-party actor's
   own Datom as-of events project into the matching 面 as `:article/kind :actor-event`, and
   every article carries `:news.mention` edges to the actors/entities it concerns. The
   `article × mention × 面` graph **is** the actor-to-actor wire:

   ```
   danjo finds a discrepancy ─┐
   watari sees a chokepoint   ├─►  kawaraban routes each into its 面 ──► one story,
   watatsuna a cable fault    │     mention-edges link the actors    ──► kanae renders
   mitooshi a forecast       ─┘                                            the 経済/国際 面
   ```

## Why it is the charter-clean inverse of a "news app"

| A news app does… | kawaraban does the opposite |
|---|---|
| sells ads / sponsored placement (Charter-Rider §2) | **G2** — `:paid-placement`/`:sponsored` unrepresentable |
| optimizes for engagement / dwell (Charter §1.13) | **G2** — ranks by recency / 面-fit / source-diversity / actor-relevance only |
| profiles the reader, personalizes the feed | **G3** — no `:reader` entity; the 面 is identical for all |
| republishes the full article body | **G4** — `:full-text` unrepresentable; headline + link + excerpt |
| adjudicates / fact-checks ("true/false") | **G1** — `:verdict`/`:truth-rating` unrepresentable (ake/danjo own that) |
| speaks in an outlet's or person's name | **G9** — `:speak-as` unrepresentable (mirror, ADR-2606042330) |
| is itself the source | **G11** — every article is a `:mirror` or an `:actor-event`; `:original` does not exist |

## Layout

```
kawaraban/
├── manifest.jsonld                  # actor manifest (runtime=kotoba-wasm, Murakumo fleet placement)
├── CLAUDE.md / README.md
├── run_tests.sh                     # one-command test runner
├── wasm/wit/world.wit               # kotoba-wasm component contract (export compute: func() -> string)
├── lex/                             # 6 lexicons (com.etzhayyim.kawaraban.*)
│   ├── outlet.edn  section.edn  article.edn  mentionEdge.edn  issue.edn  kawarabanReview.edn
├── cells/                           # 5 Pregel cells (coded state machines; .solve() raises at R0)
│   ├── outlet_ingest/  article_mirror/  section_route/  actor_project/  issue_compose/
│   └── test_state_machines.py
├── methods/                         # offline engines
│   ├── route.py     # THE MEDIUM — actor→面 wire table + article×mention×面 graph builder
│   ├── analyze.py   # issue composer — ranks a front-面 by G2 public-good signals only
│   ├── ingest.py    # offline outlet normalizer (G4 membrane, --live G8-gated)
│   └── test_route.py  test_analyze.py  test_ingest.py
└── data/
    └── seed-news-graph.kotoba.edn   # :representative seed (7 outlets / 10 面 / 12 articles / 9 wires / 24 mentions)
```

Ontology: [`/00-contracts/schemas/news-medium-ontology.kotoba.edn`](../../00-contracts/schemas/news-medium-ontology.kotoba.edn).

## Run

```bash
./run_tests.sh                       # all suites
python3 methods/analyze.py           # compose an edition from the seed → out/edition.md
python3 methods/route.py             # print the actor→面 wire + connection graph
```

## Status

🟢 **R1** (ADR-2607110200) — live RSS/Atom fetch (`methods/live_fetch.cljc`) and real
app-aozora publish (`src/kawaraban/{cacao,aozora,publisher,publish}.clj(c)`) are
code-complete and tested against local fixtures/mocks. **Both remain OFF by default** —
`KAWARABAN_ALLOW_LIVE_INGEST` is unset (refused) and `MockPublisher` is the default
publisher; actually enabling live network fetch or real aozora.app publish is a separate,
explicit operational step outside this ADR. Registered in INFRA_ACTORS →
`did:web:etzhayyim.com:actor:kawaraban`.
