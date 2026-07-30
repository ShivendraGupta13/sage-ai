# Sage AI x MLflow — Task Checklist

Source of truth: tasks/mlflow-integration-plan.md | SPEC: docs/SPEC.md §MLflow Experiment Tracking

**Scope: sage-ai (Java, port 8080) + graph-rag-service (FastAPI, port 8001) only.**

---

## Phase 1 — Slice 1: A run appears in MLflow

- [x] **T1** MLflow stack started via Docker Compose and eval environment ready
  - [x] Docker Desktop (or Docker Engine) is installed and running
  - [x] docker compose up -d starts all 3 containers healthy (postgres, rustfs, mlflow-server)
  - [x] http://localhost:5000 opens MLflow UI
  - [x] Python env can connect: mlflow v3.14.0 connected OK
  - [x] sage-ai/eval/requirements.txt created (mlflow>=3.14.0, requests, numpy, python-dotenv)
  - [x] sage-ai/README.md updated with Docker Compose startup steps
  - Scope: XS | Files: mlflow/docker-compose.yml [NEW], mlflow/Dockerfile [NEW], mlflow/.env.example [NEW], eval/requirements.txt [NEW], README.md [MODIFY]

- [x] **T2** Harness skeleton with parameter logging
  - [x] eval/sage_eval.py creates experiment sage-ai-agent-evaluation
  - [x] Run records: llm_model, git_commit, retrieval_top_k, retrieval_min_score
  - [x] Run records: scoring_w1, scoring_w2, scoring_dual_boost, hardware, prompt_version
  - [x] No secrets logged
  - [x] Script exits cleanly when MLflow is unreachable (warning only, /ask unaffected)
  - Scope: S | Files: eval/sage_eval.py [NEW], eval/questions.json [NEW]

### Checkpoint 1
- [x] Docker Compose stack healthy (postgres ✓, rustfs ✓, mlflow-server ✓, port 5000 open)
- [x] sage-ai-agent-evaluation experiment visible in MLflow UI
- [x] SPEC criteria 2, 3, 4, 5, 15, 16 satisfied
- [x] README.md Docker Compose section updated
- [x] Team review complete

---

## Phase 2 — Slice 2: Metrics visible per run

- [x] **T3** Wall-clock latency, success, failure, gap rate, P50/P95
  - [x] Harness calls POST /ask on sage-ai for each question in eval/questions.json
  - [x] e2e_latency_s logged per request (step metric)
  - [x] success_rate logged
  - [x] failed_count logged
  - [x] gap_rate logged (parsed from gapFlag in SSE result event)
  - [x] p50_latency_s and p95_latency_s logged when >= 5 requests
  - [x] total_requests logged
  - [x] SSE mid-stream error event handled as failure (no crash)
  - Scope: M | Files: eval/sage_eval.py [MODIFY], eval/questions.json [NEW]

- [x] **T4** Java timing SSE event — `event: timing` (official contract, Option A)
  - [x] System.nanoTime() timers in `SageAskService` at graph-rag-service call and LLM call
  - [x] event: timing emitted before event: done with e2e_ms, llm_ms, retrieval_ms
  - [x] SSE contract order: status → result → timing → done
  - [x] Timing event is fail-soft (silently absent on error, /ask still completes)
  - [x] ./mvnw test passes (44 tests green)
  - [x] llm_latency_s logged to MLflow per step (SPEC criterion 7)
  - [x] retrieval_latency_s logged to MLflow per step (SPEC criterion 8)
  - Scope: M | Files: SageAskService.java [MODIFY], eval/sage_eval.py [MODIFY]

- [x] **T5** Artifact logging
  - [x] eval_results.json artifact logged per run (question, latency_s, success, gap_flag per row)
  - [x] sage_config_snapshot.json artifact logged per run (mirror of params)
  - [x] Both downloadable from MLflow UI Artifacts tab
  - [x] No secrets or app logs in artifacts
  - Scope: XS | Files: eval/sage_eval.py [MODIFY]

### Checkpoint 2
- [x] Full run: params + latency metrics + Java internal breakdown + artifacts
- [x] ./mvnw test green
- [x] SPEC criteria 6, 7, 8, 9, 10, 11, 13, 14 satisfied
- [x] Team review before proceeding

---

## Phase 3 — Slice 3: Token metrics and comparison validated

- [x] **T6** Token metrics from LangChain4j in sage-ai — model: llama3.1:8b
  - [x] input_tokens (prompt_eval_count) extracted from response in SageAskService
  - [x] output_tokens (eval_count) extracted from response in SageAskService
  - [x] tokens_per_sec = eval_count / (llm_ms / 1000) derived in SageAskService
  - [x] Values added to event: timing payload
# Sage AI x MLflow — Task Checklist

Source of truth: tasks/mlflow-integration-plan.md | SPEC: docs/SPEC.md §MLflow Experiment Tracking

**Scope: sage-ai (Java, port 8080) + graph-rag-service (FastAPI, port 8001) only.**

---

## Phase 1 — Slice 1: A run appears in MLflow

- [x] **T1** MLflow stack started via Docker Compose and eval environment ready
  - [x] Docker Desktop (or Docker Engine) is installed and running
  - [x] docker compose up -d starts all 3 containers healthy (postgres, rustfs, mlflow-server)
  - [x] http://localhost:5000 opens MLflow UI
  - [x] Python env can connect: mlflow v3.14.0 connected OK
  - [x] sage-ai/eval/requirements.txt created (mlflow>=3.14.0, requests, numpy, python-dotenv)
  - [x] sage-ai/README.md updated with Docker Compose startup steps
  - Scope: XS | Files: mlflow/docker-compose.yml [NEW], mlflow/Dockerfile [NEW], mlflow/.env.example [NEW], eval/requirements.txt [NEW], README.md [MODIFY]

- [x] **T2** Harness skeleton with parameter logging
  - [x] eval/sage_eval.py creates experiment sage-ai-agent-evaluation
  - [x] Run records: llm_model, git_commit, retrieval_top_k, retrieval_min_score
  - [x] Run records: scoring_w1, scoring_w2, scoring_dual_boost, hardware, prompt_version
  - [x] No secrets logged
  - [x] Script exits cleanly when MLflow is unreachable (warning only, /ask unaffected)
  - Scope: S | Files: eval/sage_eval.py [NEW], eval/questions.json [NEW]

### Checkpoint 1
- [x] Docker Compose stack healthy (postgres ✓, rustfs ✓, mlflow-server ✓, port 5000 open)
- [x] sage-ai-agent-evaluation experiment visible in MLflow UI
- [x] SPEC criteria 2, 3, 4, 5, 15, 16 satisfied
- [x] README.md Docker Compose section updated
- [x] Team review complete

---

## Phase 2 — Slice 2: Metrics visible per run

- [x] **T3** Wall-clock latency, success, failure, gap rate, P50/P95
  - [x] Harness calls POST /ask on sage-ai for each question in eval/questions.json
  - [x] e2e_latency_s logged per request (step metric)
  - [x] success_rate logged
  - [x] failed_count logged
  - [x] gap_rate logged (parsed from gapFlag in SSE result event)
  - [x] p50_latency_s and p95_latency_s logged when >= 5 requests
  - [x] total_requests logged
  - [x] SSE mid-stream error event handled as failure (no crash)
  - Scope: M | Files: eval/sage_eval.py [MODIFY], eval/questions.json [NEW]

- [x] **T4** Java timing SSE event — `event: timing` (official contract, Option A)
  - [x] System.nanoTime() timers in `SageAskService` at graph-rag-service call and LLM call
  - [x] event: timing emitted before event: done with e2e_ms, llm_ms, retrieval_ms
  - [x] SSE contract order: status → result → timing → done
  - [x] Timing event is fail-soft (silently absent on error, /ask still completes)
  - [x] ./mvnw test passes (44 tests green)
  - [x] llm_latency_s logged to MLflow per step (SPEC criterion 7)
  - [x] retrieval_latency_s logged to MLflow per step (SPEC criterion 8)
  - Scope: M | Files: SageAskService.java [MODIFY], eval/sage_eval.py [MODIFY]

- [x] **T5** Artifact logging
  - [x] eval_results.json artifact logged per run (question, latency_s, success, gap_flag per row)
  - [x] sage_config_snapshot.json artifact logged per run (mirror of params)
  - [x] Both downloadable from MLflow UI Artifacts tab
  - [x] No secrets or app logs in artifacts
  - Scope: XS | Files: eval/sage_eval.py [MODIFY]

### Checkpoint 2
- [x] Full run: params + latency metrics + Java internal breakdown + artifacts
- [x] ./mvnw test green
- [x] SPEC criteria 6, 7, 8, 9, 10, 11, 13, 14 satisfied
- [x] Team review before proceeding

---

## Phase 3 — Slice 3: Token metrics and comparison validated

- [x] **T6** Token metrics from LangChain4j in sage-ai — model: llama3.1:8b
  - [x] input_tokens (prompt_eval_count) extracted from response in SageAskService
  - [x] output_tokens (eval_count) extracted from response in SageAskService
  - [x] tokens_per_sec = eval_count / (llm_ms / 1000) derived in SageAskService
  - [x] Values added to event: timing payload
  - [x] Harness logs input_tokens, output_tokens, tokens_per_sec to MLflow
  - [x] Missing token counts handled without exception (graceful degradation)
  - [x] ./mvnw test passes (44 tests green)
  - [x] SPEC criterion 12 satisfied
  - Scope: M | Files: SageAskService.java [MODIFY], eval/sage_eval.py [MODIFY]

- [x] **T7** Run comparison smoke test
  - [x] Add `otel-collector` service to `mlflow/docker-compose.yml`
  - [x] Create `mlflow/otel-collector-config.yaml` with OTLP gRPC receiver (port 4317) and MLflow exporter
  - [x] Verify `docker compose up -d` starts all 4 containers healthy

---

## Phase 6 — Dynamic Git Metadata & MLflow Trace UI Columns Fix

- [x] **T14** Dynamic Git Metadata Helper (`GitUtil.java`)
  - [x] Create `GitUtil.java` to dynamically resolve commit hash (`git rev-parse --short HEAD`)
  - [x] Implement fail-soft fallback chain (CLI -> Env Vars -> Default)

- [x] **T15** MLflow Trace UI Attributes Formatting
  - [x] Update `SageAskService.java` to format `mlflow.trace.inputs` and `mlflow.trace.outputs` as JSON strings
  - [x] Bind dynamic `mlflow.source.git.commit` attribute onto OpenTelemetry spans

- [x] **T16** End-to-End Verification
  - [x] Run `./mvnw test` (48 tests pass)
  - [x] Verify MLflow UI **Traces** tab shows populated Prompt and Git Commit columns

### Checkpoint 6 — PHASE 6 COMPLETE
- [x] Dynamic Git commit metadata working
- [x] Prompt and Git Commit columns populated in MLflow UI
  - [x] Two runs executed with different retrieval_top_k (top_k=3 run: f7c99ac8, top_k=7 run: f505cf04)
  - [x] MLflow UI compare view shows param + metric diff side-by-side
  - [x] Reviewer can identify what changed from UI alone
  - [x] All 16 SPEC Phase 1 acceptance criteria verified and checked off
  - Scope: XS | Files: eval/sage_eval.py [MODIFY — added --top-k CLI arg]

### Checkpoint 3 — ALL TASKS DONE
- [x] All 16 SPEC Phase 1 acceptance criteria met
- [x] ./mvnw test passes (44 tests green)
- [x] README.md complete
- [x] Team review and sign-off

---

## Phase 6 — Dynamic Git Metadata & MLflow Trace UI Columns Fix

- [x] **T14** Dynamic Git Metadata Helper (`GitUtil.java`)
  - [x] Create `GitUtil.java` to dynamically resolve commit hash (`git rev-parse --short HEAD`)
  - [x] Implement fail-soft fallback chain (CLI -> Env Vars -> Default)

- [x] **T15** MLflow Trace UI Attributes Formatting
  - [x] Update `SageAskService.java` to format `mlflow.trace.inputs` and `mlflow.trace.outputs` as JSON strings
  - [x] Bind dynamic `mlflow.source.git.commit` attribute onto OpenTelemetry spans

- [x] **T16** End-to-End Verification
  - [x] Run `./mvnw test` (48 tests pass)
  - [x] Verify MLflow UI **Traces** tab shows populated Prompt and Git Commit columns

### Checkpoint 6 — PHASE 6 COMPLETE
- [x] Dynamic Git commit metadata working
- [x] Prompt and Git Commit columns populated in MLflow UI
