# Feature Document — "Sage" (Value Addition Knowledge Concierge) — POC

> **Status:** Draft v1.1 (updated Jul 16 2026 — query interpretation, own DB, SSE streaming, confidence scoring)
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
- Web chat UI (deferred post-POC; POC uses Postman + SSE).
- Live Orion/Nexus API calls at query time (all queries run against pre-built own DB).

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

### F0 — Knowledge Database Population (Own DB)
**Description:** Before any query can be served, Sage must build its own searchable database by pulling all data from the **Orion API** (single source — Nexus is just a frontend on the same API) and approved PDFs into a local Neo4j instance that supports both **semantic search** (vector embeddings) and **Graph RAG** (relationship traversal). For the POC this is a **one-time seed script**; a scheduled cron endpoint may be added as a stretch goal.

**What is stored (all from Orion API + PDFs — no separate Nexus backend):**
- **From `GET /tech/categories`:** full tech taxonomy → `Technology` nodes.
- **From `GET /valueAdd/valueAddsByTag?tag={tech}`** *(per tag, in parallel)*: team, member list, category (HP/Innovation/Agent System), summary, document handle → `HardProblem`, `Team`, `Person`, `Document` nodes + all edges.
- **From `GET /technology/getTechDigest/label?techDigestLabel={tech}`** *(per tag)*: customer/project coverage → enriches `Technology` nodes.
- **From `GET /customers/valueAdd/hardProblemsFinancialYear?cycleId=8,7`:** gap-fill catalog for any HP not reached via tag fan-out.
- **From approved PDFs:** chunked text + metadata (team, tech tags, category, title, link) → embedded as vector nodes linked to `HardProblem`/`Document`.

**Acceptance criteria:**
- Seed script calls `/tech/categories` first, then fans out to `valueAddsByTag` and `getTechDigest` per tag in parallel, then runs the financial-year gap-fill.
- Embeddings are generated for all text-rich fields (HP summaries, problem descriptions, PDF chunks) and stored in Neo4j vector index.
- Graph nodes and edges are created: `(:Team)-[:SOLVED]->(:HardProblem)-[:USES_TECHNOLOGY]->(:Technology)`, `(:Person)-[:MEMBER_OF]->(:Team)`, `(:HardProblem)-[:HAS_DOCUMENT]->(:Document)`.
- Seed is idempotent — re-running upserts records, does not create duplicates.
- Seed completes and logs a summary: records inserted per endpoint, embeddings generated, graph edges created.

---

### F1 — Ask → Query Interpretation → Knowledge Card
**Description:** A developer submits a natural-language question via API. The system interprets the query and explicitly divides its understanding into two sections before searching:

**(a) Problem State** — a restatement of what problem is being asked: what the developer is trying to solve, how it may have manifested in similar organisational contexts.  
**(b) Tech Needed** — the specific technologies, patterns, or domains implied by the question (e.g., "GraphRAG", "SSO", "streaming", "PDF ingestion").

These two sections drive parallel searches (see F2) and are included in the structured response so the developer can verify the system understood the query correctly.

**Acceptance criteria:**
- Given a natural-language question, the system returns an explicit `problemStatement` and `techNeeded` field derived from the query.
- Both sections are used as independent search inputs (parallel search — see F2).
- A **Knowledge Card** (see §8) is assembled from the search results and returned.
- When evidence exists, the correct team(s)/person(s) are named.
- When no internal knowledge exists, the card clearly says so (see F5).
- Every claim in the card cites its source (team, document).

### F2 — Parallel Semantic + Graph RAG Search on Own DB
**Description:** Using the two sections derived in F1, Sage runs two parallel searches against its own database and merges the ranked results:

- **Semantic search** (vector similarity) — uses the `problemStatement` to find prior art with similar problem descriptions and document chunks.
- **Graph RAG traversal** — uses `techNeeded` tags to walk the knowledge graph (`Technology → HardProblem → Team → Person`) and retrieve related hard problems and teams via relationship paths.

Results from both searches are merged and re-ranked by a **confidence score** (see F3). Sage never calls the Orion API at query time — all searches run against the pre-built Neo4j DB.

**Acceptance criteria:**
- Both searches run in parallel and complete before the response is assembled.
- Results from semantic search and graph traversal are deduplicated (same HP surfaced by both → merged into one result).
- Each result carries the search path(s) that produced it (semantic / graph / both) for transparency.
- Sources (Orion API data, PDF) are attributed per result on the Knowledge Card.

### F3 — Confidence Score & Ranked Results
**Description:** Every result returned by Sage carries a **confidence score** (0–1) reflecting how well the prior art matches the query. Results are sorted descending by confidence score.

**Score composition** (exact weighting is an open question — see §15):
- Vector similarity score from semantic search.
- Graph relationship strength / path length from graph traversal.
- Boost for results surfaced by *both* semantic and graph paths.

**Acceptance criteria:**
- Every result in the Knowledge Card has a numeric confidence score (0–1, rounded to 2 dp).
- Results are sorted descending by confidence score.
- Results below a minimum threshold (TBD in tech spec) are excluded.
- The top-N results are returned (N configurable; default 5 for POC).
- Score composition logic is documented in the tech spec derived from this feature.

### F4 — Document retrieval & summary
**Description:** Provide the relevant document (link/attachment) and a short summary so the developer can judge fit. Expertise routing (team + people) is a derived output of F2 + F3 — not a separate search step.
**Acceptance criteria:**
- A 2–3 line summary of the relevant solution is shown per result.
- A link/handle to the full document is provided when one exists.
- When no formal document exists, the result says "no formal doc — reach out to the team."
- The Knowledge Card names at least one team and lists solved-by people per result where available.
- Confidence/evidence is shown per result (score + evidence string, e.g., "matched 5 hard problems tagged GraphRAG; semantic similarity 0.83").

### F5 — Honest no-answer + gap flag
**Description:** When nothing relevant is found, Sage says so and flags the question as a **candidate Hard Problem**.
**Acceptance criteria:**
- No fabricated answer is produced when internal knowledge is absent.
- The response includes a "candidate Hard Problem" callout when there is no match.

### F6 — SSE Streaming API (Postman client — POC interface)
**Description:** The primary interface for the POC is a **REST + SSE endpoint** consumed from Postman. Responses are streamed as Server-Sent Events so the developer sees progressive feedback rather than waiting for the full answer.

**SSE event sequence:**
1. `status` — `"Interpreting query…"` (after query received)
2. `status` — `"Identified problem state and tech context"`  (after F1 interpretation)
3. `status` — `"Searching knowledge base…"` (parallel F2 searches launched)
4. `status` — `"Ranking results…"` (after both searches return)
5. `result` — the full structured **Knowledge Card** JSON (see §8)
6. `done` — stream closed

**Acceptance criteria:**
- `POST /api/ask` (or equivalent) accepts `{ "query": "…" }` and responds with `Content-Type: text/event-stream`.
- Each SSE event is `data: <JSON payload>\n\n` formatted per the SSE spec.
- The `result` event contains the complete Knowledge Card JSON.
- Stream ends with a `done` event; the connection closes cleanly.
- Postman can receive and display the streamed events without any custom client code.
- Errors during processing are sent as an `error` event before closing.

> **Future (post-POC):** A web chat UI (original F6) will be built once the API is stable. Deferred from POC scope to keep the 2-week timebox.

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

Every response returns this consistent structure as a JSON object in the `result` SSE event. This is the primary artifact and the anchor for the API tech spec.

### 8.1 Top-level card fields

| Field | Type | Source | Example |
|---|---|---|---|
| `query` | string | echo of user input | `"How did we handle SSRF for email image fetching?"` |
| `problemStatement` | string | derived — F1 interpretation | `"Safely fetching external images from emails without exposing the server to SSRF attacks"` |
| `techNeeded` | string[] | derived — F1 interpretation | `["SSRF mitigation", "npm image proxy", "email rendering"]` |
| `directAnswer` | string | derived from top result | `"Yes — 1 team has solved this."` |
| `results` | Result[] | F2 + F3 search output | see §8.2 |
| `gapFlag` | boolean | derived | `false` |
| `gapMessage` | string \| null | derived | `"No internal prior art found — candidate Hard Problem"` or `null` |

### 8.2 Per-result fields (each item in `results[]`)

| Field | Type | Source | Example |
|---|---|---|---|
| `rank` | int | F3 ranking | `1` |
| `confidenceScore` | float (0–1) | F3 scoring | `0.87` |
| `matchedVia` | string[] | F2 path | `["semantic", "graph"]` |
| `teamName` | string | Orion API (own DB) | `"Payments Platform"` |
| `hardProblemTitle` | string | Orion API (own DB) | `"SSRF-safe external image loader"` |
| `category` | string | Orion API | `"Hard Problem"` / `"Innovation"` / `"Agent System"` |
| `solvedBy` | string[] | Orion (own DB) | `["Priya Sharma", "Arjun Mehta"]` |
| `summary` | string | Orion summary / PDF chunk | 2–3 line gist of the solution |
| `documentLink` | string \| null | Orion / PDF corpus | URL or handle, or `null` |
| `evidenceDetail` | string | derived | `"5 hard problems tagged SSRF; semantic similarity 0.87"` |
| `sourceAttribution` | string[] | F2 | `["Orion API", "PDF"]` |

---

## 9. Data sources (functional view)

### 9.1 Upstream source — Orion API (the only data source)

> **Architecture note:** "Nexus" is the internal web portal UI (`nexus.talentica.com`). Nexus has no separate backend — it calls the same Orion API that Sage uses directly. There is therefore **one data source: Orion API**, accessed with an `api-key` header.

| Auth method | Host | Used by |
|---|---|---|
| `api-key` header | `apiorion.talentica.com` | Nexus frontend; Sage seed script (preferred) |
| Cookie (`auth-token`) | `apidev-orion.talentica.com` | Dev/browser sessions |

**The 4 Orion endpoints Sage uses at ingest (in call order):**

| Step | Endpoint | What it returns | Graph usage |
|---|---|---|---|
| 1 | `GET /tech/categories` | Full technology taxonomy (closed vocabulary of all tech tags) | Seeds `Technology` nodes; drives fan-out in step 2+3 |
| 2 | `GET /valueAdd/valueAddsByTag?tag={tech}` *(per tag)* | Rich HP records: team, member list, category, summary, document handle/link | Seeds `HardProblem`, `Team`, `Person`, `Document` nodes + all edges |
| 3 | `GET /technology/getTechDigest/label?techDigestLabel={tech}` *(per tag)* | Tech coverage: customer projects using this tech | Enriches `Technology` nodes with coverage context |
| 4 | `GET /customers/valueAdd/hardProblemsFinancialYear?cycleId=8,7` | All HPs by financial cycle (broad fallback catalog) | Gap-fill: catch any HP not already reached via tag fan-out |

**Seed strategy:** Call step 1 first to get all tags → fan-out steps 2+3 in parallel per tag → step 4 as a gap-fill pass.

**Approved PDFs** — deep problem→approach→solution→impact docs, provided separately. Chunked and embedded during seed. PDFs lack structured team metadata; Orion data lacks document content. Blending both is the core idea.

### 9.2 Own Knowledge Database — Neo4j (the search target)

All Orion API data and PDF content is pulled **once at ingest time** (via F0 seed script) and written to a local **Neo4j** instance. All query-time searches run against Neo4j — never against the live Orion API.

**Database: Neo4j** (decided — single store for both graph traversal and vector search via Neo4j 5.x native vector indexes).

| Layer | What is stored | Search method |
|---|---|---|
| **Vector index** | Embeddings of: HP summaries, problem descriptions, PDF chunks | Semantic similarity search (F2 — `problemStatement` input) |
| **Knowledge graph** | Nodes: `Team`, `Person`, `HardProblem`, `Technology`, `Document`. Edges: `[:SOLVED]`, `[:USES_TECHNOLOGY]`, `[:MEMBER_OF]`, `[:HAS_DOCUMENT]` | Graph RAG traversal via Cypher (F2 — `techNeeded[]` input) |
| **Structured metadata** | Team name, HP title, category, document link, solved-by list, evidence counts | Structured lookup + Knowledge Card assembly |

**Hybrid query in Neo4j** — semantic and graph signals can be combined in a single Cypher query using `db.index.vector.queryNodes()` alongside relationship traversal, making the two search paths naturally composable without a separate merge step.

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
- **Interface (POC):** Postman as the only client, consuming the SSE streaming API (`POST /api/ask`). No web UI for the POC; web chat deferred to post-POC (see F6).
- **Answer emphasis:** route-first.
- **Data:** Orion API (single source — Nexus is a frontend that calls Orion, not a separate backend) + curated approved PDFs. All seeded into Neo4j before first query (F0).
- **Query-time data access:** Sage queries only its own pre-built DB — no live Orion/Nexus calls at ask time.
- **DB population:** One-time seed script for the POC. Cron/scheduled refresh is a stretch goal.
- **Timebox:** 2 weeks, 4 engineers, POC-grade.

---

## 12. Technology learning map (thin end-to-end)

The POC must exercise each target technology meaningfully (not deeply). Indicative mapping — refine during tech spec:

| Technology | Feature area it powers |
|---|---|
| **Spec-Driven Development** | This document → per-feature tech specs → tests |
| **Java + Spring Boot** | Public SSE/`POST /ask` shell, config, retrieve HTTP clients, health, deterministic merge/scoring host |
| **Google ADK** | Ask-path agent orchestration: query interpretation, parallel search fan-out (semantic + graph), Knowledge Card synthesis (via LangChain4j → Ollama; not Spring AI ChatClient) |
| **Neo4j** | Own knowledge graph (teams ↔ tech ↔ hard problems ↔ people ↔ documents) + vector index (TBD, see §15 Q4) |
| **GraphRAG** | Graph traversal on tech tags → hard problems → teams → people (F2 graph path) |
| **Semantic / Vector Search** | Similarity search on problem descriptions, summaries, PDF chunks (F2 semantic path) |
| **SSE (Server-Sent Events)** | Progressive streaming response to Postman client (F6) |
| **Seed script (Python or Java)** | One-time Orion API + PDF ingest into Neo4j (F0); fan-out across 4 Orion endpoints |
| **Promptfoo** | Runs the evaluation set (F8) |
| **MLflow** | Logs metrics/experiments (F8) |

> Learning outcome to report: "we used each technology end-to-end and understand its role and trade-offs" — not production mastery.  
> **Ownership detail:** Spring Boot hosts/transports; ADK owns agents; Graph RAG retrieves. Spring AI is out of the POC ask-path (would overlap ADK). See [docs/spec-coverage-map.md](docs/spec-coverage-map.md).

---

## 13. Demo plan

- 5–8 **golden demo scenarios backed by verified real data** from Orion API/PDFs seeded into Neo4j, built around the 5 real tech-lead queries (§5).
- Scripted-but-real (not live roulette) for a reliable CEO demo.
- Include at least one **gap-flag** scenario (F5) to show Sage detects missing knowledge, not just existing knowledge.

---

## 14. Risks

| Risk | Mitigation |
|---|---|
| 6+ technologies in 2 weeks is tight | Set expectation: "concept validated + hands-on learning," not production-ready — communicated *before* the demo |
| Orion API access or PDFs not ready day 1 | Confirm `api-key` validity + shortlist of PDFs before kickoff; seed script (F0) is the critical path — demo depends on real data |
| Local model latency/quality | Validate hardware early; route-first reduces reliance on generation; stream responses |
| Google ADK defaults to Gemini | Phase-0 spike: confirm ADK can drive the local model via an OpenAI-compatible/LiteLLM endpoint |
| Hallucinated answers in demo | Route-first + extractive summaries + mandatory citations + honest no-answer |
| DB technology choice blocks architecture | Decide Neo4j-only vs Neo4j+vector store in week 1 tech spec; Neo4j-only is the lower-risk default |
| Own DB is stale vs live Orion/Nexus | Acceptable for POC (one-time seed); document staleness as a known limitation; defer refresh cron to post-POC |
| Parallel search result merging is non-trivial | Define deduplication key (HP title + team) and merge logic early; treat as a tech spec deliverable |

---

## 15. Open questions (resolve before/at kickoff)

1. **Local model + hardware** — Which local LLM and on which machine? Affects latency bar, answer quality, and the local embedding model choice.
2. **Orion API credentials** — Confirm the `api-key` (`53e72eed-…`) and cookie tokens are valid and stable for the POC duration. The seed script (F0) is blocked on live API access.
3. **Financial year cycle IDs** — Seed uses `cycleId=8,7`. Confirm these are the correct/current cycles, or whether additional cycle IDs should be included.
4. **Final team/topic ownership split** — owner: project lead.
5. ~~**Database technology**~~ — **Resolved: Neo4j only** (graph traversal + native vector index in one store).
6. **Confidence score formula** — The per-result score is a weighted blend of semantic similarity and graph relationship strength. Exact weights TBD in tech spec:
   - What weight ratio between semantic (vector) and graph scores?
   - How is graph "strength" quantified (path length, relationship count, HP count)?
   - What is the minimum threshold score below which results are suppressed?
7. **Top-N result count** — Default is 5 for the POC. Confirm before implementation.
8. **Re-seed trigger** — Is a manual re-seed endpoint required for the POC (e.g., `POST /admin/reseed`), or is running the seed script sufficient?

---

## 16. Glossary

- **Value Addition** — org process recognizing approved Hard Problems, Innovations, and Agent Systems.
- **Hard Problem / Innovation / Agent System** — the three approved submission categories.
- **Nexus** — internal web portal (`nexus.talentica.com`) that surfaces Orion data. Nexus has no separate backend; it calls the Orion API with an `api-key` header. Sage uses Orion directly — "Nexus" and "Orion API" refer to the same data source.
- **Orion** — the backend API and data system (`apiorion.talentica.com`) powering both the Orion UI and the Nexus portal. Single source of truth for: technology taxonomy, hard problems, teams, members, summaries, document handles.
- **Own DB / Knowledge Database** — Sage's pre-built Neo4j instance populated from Orion API and PDFs; the only data store queried at ask time.
- **Knowledge Card** — Sage's consistent structured answer contract (§8), returned as JSON in the SSE `result` event.
- **Route-first** — answer stance emphasizing team/person + document over synthesized prose.
- **Problem State** — the system's restatement of what organisational problem the user is asking about (one of two F1 interpretation outputs).
- **Tech Needed** — the specific technologies/patterns implied by the user's query (the second F1 interpretation output); drives Graph RAG traversal.
- **Confidence Score** — a 0–1 float measuring how closely a prior-art result matches the query; weighted blend of semantic similarity and graph relationship strength.
- **Semantic Search** — vector-similarity search over embeddings of problem descriptions, summaries, and PDF chunks.
- **Graph RAG** — retrieval-augmented generation that traverses a knowledge graph (Team → HardProblem → Technology → Person) to find related prior art.
- **SSE (Server-Sent Events)** — HTTP streaming protocol used to push progressive status and the final result to the Postman client.
- **Seed script** — one-time (or on-demand) script that calls all 4 Orion API endpoints and processes approved PDFs, writing everything into Neo4j with embeddings and graph edges.
