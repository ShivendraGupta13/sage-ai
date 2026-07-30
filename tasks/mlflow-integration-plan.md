# Implementation Plan: Sage AI x MLflow Experiment Tracking

## Overview

Integrate MLflow as an experiment-tracking and comparison layer for the Sage AI POC,
as specified in `docs/SPEC.md section MLflow Experiment Tracking` (lines 218-699).
The goal is to connect controlled Sage test executions to MLflow so that the team
can record configuration + metrics together and answer "which Sage configuration
performs better?" by comparing runs side-by-side.

**Scope: Two services only**
- Java service: sage-ai (Spring Boot, port 8080)
- RAG service: graph-rag-service (FastAPI, port 8001)

The integration is built as a Python evaluation harness (eval/sage_eval.py) that
calls POST /ask on the Java service from outside. Java measures timing internally
and exposes it via an optional SSE event. The RAG service latency is captured as
part of the Java retrieval timing. Python collects everything and logs to MLflow.
Java never imports the MLflow SDK. This satisfies SPEC acceptance criteria 15 and 16
(contract unchanged; MLflow downtime cannot fail a Sage request).

> Source of truth for all requirements: docs/SPEC.md section MLflow Experiment Tracking

---

## Architecture Decisions

- No MLflow SDK in Java or graph-rag-service. The harness is out-of-process.
  (SPEC Never: Fail a Sage user request because MLflow is unavailable)
- Timing exposed via optional SSE event. Java emits event: timing before done
  with e2e_ms, llm_ms, retrieval_ms (retrieval_ms covers graph-rag-service call time).
  Harness reads it without changing the /ask contract.
- Experiment name fixed: sage-ai-agent-evaluation (SPEC example, line 308).
- One run = one controlled test execution with one fixed configuration (SPEC line 313).
- Phase 1 only. CPU, GPU, VRAM, and quality scoring are out of scope.
- MLflow started via official Docker Compose (3 containers: MLflow Tracking Server +
  PostgreSQL backend store + RustFS S3-compatible artifact store).
  Source: https://mlflow.org/docs/latest/self-hosting/#docker-compose
- event: timing is an **official, documented part of the /ask SSE contract** (Option A).
  Any client may rely on it. It is always emitted (not a feature flag).
- LLM model for POC: **llama3.1:8b** via Ollama. Token counts available via Ollama API.
- eval/ lives inside sage-ai/ (same repo, same team).

---

## Services in Scope

| Service | Language | Port | Role in integration |
|---------|----------|------|---------------------|
| sage-ai | Java 21 / Spring Boot | 8080 | Receives /ask, calls graph-rag-service, calls LLM, emits SSE timing |
| graph-rag-service | Python / FastAPI | 8001 | Retrieves context from Neo4j; latency captured via Java timer |
| eval/sage_eval.py | Python (harness) | — | Calls sage-ai, measures wall-clock, logs all metrics to MLflow |

> **Not in scope:** Node.js service, any third service, production infra, model training.

---

## Dependency Graph

```
T1: MLflow server + eval environment
        |
        v
T2: Harness skeleton + parameter logging
        |
        v
T3: Wall-clock latency + success/failure + gap rate + P50/P95
        |                                        |
        v                                        v
T4: Java timing SSE event              T5: Artifact logging
 (llm_ms, retrieval_ms from sage-ai)
        |
        v
T6: Token metrics from LangChain4j (sage-ai)
        |
        v
T7: Run comparison smoke test (end-to-end validation)
```

---

## Phase 1 — Slice 1: A complete run appears in MLflow for a Sage test execution

Delivers: SPEC acceptance criteria 1, 2, 3, 4, 5, 15, 16

---

## Task 1: MLflow stack started via Docker Compose and eval environment ready

**Description:** Stand up MLflow using the official Docker Compose setup.
Create eval/ inside sage-ai/ with a requirements.txt and verify the Python environment
can connect to the running MLflow server. Document the startup steps in README.md.

Ref: https://mlflow.org/docs/latest/self-hosting/#docker-compose

**Stack:** 3 containers — MLflow Tracking Server, PostgreSQL (backend store), RustFS (artifact store).

**Acceptance criteria:**
- [ ] docker compose up -d starts all 3 containers healthy (SPEC criterion 1)
- [ ] http://localhost:5000 opens the MLflow UI with no errors
- [ ] python -c "import mlflow; mlflow.set_tracking_uri('http://localhost:5000'); print('OK')" exits 0
- [ ] sage-ai/eval/requirements.txt created with mlflow>=3.x, requests, numpy
- [ ] Docker Compose startup steps documented in sage-ai/README.md
- [ ] docker compose down (without -v) stops all containers; data survives restart

**Verification:**
- [ ] Manual: run docker compose up -d, open UI, see empty Experiments page
- [ ] Manual: run Python one-liner against http://localhost:5000, confirm exit 0
- [ ] Manual: docker compose down then up again, confirm previous runs/experiments still present

**Dependencies:** Docker Desktop (or Docker Engine) must be installed and running

**Files likely touched:**
- sage-ai/mlflow/docker-compose.yml [NEW — PostgreSQL + RustFS + MLflow server]
- sage-ai/mlflow/.env.example [NEW]
- sage-ai/eval/requirements.txt [NEW]
- sage-ai/README.md [MODIFY — add Docker Compose MLflow section]

**Estimated scope:** XS

---

## Task 2: Harness skeleton with parameter logging

**Description:** Create eval/sage_eval.py with the MLflow run lifecycle
(start_run / end_run) and parameter logging. At this stage the harness does
not yet call /ask — it just creates a run with all required SPEC parameters and
verifies the run appears in the UI with the correct fields.

**Acceptance criteria:**
- [ ] python eval/sage_eval.py creates a run in experiment sage-ai-agent-evaluation (SPEC criterion 2)
- [ ] Run records llm_model (SPEC criterion 3, parameter table Required)
- [ ] Run records git_commit mapping to the sage-ai application version (SPEC criterion 4)
- [ ] Run records retrieval_top_k, retrieval_min_score, scoring_w1, scoring_w2, scoring_dual_boost (SPEC criterion 5)
- [ ] Run records hardware, prompt_version (SPEC parameter table Required)
- [ ] No secrets or credentials are logged (SPEC Never: Store secrets or credentials)
- [ ] Script exits cleanly when MLflow is unreachable; logs a warning; sage-ai /ask is unaffected (SPEC criterion 16)

**Verification:**
- [ ] Run script, open MLflow UI, confirm run exists with all 8+ params visible
- [ ] Stop MLflow server, re-run script — script exits with warning; /ask endpoint is unaffected

**Dependencies:** Task 1

**Files likely touched:**
- sage-ai/eval/sage_eval.py [NEW]

**Estimated scope:** S (1 file)

---

## Checkpoint: After Tasks 1-2 (Slice 1 complete)

- [ ] Docker Compose stack starts reliably (MLflow healthy)
- [ ] A run appears in UI with all required parameters logged
- [ ] SPEC acceptance criteria 1, 2, 3, 4, 5, 15, 16 satisfied
- [ ] Team review before proceeding to Phase 2

---

## Phase 2 — Slice 2: Latency, reliability, and gap metrics visible per run

Delivers: SPEC acceptance criteria 6, 7, 8, 9, 10, 11, 13, 14

---

## Task 3: Wall-clock metrics — latency, success, failure, gap, P50/P95

**Description:** Extend sage_eval.py to call POST /ask on sage-ai for each question
in the evaluation dataset. Measure wall-clock latency per request. Parse gapFlag
from the SSE result event. Track successes and failures. After all requests,
calculate and log aggregate metrics.

**Acceptance criteria:**
- [ ] e2e_latency_s logged as a per-step metric, one value per request (SPEC criterion 6)
- [ ] success_rate logged (SPEC criterion 9)
- [ ] failed_count logged (SPEC criterion 9)
- [ ] gap_rate logged for the evaluation dataset (SPEC criterion 10)
- [ ] p50_latency_s and p95_latency_s logged when >= 5 requests in the run (SPEC criterion 11)
- [ ] total_requests logged
- [ ] SSE parser handles mid-stream error event gracefully — counts as failure, does not crash harness

**Verification:**
- [ ] Run harness with sage-ai + graph-rag-service both running; open MLflow UI — all 7 metrics appear
- [ ] Kill sage-ai mid-run; confirm harness counts failures and logs failed_count > 0
- [ ] Run with 1 question; confirm P50/P95 are skipped

**Dependencies:** Task 2

**Files likely touched:**
- sage-ai/eval/sage_eval.py [MODIFY]
- sage-ai/eval/questions.json [NEW — evaluation dataset]

**Estimated scope:** M (2 files)

---

## Task 4: Java timing SSE event (llm_ms, retrieval_ms)

**Description:** Add System.nanoTime() timers at three points inside the sage-ai
ask pipeline: overall request start, graph-rag-service call start/end (retrieval),
and LLM call start/end. Emit an optional event: timing SSE event before event: done
with the measured values. Update sage_eval.py to parse this event and log
llm_latency_s and retrieval_latency_s. The timing event must be fail-soft.

**Decision (Q2 — resolved):** `event: timing` is an **official part of the `/ask` SSE contract** (Option A).
It is always emitted and can be relied upon by any client.

**SSE contract order after T4:**
```
event: status  → event: status → ... → event: result → event: timing → event: done
```

**Timing payload shape:**
```json
{ "e2e_ms": 3200, "llm_ms": 2100, "retrieval_ms": 800 }
```

**Acceptance criteria:**
- [ ] POST /ask SSE stream contains event: timing with e2e_ms, llm_ms, retrieval_ms before done
- [ ] Timing event is silently absent if instrumentation fails (fail-soft — SPEC criterion 16)
- [ ] ./mvnw test passes with no regression
- [ ] llm_latency_s logged to MLflow run (SPEC criterion 7)
- [ ] retrieval_latency_s logged to MLflow run (SPEC criterion 8, covers graph-rag-service call time)

**Verification:**
- [ ] curl -N -X POST http://localhost:8080/ask confirms event: timing present in output
- [ ] ./mvnw test — no failures
- [ ] MLflow run shows both llm_latency_s and retrieval_latency_s

**Dependencies:** Task 3

**Where to add timers (Q1 — resolved):**
- `SpringChatController.java` handles `POST /ask` but delegates to `SageAskService.processAsk()`.
- Timers go **inside `SageAskService`** at the graph-rag-service call and LLM call.
- The `event: timing` SSE event is emitted from `SageAskService` before it signals done.

**Files likely touched:**
- `sage-ai/src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY — add timers + emit timing event]
- `sage-ai/eval/sage_eval.py` [MODIFY — parse timing event]

**Estimated scope:** M (2 files)

---

## Task 5: Artifact logging

**Description:** At the end of each run log two artifacts to MLflow:
(1) eval_results.json with per-request raw data; (2) sage_config_snapshot.json
with the configuration params used in the run. No secrets included.

**Acceptance criteria:**
- [ ] eval_results.json artifact appears on each MLflow run, downloadable from UI
- [ ] eval_results.json contains one entry per request: question, latency_s, success, gap_flag
- [ ] sage_config_snapshot.json contains the params logged in the same run
- [ ] No credentials, tokens, or application logs in artifacts (SPEC Never)

**Verification:**
- [ ] MLflow UI -> run -> Artifacts tab — both files present and downloadable
- [ ] Open eval_results.json — confirm structure matches spec, no secrets

**Dependencies:** Task 3

**Files likely touched:**
- sage-ai/eval/sage_eval.py [MODIFY — add log_artifact calls]

**Estimated scope:** XS (1 file)

---

## Checkpoint: After Tasks 3-5 (Slice 2 complete)

- [ ] Full run: params (T2) + latency/reliability metrics (T3) + Java internal breakdown (T4) + artifacts (T5)
- [ ] ./mvnw test still passes
- [ ] SPEC acceptance criteria 6-11, 13, 14 satisfied
- [ ] Team review before proceeding to Phase 3

---

## Phase 3 — Slice 3: Token metrics and run comparison validated

Delivers: SPEC acceptance criteria 12, 13, 14

---

## Task 6: Token metrics from LangChain4j Ollama response (sage-ai)

**Description:** Extract prompt_eval_count (input tokens) and eval_count (output
tokens) from the LangChain4j Ollama response inside sage-ai. Derive
tokens_per_sec = eval_count / (llm_ms / 1000). Extend the event: timing payload.
All three are conditional — if Ollama does not return token counts, fields are absent,
no exception thrown.

**Acceptance criteria:**
- [ ] input_tokens, output_tokens, tokens_per_sec appear in MLflow run when Ollama exposes token counts
- [ ] Missing token counts from Ollama handled without exception (graceful degradation)
- [ ] ./mvnw test still passes
- [ ] SPEC acceptance criterion 12 satisfied

**Decision (Q3 — resolved):** Model is **llama3.1:8b** via Ollama.
Ollama exposes `prompt_eval_count` (input tokens) and `eval_count` (output tokens)
in the LangChain4j response — token metrics are expected to be available.

**Verification:**
- [ ] Run harness with llama3.1:8b via sage-ai — confirm token metrics appear in MLflow UI
- [ ] Simulate missing token counts — confirm harness does not crash

**Dependencies:** Task 4

**Files likely touched:**
- `sage-ai/src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY — extract token counts from LangChain4j response]
- `sage-ai/eval/sage_eval.py` [MODIFY — parse token fields]

**Estimated scope:** M (2 files)

---

## Task 7: Run comparison smoke test

**Description:** Execute two intentionally different runs back-to-back and
validate in the MLflow UI compare view that the parameter difference is visible
alongside the metric difference. End-to-end validation of SPEC criteria 13 and 14.

**Acceptance criteria:**
- [ ] Two runs with different retrieval_top_k values exist in sage-ai-agent-evaluation
- [ ] MLflow UI compare view shows param difference and metric difference side-by-side (SPEC criterion 13)
- [ ] A reviewer can identify from the UI alone which parameter changed between runs (SPEC criterion 14)
- [ ] All 16 SPEC Phase 1 acceptance criteria are verifiably met

**Verification:**
- [ ] MLflow UI -> Experiments -> sage-ai-agent-evaluation -> select two runs -> Compare -> confirm
- [ ] Walk through SPEC acceptance criteria 1-16 one by one and mark each as met

**Dependencies:** Tasks 1-6

**Files likely touched:**
- sage-ai/eval/sage_eval.py [MINOR MODIFY — add --top-k CLI arg for comparison runs]

**Estimated scope:** XS (1 file)

---

## Checkpoint: Phase 3 Complete — All Acceptance Criteria Met

- [ ] All 16 SPEC Phase 1 acceptance criteria satisfied and verified
- [ ] ./mvnw test passes (no Java regression)
- [ ] Two runs comparable in MLflow UI with clear param + metric diff
- [ ] No secrets, credentials, or application logs in MLflow
- [ ] README.md documents how to start MLflow and run the harness
- [ ] Team review and sign-off

---

## Parallelization Opportunities

| Tasks | Safe? | Note |
|-------|-------|------|
| T1 + T2 | Yes | Both are setup/foundation, no service dependency |
| T3 + T5 | Yes | Artifact logging is independent of timing event |
| T3 and T4 | Needs coordination | Define the timing event JSON shape first, then parallelize |
| T6 + T7 | Sequential | T7 validates T6 |

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Docker Desktop not installed on dev machine | High | Install Docker Desktop before T1. Confirm with team before starting. |
| Port 5000 already in use | Med | Edit .env to change MLFLOW_PORT before docker compose up |
| AskController.java not yet implemented when T4 begins | High | T1-T3 and T5 can proceed without it. T4 can only start once the business logic exists. |
| Ollama does not expose token counts for chosen model | Med | T6 is conditional by design; harness skips gracefully. |
| SSE line-by-line parsing is fragile | Med | Write a dedicated SSE parser with its own unit test in eval/test_sse_parser.py |
| git rev-parse fails in detached HEAD state | Low | Wrap in try/except; fall back to "unknown" |
| MLflow data lost if docker compose down -v is run | Low | Always use docker compose down (without -v) to preserve volumes |

---

## Decisions Log

| Q4 | MLflow backend: SQLite or PostgreSQL + RustFS? | **PostgreSQL + RustFS** — official 3-container Docker Compose setup. |
| Q5 | Live Prompt Telemetry: Approach B (OpenTelemetry Java Agent)? | **Approach B selected.** Visual waterfall span trees in MLflow Traces tab without code changes. |
| Q6 | Custom Span Attributes Enrichment? | **Phase 5 added.** Add OpenTelemetry API custom attributes for GenAI & RAG metadata. |

---

## Phase 5 — Custom Span Attributes & GenAI Metadata Enrichment

Enrich visual waterfall traces in MLflow's **Traces** tab with domain-specific attributes:
- `gen_ai.system`: `"ollama"`
- `gen_ai.request.model`: `"llama3.2:latest"`
- `gen_ai.usage.input_tokens` / `gen_ai.usage.output_tokens` / `gen_ai.usage.total_tokens`
- `rag.query`, `rag.num_results`, `rag.gap_flag`
- `sage.correlation_id`, `sage.problem_statement`, `sage.tech_needed`

---

## Task 11: OpenTelemetry API Dependency & Span Attribute Helper

**Description:** Add `io.opentelemetry:opentelemetry-api` to `pom.xml`. Create `OtelSpanHelper.java` to safely set attributes on `Span.current()` in a fail-soft manner.

**Acceptance criteria:**
- [ ] `opentelemetry-api` added to `pom.xml`
- [ ] `OtelSpanHelper.java` created with static methods for GenAI & RAG attributes
- [ ] `./mvnw test` passes cleanly

**Files likely touched:**
- `pom.xml` [MODIFY]
- `src/main/java/com/company/sage/util/OtelSpanHelper.java` [NEW]

**Estimated scope:** S

---

## Task 12: Instrument `SageAskService` & `QueryInterpreter`

**Description:** Update `QueryInterpreter` and `SageAskService` to record model metadata, token counts, correlation IDs, and RAG results onto the active OpenTelemetry span.

**Acceptance criteria:**
- [ ] `QueryInterpreter` records `gen_ai.system` and `gen_ai.request.model`
- [ ] `SageAskService` records `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `rag.gap_flag`, `sage.correlation_id`
- [ ] `./mvnw test` passes with zero failures

**Files likely touched:**
- `src/main/java/com/company/sage/chat/QueryInterpreter.java` [MODIFY]
- `src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY]

**Estimated scope:** M

---

## Task 13: End-to-End Verification & MLflow Attribute Inspection

**Description:** Send a live `POST /ask` prompt, inspect the trace in MLflow UI (`http://localhost:5000`), and verify that the Attributes panel displays all GenAI and RAG metadata.

**Acceptance criteria:**
- [ ] MLflow UI **Traces** tab Attributes panel displays `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `rag.gap_flag`
- [ ] README.md updated with Phase 5 verification instructions

**Files likely touched:**
- `README.md` [MODIFY]
- `tasks/mlflow-integration-plan.md` [MODIFY]
- `tasks/mlflow-integration-todo.md` [MODIFY]

**Estimated scope:** M

---

## Checkpoint 5: Phase 5 Complete
- [ ] OpenTelemetry span attributes visible in MLflow UI
- [ ] Fail-soft verification passed
- [ ] `./mvnw test` green (all 48 tests pass)

---

## Phase 6 — MLflow Standard UI Semantic Key Mapping (Inputs, Outputs, Session, User, Git Version)

Populate MLflow's Summary tab (`Inputs`, `Outputs`), Top-level Cards (Request, Response, Session, User, Version, Git Commit), and Table Previews by mapping standard OpenTelemetry semantic keys:
- `mlflow.trace.inputs`, `input.value`: `rawQuery`
- `mlflow.trace.outputs`, `output.value`: `directAnswer`
- `session.id`: `correlationId`
- `user.id`: `"developer"`
- `service.version`: `"0.0.1-SNAPSHOT"`
- `git.commit`: `"35b30b8"`

---

## Task 14: Instrument Root Span with MLflow Trace Inputs & Outputs

**Description:** Update `SageAskService.java` to set `mlflow.trace.inputs`, `input.value`, `mlflow.trace.outputs`, and `output.value` on the active root span.

**Acceptance criteria:**
- [ ] `mlflow.trace.inputs` and `input.value` set to prompt query
- [ ] `mlflow.trace.outputs` and `output.value` set to direct answer / knowledge card payload
- [ ] MLflow UI **Summary** tab `Inputs` and `Outputs` sections expand with formatted text
- [ ] `./mvnw test` passes cleanly

**Files likely touched:**
- `src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY]

**Estimated scope:** S

---

## Task 15: Instrument Session, User, and Git Version Attributes

**Description:** Update `SageAskService.java` to record `session.id`, `user.id`, `service.version`, and `git.commit` on the span.

**Acceptance criteria:**
- [ ] `session.id` set to `correlationId`
- [ ] `user.id` set to `"developer"`
- [ ] `service.version` set to `"0.0.1-SNAPSHOT"`
- [ ] `git.commit` set to active commit SHA
- [ ] MLflow UI top cards (Session, User, Version, Git Commit) populated
- [ ] `./mvnw test` passes cleanly

**Files likely touched:**
- `src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY]

**Estimated scope:** S

---

## Task 16: Verification & Documentation Update

**Description:** Send a live `POST /ask` prompt, inspect the trace in MLflow UI (`http://localhost:5000`), and verify that Summary tab `Inputs`/`Outputs` and top cards are fully populated.

**Acceptance criteria:**
- [ ] MLflow UI **Traces** tab Summary section (`Inputs`/`Outputs`) and top cards display full data
- [ ] README.md updated with Phase 6 verification instructions

**Files likely touched:**
- `README.md` [MODIFY]
- `tasks/mlflow-integration-plan.md` [MODIFY]
- `tasks/mlflow-integration-todo.md` [MODIFY]

**Estimated scope:** M

---

## Checkpoint 6: Phase 6 Complete
- [ ] Summary tab `Inputs` and `Outputs` populated in MLflow UI
- [ ] Top cards (Request, Response, Session, User, Version) fully populated
- [ ] `./mvnw test` green (all 48 tests pass)


| ID | Question | Decision |
|----|----------|----------|
| Q1 | Which Java class handles `POST /ask`? | `SpringChatController.java` (delegates to `SageAskService.processAsk()`). Timers go in `SageAskService`. |
| Q2 | Should `event: timing` be official or eval-only? | **Option A — Official.** Always emitted, documented, any client may rely on it. |
| Q3 | Which LLM model for the POC? | **llama3.1:8b** via Ollama. Token counts available. |
| Q4 | MLflow backend: SQLite or PostgreSQL + RustFS? | **PostgreSQL + RustFS** — official 3-container Docker Compose setup. |
| Q5 | Live Prompt Telemetry: Approach B (OpenTelemetry Java Agent)? | **Approach B selected.** Visual waterfall span trees in MLflow Traces tab without code changes. |
| Q6 | Custom Span Attributes Enrichment? | **Phase 5 added.** Add OpenTelemetry API custom attributes for GenAI & RAG metadata. |

---

## Phase 5 — Custom Span Attributes & GenAI Metadata Enrichment

Enrich visual waterfall traces in MLflow's **Traces** tab with domain-specific attributes:
- `gen_ai.system`: `"ollama"`
- `gen_ai.request.model`: `"llama3.2:latest"`
- `gen_ai.usage.input_tokens` / `gen_ai.usage.output_tokens` / `gen_ai.usage.total_tokens`
- `rag.query`, `rag.num_results`, `rag.gap_flag`
- `sage.correlation_id`, `sage.problem_statement`, `sage.tech_needed`

---

## Task 11: OpenTelemetry API Dependency & Span Attribute Helper

**Description:** Add `io.opentelemetry:opentelemetry-api` to `pom.xml`. Create `OtelSpanHelper.java` to safely set attributes on `Span.current()` in a fail-soft manner.

**Acceptance criteria:**
- [ ] `opentelemetry-api` added to `pom.xml`
- [ ] `OtelSpanHelper.java` created with static methods for GenAI & RAG attributes
- [ ] `./mvnw test` passes cleanly

**Files likely touched:**
- `pom.xml` [MODIFY]
- `src/main/java/com/company/sage/util/OtelSpanHelper.java` [NEW]

**Estimated scope:** S

---

## Task 12: Instrument `SageAskService` & `QueryInterpreter`

**Description:** Update `QueryInterpreter` and `SageAskService` to record model metadata, token counts, correlation IDs, and RAG results onto the active OpenTelemetry span.

**Acceptance criteria:**
- [ ] `QueryInterpreter` records `gen_ai.system` and `gen_ai.request.model`
- [ ] `SageAskService` records `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `rag.gap_flag`, `sage.correlation_id`
- [ ] `./mvnw test` passes with zero failures

**Files likely touched:**
- `src/main/java/com/company/sage/chat/QueryInterpreter.java` [MODIFY]
- `src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY]

**Estimated scope:** M

---

## Task 13: End-to-End Verification & MLflow Attribute Inspection

**Description:** Send a live `POST /ask` prompt, inspect the trace in MLflow UI (`http://localhost:5000`), and verify that the Attributes panel displays all GenAI and RAG metadata.

**Acceptance criteria:**
- [ ] MLflow UI **Traces** tab Attributes panel displays `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `rag.gap_flag`
- [ ] README.md updated with Phase 5 verification instructions

**Files likely touched:**
- `README.md` [MODIFY]
- `tasks/mlflow-integration-plan.md` [MODIFY]
- `tasks/mlflow-integration-todo.md` [MODIFY]

**Estimated scope:** M

---

## Checkpoint 5: Phase 5 Complete
- [ ] OpenTelemetry span attributes visible in MLflow UI
- [ ] Fail-soft verification passed
- [ ] `./mvnw test` green (all 48 tests pass)

---

## Phase 6 — MLflow Standard UI Semantic Key Mapping (Inputs, Outputs, Session, User, Git Version)

Populate MLflow's Summary tab (`Inputs`, `Outputs`), Top-level Cards (Request, Response, Session, User, Version, Git Commit), and Table Previews by mapping standard OpenTelemetry semantic keys:
- `mlflow.trace.inputs`, `input.value`: `rawQuery`
- `mlflow.trace.outputs`, `output.value`: `directAnswer`
- `session.id`: `correlationId`
- `user.id`: `"developer"`
- `service.version`: `"0.0.1-SNAPSHOT"`
- `git.commit`: `"35b30b8"`

---

## Task 14: Instrument Root Span with MLflow Trace Inputs & Outputs

**Description:** Update `SageAskService.java` to set `mlflow.trace.inputs`, `input.value`, `mlflow.trace.outputs`, and `output.value` on the active root span.

**Acceptance criteria:**
- [ ] `mlflow.trace.inputs` and `input.value` set to prompt query
- [ ] `mlflow.trace.outputs` and `output.value` set to direct answer / knowledge card payload
- [ ] MLflow UI **Summary** tab `Inputs` and `Outputs` sections expand with formatted text
- [ ] `./mvnw test` passes cleanly

**Files likely touched:**
- `src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY]

**Estimated scope:** S

---

## Task 15: Instrument Session, User, and Git Version Attributes

**Description:** Update `SageAskService.java` to record `session.id`, `user.id`, `service.version`, and `git.commit` on the span.

**Acceptance criteria:**
- [ ] `session.id` set to `correlationId`
- [ ] `user.id` set to `"developer"`
- [ ] `service.version` set to `"0.0.1-SNAPSHOT"`
- [ ] `git.commit` set to active commit SHA
- [ ] MLflow UI top cards (Session, User, Version, Git Commit) populated
- [ ] `./mvnw test` passes cleanly

**Files likely touched:**
- `src/main/java/com/company/sage/chat/SageAskService.java` [MODIFY]

**Estimated scope:** S

---

## Task 16: Verification & Documentation Update

**Description:** Send a live `POST /ask` prompt, inspect the trace in MLflow UI (`http://localhost:5000`), and verify that Summary tab `Inputs`/`Outputs` and top cards are fully populated.

**Acceptance criteria:**
- [ ] MLflow UI **Traces** tab Summary section (`Inputs`/`Outputs`) and top cards display full data
- [ ] README.md updated with Phase 6 verification instructions

**Files likely touched:**
- `README.md` [MODIFY]
- `tasks/mlflow-integration-plan.md` [MODIFY]
- `tasks/mlflow-integration-todo.md` [MODIFY]

**Estimated scope:** M

---

## Checkpoint 6: Phase 6 Complete
- [ ] Summary tab `Inputs` and `Outputs` populated in MLflow UI
- [ ] Top cards (Request, Response, Session, User, Version) fully populated
- [ ] `./mvnw test` green (all 48 tests pass)


