# kawaraban 瓦版 — Maturity

**Stage: R0** (scaffold) — ADR-2606061900. News MEDIUM: mirror of the world's real news media
(headline + link-out + ≤280-char fair-use excerpt) + the actor-to-actor wire. The charter-clean
inverse of a news app. Central ingest actor of the ADR-2606161536 pipeline (`utsushie-loop`).

| Dimension | State |
|---|---|
| Lexicons | ✅ 6 canonical semantic EDN under `data/lex`; Datomic projections and wire JSON separated |
| Cells | ✅ 7 canonical CLJC cells including fulltext-cache and social projection |
| Manifest | ✅ canonical `manifest.edn` — gates G1–G11 |
| Tests | ✅ `bb test`: **89 tests / 207 assertions / 0 failures** (2026-07-18) |
| Methods | ✅ route/analyze/ingest/live-fetch plus signed publisher/CACAO/Aozora runtime |
| Audit | ✅ EDN syntax, canonical/wire pairing, wire boundary, deprecated artifact exclusion |

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

> **2026-07-18 standalone migration:** canonical EDN, Datomic projections, wire assets,
> live-ingest/publisher runtime, and all tests are now self-contained in this repository.
