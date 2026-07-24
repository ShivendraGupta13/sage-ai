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
- MLflow started via official Docker Compose (PostgreSQL backend + RustFS artifact store).
  Source: https://mlflow.org/docs/latest/self-hosting/#docker-compose
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

## Phase 1 - Slice 1: A complete run appears in MLflow for a Sage test execution

Delivers: SPEC acceptance criteria 1, 2, 3, 4, 5, 15, 16

---

## Task 1: MLflow stack started via Docker Compose and eval environment ready

**Description:** Stand up MLflow using the official Docker Compose bundle from the
MLflow repository. This provisions three containers: MLflow Tracking Server,
PostgreSQL (backend store), and RustFS (S3-compatible artifact store). Create
eval/ inside sage-ai/ with a requirements.txt and verify the Python environment
can connect to the running MLflow server. Document the startup steps in README.md.

Ref: https://mlflow.org/docs/latest/self-hosting/#docker-compose

**Setup steps (to be documented in README.md):**
```bash
git clone https://github.com/mlflow/mlflow.git /tmp/mlflow-docker
cd /tmp/mlflow-docker/docker-compose
cp .env.dev.example .env          # review and adjust ports/passwords if needed
docker compose up -d              # starts mlflow + postgres + rustfs
# UI available at http://localhost:5000
```

**Acceptance criteria:**
- [ ] docker compose up -d completes without error (all 3 containers healthy) (SPEC criterion 1)
- [ ] http://localhost:5000 opens the MLflow UI with no errors
- [ ] python -c "import mlflow; mlflow.set_tracking_uri('http://localhost:5000'); print('OK')" exits 0
- [ ] sage-ai/eval/requirements.txt created with mlflow>=2.15.0, requests, numpy
- [ ] Docker Compose startup steps documented in sage-ai/README.md
- [ ] docker compose down cleanly stops all containers (no data loss between restarts)

**Verification:**
- [ ] Manual: run docker compose up -d, open UI, see empty Experiments page
- [ ] Manual: run Python one-liner against http://localhost:5000, confirm exit 0
- [ ] Manual: docker compose down then up again, confirm previous runs/experiments still present

**Dependencies:** Docker Desktop (or Docker Engine) must be installed and running

**Files likely touched:**
- sage-ai/eval/requirements.txt [NEW]
- sage-ai/README.md [MODIFY - add Docker Compose MLflow section]

**Estimated scope:** XS (2 files — Docker Compose files live in the cloned mlflow repo, not in sage-ai)

---

## Task 2: Harness skeleton with parameter logging

**Description:** Create eval/sage_eval.py with the MLflow run lifecycle
(start_run / end_run) and parameter logging. At this stage the harness does
not yet call /ask - it just creates a run with all required SPEC parameters and
verifies the run appears in the UI with the correct fields. This validates the
connection before adding metric collection.

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
- [ ] Stop MLflow server, re-run script - script exits with warning; /ask endpoint is unaffected

**Dependencies:** Task 1

**Files likely touched:**
- sage-ai/eval/sage_eval.py [NEW]

**Estimated scope:** S (1 file)

---

## Checkpoint: After Tasks 1-2 (Slice 1 complete)

- [ ] Docker Compose stack starts reliably (mlflow + postgres + rustfs all healthy)
- [ ] A run appears in UI with all required parameters logged
- [ ] SPEC acceptance criteria 1, 2, 3, 4, 5, 15, 16 satisfied
- [ ] Team review before proceeding to Phase 2

---

## Phase 2 - Slice 2: Latency, reliability, and gap metrics visible per run

Delivers: SPEC acceptance criteria 6, 7, 8, 9, 10, 11, 13, 14

---

## Task 3: Wall-clock metrics - latency, success, failure, gap, P50/P95

**Description:** Extend sage_eval.py to call POST /ask on sage-ai for each question
in the evaluation dataset. Measure wall-clock latency per request. Parse gapFlag
from the SSE result event. Track successes and failures. After all requests,
calculate and log aggregate metrics. At this point the harness uses wall-clock
timing only (Java instrumentation comes in Task 4).

**Acceptance criteria:**
- [ ] e2e_latency_s logged as a per-step metric, one value per request (SPEC criterion 6)
- [ ] success_rate logged (SPEC criterion 9)
- [ ] failed_count logged (SPEC criterion 9)
- [ ] gap_rate logged for the evaluation dataset (SPEC criterion 10)
- [ ] p50_latency_s and p95_latency_s logged when >= 5 requests in the run (SPEC criterion 11)
- [ ] total_requests logged
- [ ] SSE parser handles mid-stream error event gracefully - counts as failure, does not crash harness

**Verification:**
- [ ] Run harness with sage-ai + graph-rag-service both running; open MLflow UI - all 7 metrics appear
- [ ] Kill sage-ai mid-run; confirm harness counts failures and logs failed_count > 0
- [ ] Run with 1 question; confirm P50/P95 are skipped or clearly marked

**Dependencies:** Task 2

**Files likely touched:**
- sage-ai/eval/sage_eval.py [MODIFY]
- sage-ai/eval/questions.json [NEW - evaluation dataset]

**Estimated scope:** M (2-3 files)

---

## Task 4: Java timing SSE event (llm_ms, retrieval_ms)

**Description:** Add System.nanoTime() timers at three points inside the sage-ai
ask pipeline: overall request start, graph-rag-service call start/end (retrieval),
and LLM call start/end. Emit an optional event: timing SSE event before event: done
with the measured values. Update sage_eval.py to parse this event and log
llm_latency_s and retrieval_latency_s. The timing event must be fail-soft: if
instrumentation throws, it is silently omitted and the SSE stream continues to done.

Note: retrieval_ms covers the full graph-rag-service HTTP call from sage-ai.
No changes are required inside graph-rag-service itself.

**Acceptance criteria:**
- [ ] POST /ask SSE stream contains event: timing with e2e_ms, llm_ms, retrieval_ms before done
- [ ] Timing event is silently absent if instrumentation fails (fail-soft - SPEC criterion 16)
- [ ] ./mvnw test passes with no regression
- [ ] llm_latency_s logged to MLflow run (SPEC criterion 7)
- [ ] retrieval_latency_s logged to MLflow run (SPEC criterion 8, covers graph-rag-service call time)
- [ ] SSE contract order preserved: status -> status -> status -> status -> result -> timing -> done

**Verification:**
- [ ] curl -N -X POST http://localhost:8080/ask confirms event: timing present in output
- [ ] ./mvnw test - no failures
- [ ] MLflow run shows both llm_latency_s and retrieval_latency_s

**Dependencies:** Task 3 (harness must exist to consume the timing event)
  Note: Requires ChatController.java business logic to be in place before starting.

**Files likely touched:**
- sage-ai/src/main/java/com/company/sage/chat/ChatController.java [MODIFY]
- sage-ai/eval/sage_eval.py [MODIFY - parse timing event]

**Estimated scope:** M (2 files)

---

## Task 5: Artifact logging

**Description:** At the end of each run log two artifacts to MLflow: (1)
eval_results.json with per-request raw data (question, latency, success, gapFlag);
(2) sage_config_snapshot.json with the configuration params used in the run.
Artifacts must contain enough information to understand or reproduce the experiment.
No secrets, application logs, or credentials are included.

**Acceptance criteria:**
- [ ] eval_results.json artifact appears on each MLflow run, downloadable from UI
- [ ] eval_results.json contains one entry per request: question, latency_s, success, gap_flag
- [ ] sage_config_snapshot.json contains the params logged in the same run
- [ ] No credentials, tokens, or application logs in artifacts (SPEC Never)

**Verification:**
- [ ] MLflow UI -> run -> Artifacts tab - both files present and downloadable
- [ ] Open eval_results.json - confirm structure matches spec, no secrets

**Dependencies:** Task 3

**Files likely touched:**
- sage-ai/eval/sage_eval.py [MODIFY - add log_artifact calls]

**Estimated scope:** XS (1 file)

---

## Checkpoint: After Tasks 3-5 (Slice 2 complete)

- [ ] Full run: params (T2) + latency/reliability metrics (T3) + Java internal breakdown (T4) + artifacts (T5)
- [ ] ./mvnw test still passes
- [ ] SPEC acceptance criteria 6-11, 13, 14 satisfied
- [ ] Team review before proceeding to Phase 3

---

## Phase 3 - Slice 3: Token metrics and run comparison validated

Delivers: SPEC acceptance criteria 12, 13, 14

---

## Task 6: Token metrics from LangChain4j Ollama response (sage-ai)

**Description:** Extract prompt_eval_count (input tokens) and eval_count (output
tokens) from the LangChain4j Ollama response inside sage-ai. Derive
tokens_per_sec = eval_count / (llm_ms / 1000). Extend the event: timing payload
to include these values. Update the harness to log input_tokens, output_tokens, and
tokens_per_sec to MLflow. All three are conditional: if Ollama does not return token
counts, fields are absent and no exception is thrown (SPEC Recommended where available).

No changes are required in graph-rag-service for token metrics.

**Acceptance criteria:**
- [ ] input_tokens, output_tokens, tokens_per_sec appear in MLflow run when Ollama exposes token counts
- [ ] Missing token counts from Ollama handled without exception (graceful degradation)
- [ ] ./mvnw test still passes
- [ ] SPEC acceptance criterion 12 satisfied

**Verification:**
- [ ] Run harness with llama3.2:3b via sage-ai - confirm token metrics appear in MLflow UI
- [ ] Switch to a model that does not return counts - confirm harness does not crash

**Dependencies:** Task 4

**Files likely touched:**
- sage-ai/src/main/java/com/company/sage/adk/agents/ [MODIFY - token extraction]
- sage-ai/eval/sage_eval.py [MODIFY - parse token fields]

**Estimated scope:** M (2 files)

---

## Task 7: Run comparison smoke test

**Description:** Execute two intentionally different runs back-to-back (e.g. one
with retrieval_top_k=3, one with retrieval_top_k=5 against graph-rag-service) and
validate in the MLflow UI compare view that the parameter difference is visible
alongside the metric difference. This is the end-to-end validation of SPEC
criteria 13 and 14.

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
- sage-ai/eval/sage_eval.py [MINOR MODIFY - add --top-k CLI arg for comparison runs]

**Estimated scope:** XS (1 file)

---

## Checkpoint: Phase 3 Complete - All Acceptance Criteria Met

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
| Port 5000 already in use | Med | Edit .env from the mlflow docker-compose bundle to change MLFLOW_PORT before docker compose up |
| ChatController.java not yet implemented when T4 begins | High | T1-T3 and T5 can proceed without it. T4 can only start once the business logic exists. |
| Ollama does not expose token counts for chosen model | Med | T6 is conditional by design; harness skips gracefully. Verify with llama3.2:3b early. |
| SSE line-by-line parsing is fragile | Med | Write a dedicated SSE parser with its own unit test in eval/test_sse_parser.py |
| git rev-parse fails in detached HEAD state | Low | Wrap in try/except; fall back to "unknown" |
| PostgreSQL data lost if docker compose down -v is run | Low | Always use docker compose down (without -v) to preserve volumes |

---

## Open Questions

| ID | Question | Blocks |
|----|----------|--------|
| Q1 | Is ChatController.java business logic implemented? If not, T4 and T6 cannot start. | T4, T6 |
| Q2 | Should event: timing SSE be an official part of the /ask contract or kept as eval-only? | T4 |
| Q3 | Which Ollama model is confirmed for the POC? Token count availability depends on model. | T6 |
