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

> **2026-07-19 world-scope outlet expansion (ADR-2607197800):** `data/outlets/allowlist.edn`
> grew from 22 to 37 entries (registry-only change; no lexicon/cell/charter-gate code
> touched, `KAWARABAN_ALLOW_LIVE_INGEST` semantics unchanged). The prior set skewed heavily
> anglophone + Japan + Qatar (US/GB/DE/FR/HK/CA/AU/JP/QA/INT only). Added 15 new
> state/public-broadcaster or non-profit/international-org outlets spanning China, Russia,
> Iran, Turkey, South Africa, Brazil, Latin America (multi-country), Vietnam, Malaysia,
> Singapore, South Korea, and WHO (international org):
>
> - **10 `:verified true`** (live-fetched this session, confirmed real RSS/Atom XML with
>   current, same-day-dated items): CGTN (China), TASS (Russia), Press TV (Iran), Anadolu
>   Agency (Turkey), SABC News (South Africa), Agência Brasil/EBC (Brazil), BERNAMA
>   (Malaysia), CNA (Singapore, semi-state via Mediacorp/Temasek — flagged honestly in its
>   `:note`), KBS World (South Korea), WHO News (international org).
> - **5 `:verified false`** (honest, non-fabricated): Xinhua (China) — the URL resolves and
>   returns real RSS XML, but every item is stale (dated 2017–2018, an abandoned legacy
>   endpoint, not fabricated but not live either); TRT World (Turkey), Arirang (South Korea),
>   Voice of Vietnam/VOV World (Vietnam), and teleSUR English (Latin America, multi-country)
>   — no working public feed URL could be located this session for these four, so
>   `:feed-url` is left `nil` rather than guessing wrong, same discipline as the pre-existing
>   AP entry.
>
> India (DD News / PIB) was deliberately NOT added here — handled by a separate concurrent
> task on the kouhou repo instead, per ADR-2607197800. World coverage remains best-effort and
> incomplete after this wave, not exhaustive — gaps should keep being filled incrementally
> and honestly flagged, not silently backfilled with guessed URLs.
