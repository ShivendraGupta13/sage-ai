# Feature Document — "Sage" (Value Addition Knowledge Concierge) — POC

> **Status:** Draft v1.0 (feature-level, pre-tech-spec)
> **Purpose of this doc:** Define *what* we are building and *why*, at the feature level, so that per-feature **technical specs** can be written from it (Spec-Driven Development). This document intentionally avoids deep technical/architecture decisions — those belong in the tech specs derived from here.
> **Timebox:** 2 weeks · 4 engineers · POC-grade (not production).

---

## 1. Overview

Sage is an internal AI assistant that helps a developer discover **prior art and expertise** inside the organization. Given a technical question, Sage tells the developer **which team/person already knows the topic**, provides **the relevant document or a summary**, and honestly reports when **no internal knowledge exists**.

This is an **expertise-locator + prior-art concierge** — deliberately *not* a generic technical Q&A bot (GPT/Google already do that). Sage's differentiator is the **organization layer**: who here has done this, and where is the proof.

---

## 2. Background & problem

As part of the **Value Addition** process, teams document every approved **Hard Problem, Innovation, or Agent System** as a detailed technical PDF and submit it to a review panel. Over time this creates a valuable corpus of proven solutions — but it is **not discoverable**. Teams cannot easily answer:

- Is technology X already used in any project?
- Has anyone experimented with Y before?
- How was problem Z solved, and by whom?
- Has a comparable solution already been implemented elsewhere?

The result is **duplicated effort** — teams re-research and re-solve problems the organization has already cracked. Sage makes this accumulated expertise discoverable and reusable.

---

## 3. Goals

### 3.1 Dual mandate
1. **Learn** — gain hands-on, end-to-end understanding of the target technologies (see §12).
2. **Prove** — deliver a CEO-demoable result that justifies a future production build.

Where the two conflict: *learning* governs **how** we build (use each technology at least once, meaningfully); *demo value* governs **what** we show.

### 3.2 POC success = three (plus one) measurable outcomes
- **Retrieval quality** — the right source/team surfaces for a question.
- **Answer quality** — the response is correct, useful, and honest when unknown.
- **Latency** — responses are fast enough to feel usable (realistic bar for a local model).
- **Routing accuracy** *(added)* — the correct team/person is named. This is the core value prop and is easy to score.

---

## 4. Non-goals (explicitly out of scope for the POC)

Stated out loud to prevent gold-plating:

- Authentication / SSO / RBAC.
- Production infrastructure, HA, scaling, security hardening.
- Live Microsoft Teams ingestion or a real-time channel bot.
- Incremental re-ingestion, deduplication, corpus lifecycle management.
- Deep multi-turn agentic reasoning / complex agent trees.
- Managing the Value Addition submission/review workflow (Sage *consumes* approved outputs only).

---

## 5. Personas & primary use cases

- **Developer / Tech Lead** (primary): checks for prior art and the right expert before starting work.
- **Architect**: surveys which technologies/patterns exist across teams.
- **Delivery / Practice Manager**: spots duplicated effort and knowledge gaps.

**Representative queries** (real tech-lead questions; these seed the eval set and demo):
1. AWS SES no-reply → preventing automated-reply loops.
2. SMUS ↔ QuickSight IAM-vs-IDC authentication migration / alternatives.
3. AntD `css-dev-only-do-not-override` / `ConfigProvider hashed:false` not working.
4. SSRF-safe JS/npm library for fetching external images from emails.
5. Building a Figma-vs-implementation UI-diff tool.

---

## 6. Value proposition (job-to-be-done)

> "I have a problem → who here already solved it, who do I talk to, and where's the doc/summary so I can judge if it fits?"

**Design stance: route-first.** Sage leads with *which team/person + the document*, and uses generation only to *summarize retrieved content*. This minimizes hallucination risk (critical for a live demo) and delivers the higher-value outcome (connecting people to prior art and to each other).

---

## 7. Features

Each feature has acceptance criteria so a technical spec can be written per feature and mapped 1:1 to evaluation tests.

### F1 — Ask → Knowledge Card
**Description:** A developer asks a natural-language question and receives a single, consistent **Knowledge Card** (see §8).
**Acceptance criteria:**
- Given a question, a Knowledge Card is returned with all applicable fields populated.
- When evidence exists, the correct team(s)/person(s) are named.
- When no internal knowledge exists, the card clearly says so (see F5).
- Every card with a claim includes a citation to a real source (team/doc).

### F2 — Multi-source knowledge blend
**Description:** Sage combines three sources: **Nexus API** (team ↔ tech, # hard problems), **Orion API** (team members, # solved, summaries), and **approved PDFs** (deep documentation).
**Acceptance criteria:**
- Sage can answer routing questions using Nexus/Orion **even when no PDF exists** for the topic.
- When a PDF exists, its content is used for the summary and provided as the document.
- Sources are attributed distinctly on the card.

### F3 — Expertise routing
**Description:** Identify and name the team(s) and person(s) most knowledgeable about the queried topic.
**Acceptance criteria:**
- The card names at least one team when Nexus/Orion has relevant data.
- People are surfaced from Orion where available.
- Confidence/evidence is shown (e.g., "5 hard problems tagged X").

### F4 — Document retrieval & summary
**Description:** Provide the relevant document (link/attachment) and a short summary so the developer can judge fit.
**Acceptance criteria:**
- A 2–3 line summary of the relevant solution is shown.
- A link/handle to the full document is provided when one exists.
- When no formal document exists, the card says "no formal doc — reach out to the team."

### F5 — Honest no-answer + gap flag
**Description:** When nothing relevant is found, Sage says so and flags the question as a **candidate Hard Problem**.
**Acceptance criteria:**
- No fabricated answer is produced when internal knowledge is absent.
- The response includes a "candidate Hard Problem" callout when there is no match.

### F6 — Web chat interface
**Description:** A simple web chat UI to ask questions and view Knowledge Cards.
**Acceptance criteria:**
- User can type a question and see a rendered Knowledge Card.
- Citations/links are visible and openable.
- Responses stream (to mask local-model latency) — see §11.

### F7 — PDF ingestion (curated corpus)
**Description:** Ingest a curated set of 10–30 approved PDFs into a searchable index with basic metadata (title, team, technologies, category).
**Acceptance criteria:**
- Curated PDFs are ingested and retrievable by question.
- Basic metadata is captured and usable for attribution.

### F8 — Evaluation & metrics harness
**Description:** A ~15–20 question evaluation set (including the 5 real queries) scored on the success metrics, run via Promptfoo, with results logged in MLflow across configuration experiments.
**Acceptance criteria:**
- Eval set runs and reports retrieval quality, answer quality, routing accuracy, latency.
- Results are logged per experiment run for comparison.

---

## 8. The Knowledge Card (answer contract)

Every response returns this consistent structure. This is the primary UX artifact and the anchor for the API tech spec.

| Field | Source | Example |
|---|---|---|
| Direct answer | derived | "Yes — 2 teams have used GraphRAG." |
| Team(s) + people | Nexus + Orion | "Payments Platform (Priya, Arjun)" |
| Evidence / confidence | Nexus/Orion counts | "5 hard problems tagged GraphRAG" |
| Summary | Orion summary / PDF | 2–3 line gist of the solution |
| Full document | PDF corpus | link/attachment, or "no formal doc yet" |
| Honesty / gap flag | derived | "No internal prior art found — candidate Hard Problem" |

---

## 9. Data sources (functional view)

| Source | Provides | Role in Sage |
|---|---|---|
| **Nexus** (API + filters) | Which team uses which tech; # hard problems per team/tech | Coverage & routing ("is anyone using X / which teams") |
| **Orion** (API + filters) | Team members, # hard problems solved, summaries | Expertise (people) + summaries |
| **Approved PDFs** | Deep problem→approach→solution→impact documentation | The "read the actual solution" payload |

> Nexus/Orion lack document content; PDFs lack structured team/coverage metadata. Blending all three is the core idea — structured routing truth + deep documentation.

---

## 10. Success metrics (POC bar)

| Metric | How measured | Rough target |
|---|---|---|
| Retrieval quality | correct source/team in top-k on golden set (Promptfoo) | ~80% recall@5 |
| Answer quality | human 1–5: correct + useful + honest-when-unknown | ≥ 4/5 avg |
| Routing accuracy | named the correct team/expert | ≥ 80% |
| Latency | P95, realistic for local model + streaming | set after hardware check |

---

## 11. Constraints & assumptions

- **Local LLM** (privacy-preserving; IP never leaves the network). Implications:
  - Keep generation constrained to *summarizing retrieved content + naming the team* (reinforces route-first).
  - Latency depends on hardware; use streaming to mask perceived latency; set the latency bar after validating the machine.
  - Use a **local embedding model** so the pipeline runs fully offline.
- **Interface:** single web chat UI.
- **Answer emphasis:** route-first.
- **Data:** Nexus API + Orion API + curated approved PDFs available at project start.
- **Timebox:** 2 weeks, 4 engineers, POC-grade.

---

## 12. Technology learning map (thin end-to-end)

The POC must exercise each target technology meaningfully (not deeply). Indicative mapping — refine during tech spec:

| Technology | Feature area it powers |
|---|---|
| **Spec-Driven Development** | This document → per-feature tech specs → tests |
| **Java + Spring AI** | Ingestion, retrieval orchestration, chat service, streaming |
| **Google ADK** | Agent orchestration (routing between sources / synthesis) |
| **GraphRAG** | Linking teams ↔ tech ↔ hard problems ↔ documents across Nexus/Orion/PDFs |
| **Promptfoo** | Runs the evaluation set (F8) |
| **MLflow** | Logs metrics/experiments (F8) |

> Learning outcome to report: "we used each technology end-to-end and understand its role and trade-offs" — not production mastery.

---

## 13. Demo plan

- 5–8 **golden demo scenarios backed by verified real data** in Nexus/Orion/PDFs, spined on the 5 real tech-lead queries (§5).
- Scripted-but-real (not live roulette) for a reliable CEO demo.
- Include at least one **gap-flag** scenario (F5) to show Sage detects missing knowledge, not just existing knowledge.

---

## 14. Risks

| Risk | Mitigation |
|---|---|
| 6 technologies in 2 weeks is tight | Set expectation: "concept validated + hands-on learning," not production-ready — communicated *before* the demo |
| Nexus/Orion access or PDFs not ready day 1 | Confirm API creds + shortlist of PDFs before kickoff; demo depends on real data |
| Local model latency/quality | Validate hardware early; route-first reduces reliance on generation; stream responses |
| Google ADK defaults to Gemini | Phase-0 spike: confirm ADK can drive the local model via an OpenAI-compatible/LiteLLM endpoint |
| Hallucinated answers in demo | Route-first + extractive summaries + mandatory citations + honest no-answer |

---

## 15. Open questions (non-blocking; resolve before/at kickoff)

1. Which local model + hardware? (affects latency bar and answer quality)
2. Confirm Nexus/Orion API credentials and the shortlist of approved PDFs are available now.
3. Final team/topic ownership split (owner: project lead).

---

## 16. Glossary

- **Value Addition** — org process recognizing approved Hard Problems, Innovations, and Agent Systems.
- **Hard Problem / Innovation / Agent System** — the three approved submission categories.
- **Nexus** — internal system: team ↔ tech stack, # hard problems solved.
- **Orion** — internal system: team members, # hard problems solved, summaries.
- **Knowledge Card** — Sage's consistent answer contract (§8).
- **Route-first** — answer stance emphasizing team/person + document over synthesized prose.
