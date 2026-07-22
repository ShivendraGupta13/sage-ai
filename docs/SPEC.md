# Spec: Sage AI (Java) — Expertise Locator POC

> **Status:** Approved (2026-07-22)  
> **Scope:** This repository (`sage-ai`) only — Java ask orchestration on `:8080`  
> **Not in scope for implementation here:** `graph-rag-service` (Neo4j, Orion/PDF ingest, `/retrieve/*` algorithms)  
> **Product narrative:** [feature-document.md](feature-document.md)  
> **Locked system design:** [architecture.md](architecture.md) — do not reopen decided boundaries without an explicit reason  
> **ADK wiring:** [google-java-adk-usage.md](google-java-adk-usage.md)  
> **HTTP contracts:** [contracts/ask-api.md](contracts/ask-api.md), [contracts/retrieve-api.md](contracts/retrieve-api.md)

---

## Objective

### What

Sage is an internal **expertise-locator + prior-art concierge**. A developer asks a technical question; Sage answers:

> *Who here already solved this, who do I talk to, and where is the proof?*

This repo implements the **Java Sage service**: interpret the query → call Graph RAG retrieve APIs in parallel → merge/score hits → synthesize a **Knowledge Card** → stream it over **SSE** to Postman/curl.

### Who

| Persona | Need |
|---------|------|
| Developer / Tech Lead (primary) | Prior art + expert before starting work |
| Architect | Which technologies/patterns exist across teams |
| Delivery / Practice Manager | Duplicated effort and knowledge gaps |

POC client: **Postman or curl** only. No web chat UI in this POC.

### Why (POC dual mandate)

1. **Learn** — exercise Spring Boot, Google Java ADK (`ParallelAgent`), SSE, hybrid retrieve clients end-to-end.
2. **Prove** — CEO-demoable route-first answers with honest gaps when nothing is found.

### Feature scope (this repo)

Approved. Architecture §18 ownership:

| Feature | In this Java SPEC? | Role of `sage-ai` |
|---------|--------------------|-------------------|
| **F1** Ask → interpret → Knowledge Card | **Yes** | Own end-to-end ask pipeline |
| **F2** Parallel semantic + graph search | **Yes (orchestration)** | ADK `ParallelAgent` + HTTP clients; Python runs the searches |
| **F3** Confidence score & ranking | **Yes** | Deterministic `ResultMerger` |
| **F4** Doc link, summary, team/people | **Yes (assembly)** | Card fields from retrieve hits; no Orion at ask-time |
| **F5** Honest no-answer + gap flag | **Yes** | `gapFlag` / `gapMessage` when no usable hits |
| **F6** SSE streaming API | **Yes** | `POST /ask` + `GET /health` |
| **F8** Eval harness | **Yes** | Promptfoo (and optional MLflow) against same `POST /ask` |
| **F0** Knowledge DB seed | **No — dependency** | Requires seeded Graph RAG / Neo4j |
| **F7** PDF ingestion | **No — dependency** | Owned by Python seed |

### Design stance (locked)

- **Route-first** — lead with team/person + document; generation only summarizes retrieved evidence.
- **Evidence-only synthesis** — never invent teams, people, or documents.
- **Ingest once, query own store** — Orion is never called from Java at ask-time.
- **Interpret then parallel-retrieve** — `problemStatement` + `techNeeded[]` drive both paths.
- **Fail-soft retrieval** — one path failing → empty hits for that path; both empty → honest gap card.
- **Spring hosts / ADK orchestrates / Graph RAG retrieves** — Spring AI is **out** of the ask path.

---

## Tech Stack

| Component | Choice | Notes |
|-----------|--------|-------|
| Language | Java **21** | LTS; Boot 4.1 supports 17–26 |
| Framework | Spring Boot **4.1** | Web + SSE (`SseEmitter`) |
| Agents | Google Java ADK **1.5.0** | + `google-adk-langchain4j` |
| Local LLM | Ollama via LangChain4j | Default model name in config: `llama3.2:3b` *(confirm D1)* |
| Sister service | Graph RAG FastAPI `:8000` | Consumed over HTTP only |
| Build | Maven Wrapper (`./mvnw`) | |
| Eval | Promptfoo → `POST /ask` | F8; MLflow optional for experiment logs |

**Ports:** Sage `8080` · Graph RAG `8000` · Neo4j `7687`/`7474` (sister) · Ollama `11434`.

---

## Commands

```bash
# Dev (this repo)
./mvnw spring-boot:run

# Test
./mvnw test

# Optional Ollama spike (when present)
OLLAMA_SPIKE=true ./mvnw test -Dtest=QueryInterpretSpikeTest

# Package
./mvnw -DskipTests package

# Ask (Graph RAG + Neo4j must already be up from sister repo)
curl -N -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"query":"How did we migrate to OpenTelemetry?"}'

# Health
curl -s http://localhost:8080/health
```

**Sister prerequisites (not started from this repo):**

```bash
cd ../graph-rag-service
docker compose up -d neo4j
# seed + uvicorn on :8000 — see sister README
```

**Env (Java):**

| Variable | Default | Purpose |
|----------|---------|---------|
| `SAGE_GRAPH_RAG_BASE_URL` | `http://localhost:8000` | Retrieve + health base URL |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local LLM |
| `SAGE_LLM_MODEL` | `llama3.2:3b` | ADK chat model *(D1)* |
| `SAGE_RETRIEVAL_TOP_K` | `5` | `top_k` on retrieve calls *(D3)* |
| `SAGE_RETRIEVAL_MIN_SCORE` | `0.60` | Semantic `min_score` |
| `SAGE_SCORING_W1` / `W2` | `0.6` / `0.4` | Semantic vs graph weights *(D2)* |
| `SAGE_SCORING_DUAL_BOOST` | `0.1` | Dual-path boost *(D2)* |
| `SAGE_SCORING_MIN_SCORE` | `0.60` | Drop results below this *(D2)* |

POC defaults for D1–D3 are locked in `application.yml` (see Open Questions — approved 2026-07-22). Change only via Ask-first process.

---

## Project Structure

```text
sage-ai/
├── pom.xml
├── docs/
│   ├── SPEC.md                 # This file — Java POC source of truth for agents + humans
│   ├── feature-document.md     # Product F0–F8 narrative
│   ├── architecture.md         # System design (locked decisions)
│   ├── google-java-adk-usage.md
│   ├── spec-coverage-map.md    # Spring / ADK / Graph RAG ownership checklists
│   ├── orion-api-documentation.md   # Ingest reference only (Python uses it)
│   └── contracts/
│       ├── ask-api.md          # Public POST /ask + GET /health
│       └── retrieve-api.md     # What Java may call on :8000
├── orion-apis/                 # Postman collection (ingest capture / offline seed aid)
├── src/main/java/com/company/sage/
│   ├── SageApplication.java
│   ├── chat/                   # POST /ask, SSE, health
│   ├── clients/graphrag/       # HTTP → /retrieve/semantic, /retrieve/graph, /health
│   ├── model/                  # Knowledge Card + retrieve DTOs
│   ├── adk/
│   │   ├── SageAgents.java     # SequentialAgent SageRoot factory
│   │   ├── agents/             # QueryInterpret, SemanticSearch, GraphTraversal, Synth
│   │   └── tools/              # FunctionTools wrapping retrieve clients + ResultMerger
│   ├── merge/                  # Deterministic scoring / dedupe
│   └── config/                 # LLM, retrieval, scoring Spring config
├── src/main/resources/application.yml
└── src/test/java/com/company/sage/
```

**Boundary rule:** Java never embeds Neo4j drivers, Orion clients, or embedding pipelines. Neo4j is started only from `graph-rag-service`.

---

## Code Style

- Package: `com.company.sage.*`; match existing package layout (`chat/`, `clients/graphrag/`, `adk/`, `merge/`, `model/`, `config/`).
- Prefer clear names over cleverness; keep merge/scoring **pure Java** (no LLM in `ResultMerger`).
- Logs: include `correlationId` when available; never log tokens, secrets, or prompts that contain credentials.
- DTOs must follow contract field names/casing (see Contract SoT below) — do not invent retrieve response fields.
- Spring AI (`spring-ai-*`) must not be added for the ask path.

**Example shape (illustrative — not the final class):**

```java
package com.company.sage.merge;

import java.util.Comparator;
import java.util.List;

/** Deterministic merge: dedupe by hard-problem identity, score, sort desc, apply top-k. */
public final class ResultMerger {

    public List<MergedHit> merge(List<RetrieveHit> semantic, List<RetrieveHit> graph, ScoringConfig cfg) {
        // 1) union + dedupe (same HP from both paths → matchedVia contains both)
        // 2) confidenceScore = clamp(w1 * vectorScore + w2 * graphScore + dualBoost?)
        // 3) drop below cfg.minScore(); sort by score desc; limit topK
        return List.of();
    }
}
```

Formatting: standard Spring/Java conventions via the project toolchain; no new formatter config unless asked.

---

## Testing Strategy

| Level | What | Where / how |
|-------|------|-------------|
| Unit | `ResultMerger` scoring, dedupe, threshold, top-k | `src/test/java/.../merge/` |
| Unit / slice | Query-interpret parsing of structured output (stub LLM where possible) | `.../adk/` |
| Integration | `POST /ask` SSE sequence with **mocked** Graph RAG clients | `.../chat/` + MockWebServer or `@MockBean` |
| Contract | Retrieve client deserialization matches [retrieve-api.md](contracts/retrieve-api.md) | Client tests with fixture JSON |
| Smoke | `SageApplicationTests` context load | existing |
| Eval (F8) | Golden questions via Promptfoo against live or stubbed stack | eval config calling `POST /ask` |

**Expectations:**

- Fail-soft: retrieve `5xx` → empty hits for that path; ask still completes (gap card if both empty) — assert this in tests.
- Pre-SSE bad/missing `query` → HTTP `400` shared Error schema ([architecture.md §12](architecture.md#12-inter-service-api-contracts)).
- Mid-stream failure → SSE `error` then close **without** `done`.
- Do not require live Neo4j/Orion for default `./mvnw test`.
- Eval asserts: no hallucinated team/person names when hits empty; `gapFlag=true` for out-of-domain; P95 latency target set after D1 hardware check (indicative dev bar in architecture: &lt; 10 s).

---

## Boundaries

### Always

- Follow [contracts/ask-api.md](contracts/ask-api.md) and [contracts/retrieve-api.md](contracts/retrieve-api.md) for public and downstream HTTP.
- Run `./mvnw test` before treating a slice as done.
- Propagate `correlationId` on ask → Graph RAG calls and logs.
- Fail-soft retrieve failures; never fall back to live Orion from Java.
- Keep Knowledge Card field set aligned with [architecture.md §11](architecture.md#11-knowledge-card-contract).
- Prefer extractive `summary` / `passage` from hits over LLM paraphrase of org facts.

### Ask first

- Changing SSE event sequence, Knowledge Card fields, or retrieve request/response shapes.
- Changing scoring formula beyond env-tuned weights already in `application.yml` (approved D2).
- Adding Maven dependencies (especially anything Spring AI / Neo4j / Orion).
- Expanding POC scope (auth, web UI, multi-turn memory, LoopAgent).
- Changing approved D1–D3 defaults without updating this SPEC (and noting the reason in architecture if system-wide).

### Never

- Call Orion or Neo4j from this Java service at ask-time (or add clients that do).
- Invent retrieve hit fields not in the retrieve contract / sister `api_contracts.py`.
- Commit secrets, API keys, or credentials.
- Add Spring AI on the ask path.
- Fabricate teams, people, or documents when retrieval is empty.
- Start Neo4j docker-compose from this repo.

---

## Public behavior (acceptance)

### `POST /ask`

- Request: `{ "query": "<non-blank string>" }`.
- Response: `Content-Type: text/event-stream`.
- SSE sequence (success):  
  `status` Interpreting → `status` Identified (+ `problemStatement`, `techNeeded`) → `status` Searching → `status` Ranking → `result` (Knowledge Card) → `done`.
- Knowledge Card includes: `query`, `problemStatement`, `techNeeded`, `directAnswer`, `results[]`, `gapFlag`, `gapMessage`.
- Each result includes rank, `confidenceScore`, `matchedVia`, team, title, category, `solvedBy`, summary, `documentLink`, `evidenceDetail`, `sourceAttribution`.
- Empty merged results → `gapFlag: true` and a candidate Hard Problem message; no fabricated experts.

### `GET /health`

- Process up → `200` with `status: "ok" | "degraded"` and Graph RAG reachability flags (`semanticServiceReachable`, `graphServiceReachable`).

Full tables: [contracts/ask-api.md](contracts/ask-api.md).

---

## Contract source of truth

| Contract | Documented SoT | Runtime SoT |
|----------|----------------|-------------|
| Sage ask / health | [contracts/ask-api.md](contracts/ask-api.md) | Spring controllers in this repo |
| Graph RAG retrieve / health | [contracts/retrieve-api.md](contracts/retrieve-api.md) | Sister `api_contracts.py` |
| Application HTTP / SSE **error** shape | [architecture.md §12](architecture.md#12-inter-service-api-contracts) | Both services |

Rules:

1. Java never invents retrieve response fields.
2. Mixed casing is intentional: camelCase domain fields (`problemStatement`, `vectorScore`) and snake_case transport knobs (`top_k`, `doc_id`).
3. IDs (`doc_id`, `personId`, `teamId`) are always strings.
4. Empty retrieval → `200` + `hits: []` (not an error). Java fail-softs retrieve `5xx` to empty hits.
5. If narrative docs and contract files disagree on success field shapes, **contract files + `api_contracts.py` win**.

---

## Ask pipeline (locked)

```text
POST /ask
  → QueryInterpret (problemStatement + techNeeded[])
  → ParallelAgent
       → SemanticSearchAgent  → POST /retrieve/semantic
       → GraphTraversalAgent  → POST /retrieve/graph
  → ResultMerger (dedupe + confidenceScore + top-k)
  → KnowledgeCardSynth
  → SSE result + done
```

Detail: [architecture.md §9](architecture.md#9-ask-time-request-lifecycle), [google-java-adk-usage.md](google-java-adk-usage.md).

---

## Success Criteria

This Java POC SPEC is **done** when all of the following are true:

1. `POST /ask` streams the contracted SSE sequence and a valid Knowledge Card for a seeded happy-path query.
2. Parallel retrieve is exercised via ADK `ParallelAgent` (both clients called for a normal ask).
3. `ResultMerger` is deterministic, unit-tested, and respects configured weights / min-score / top-k.
4. Gap path: no hits → `gapFlag: true`, no invented people/teams.
5. Retrieve `5xx` or unreachable Graph RAG fail-softs; ask still returns a card (typically gap).
6. `GET /health` reports process health and Graph RAG reachability.
7. `./mvnw test` passes without requiring live Neo4j/Orion.
8. F8: a small Promptfoo (or equivalent) golden set can call `POST /ask` and record routing / gap behavior.
9. No Orion/Neo4j clients exist in this codebase; Spring AI is not on the ask path.
10. Docs that humans/agents need (`SPEC`, contracts, README quick start) match shipped behavior.

**POC metrics bar** (product; measured via F8 + demo):

| Metric | Rough target |
|--------|----------------|
| Retrieval / routing quality | ~80% correct source/team in top-k on golden set |
| Answer quality | ≥ 4/5 human avg (correct, useful, honest when unknown) |
| Latency | Set after D1; indicative local-model P95 &lt; 10 s |

---

## Locked decisions (do not reopen casually)

From [architecture.md](architecture.md) / [spec-coverage-map.md](spec-coverage-map.md):

| Decision | Choice |
|----------|--------|
| Service split | Java ask/SSE vs Python Neo4j + retrieve |
| DB | Neo4j-only (graph + vector index) — owned by sister service |
| Ask path stack | Spring Boot shell + Google ADK; **not** Spring AI |
| Agent shape | Shallow `SequentialAgent` + `ParallelAgent`; single-turn; no `MemoryService` / `LoopAgent` |
| Client | Postman/curl SSE; web UI deferred |
| Auth / SSO / RBAC | Out of POC |
| Orion at ask-time | Never |

---

## Open Questions

| ID | Question | Blocks | Suggested default until confirmed |
|----|----------|--------|-----------------------------------|
| **D1** | Final Ollama model + hardware for latency bar | Latency SLA, demos | **Approved for POC:** `llama3.2:3b` via `SAGE_LLM_MODEL`; revisit if hardware forces a change |
| **D2** | Scoring weights, dual boost, min threshold | F3 values | **Approved for POC:** `application.yml` scoring block (`w1=0.6`, `w2=0.4`, dual boost `0.1`, min `0.60`) |
| **D3** | Top-N result count | Card size + retrieve `top_k` | **Approved for POC:** `5` |
| **D4–D6** | `cycleId`, Orion key validity, reseed endpoint | **Python ingest only** | Out of this Java SPEC |

---

## Related docs (read order for implementers)

1. This `SPEC.md` (acceptance + boundaries for Java work)
2. [architecture.md](architecture.md) (lifecycle, Knowledge Card, errors)
3. [contracts/ask-api.md](contracts/ask-api.md) + [contracts/retrieve-api.md](contracts/retrieve-api.md)
4. [google-java-adk-usage.md](google-java-adk-usage.md)
5. [feature-document.md](feature-document.md) (product F0–F8 detail)
6. [spec-coverage-map.md](spec-coverage-map.md) (Spring vs ADK vs Graph RAG checklists)

---

## Approved decisions (2026-07-22)

1. Feature scope for **implementation in sage-ai** = F1–F6 + F8; F0/F7 are sister-service dependencies.
2. D1–D3 use current `application.yml` values for the POC.
3. Endpoint path is `POST /ask` (contracts/architecture), not `/api/ask` (older wording in the feature doc).
4. Architecture locked decisions stay as written; this SPEC does not change them.

**Next:** planning — break this SPEC into an implementation plan and task list (`/plan` or `planning-and-task-breakdown`). Do not implement until the plan is reviewed.
