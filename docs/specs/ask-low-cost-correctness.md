# Spec: Ask low-cost correctness fixes

## Objective

Improve answer correctness and UX on `POST /ask` with the **smallest possible code changes** — no ADK rewire, no scoring redesign, no eval harness.

**User:** Engineer asking “who solved this?” via Postman/SSE.  
**Success:** Hybrid search still runs when tags are missing, faster retrieve when tags exist, route-first one-liner answers grounded in merge results only. (False Hard Problem on retrieve failure is deferred to T3.)

## In scope (cost-ordered)

| # | Change | Cost | Impact |
|---|--------|------|--------|
| 1 | Parallel semantic + graph when `techNeeded` non-empty | Tiny (~10 lines in `SageAskService`) | Latency; matches architecture intent |
| 2 | Route-first `directAnswer` from top merged result(s) | Tiny (small helper + ask wiring) | Matches product one-liner contract |
| 4 | Recover `techNeeded` from semantic hit metadata when empty | Small (helper + ask branch) | Restores graph path after interpret fallback |

## Out of scope (defer)

- **T3:** Retrieve failure → not a true knowledge gap (`degraded` flag / false Hard Problem) — cover later
- Graph-exact score floor / scoring weight redesign
- Technology vocabulary normalizer allowlist
- Wiring ADK `SageRoot` / LLM `KnowledgeCardSynth` on the live path
- Promptfoo / full golden eval harness
- Broad architecture.md rewrite (only touch ask-api / Knowledge Card fields we change)

## Tech stack

Existing: Java 21, Spring Boot, Graph RAG HTTP client, deterministic `ResultMerger`, Ollama via `QueryInterpreter`.

## Commands

```
./mvnw test
./mvnw test -Dtest=AskIntegrationTest,SageAskServiceTest,DirectAnswerFormatterTest,TechNeededRecoveryTest
./mvnw -DskipTests package
```

## Project structure

```
src/main/java/com/company/sage/chat/SageAskService.java   # orchestration
src/main/java/com/company/sage/clients/graphrag/          # HTTP retrieve client
src/main/java/com/company/sage/model/                     # RetrieveResponse, KnowledgeCard
src/test/java/com/company/sage/chat/                      # unit + AskIntegrationTest
```

## Code style

- Match existing records, constructor injection, AssertJ tests.
- Prefer a few-line private/helper over new packages unless shared by ≥2 callers.
- Evidence-only: never invent team/person names in `directAnswer`.

Example `directAnswer` shapes:

```text
Yes — 1 team has solved this: Payments Platform (SSRF-safe external image loader).
No internal prior art found.
```

## Testing strategy

- Unit: `DirectAnswerFormatter`, `TechNeededRecovery`.
- Integration: `AskIntegrationTest` — happy path SSE; true empty → `gapFlag=true` (current behavior until T3).
- No live Ollama required for these tests (mock interpreter / client).

## Boundaries

- **Always:** Fail-soft retrieve (no ask SSE `error` solely for Graph RAG 5xx); evidence-only card fields; keep Java free of Neo4j/Orion at ask-time.
- **Ask first:** Changing merge scoring.
- **Never:** Enable LLM card synth on `/ask` in this slice; delete ADK scaffolding; invent experts on empty hits.
- **Later (T3):** `degraded` on Knowledge Card; distinguish retrieve failure from true gap.

## Success criteria

1. Interpret returns `techNeeded=[]` but semantic hits carry `metadata.technologies` → graph retrieve is still called with those tags.
2. Non-empty `techNeeded` → semantic and graph HTTP calls overlap in time (`CompletableFuture`).
3. Non-empty merge → `directAnswer` is a route-first one-liner from team/title evidence, not a raw passage dump.
4. `./mvnw test` green for touched tests.

## Open questions (defaults if you approve as-is)

1. **Empty tags + empty semantic:** Skip graph call — **default: yes**.
2. **T3 `degraded` field:** Deferred; decide when T3 is scheduled.
