# Sage AI x MLflow - Task Checklist

Source of truth: tasks/plan.md | SPEC: docs/SPEC.md section MLflow Experiment Tracking

**Scope: sage-ai (Java, port 8080) + graph-rag-service (FastAPI, port 8001) only.**

---

## Phase 1 - Slice 1: A run appears in MLflow

- [ ] **T1** MLflow stack started via Docker Compose and eval environment ready
  - [ ] Docker Desktop (or Docker Engine) is installed and running
  - [ ] Clone mlflow repo and cd mlflow/docker-compose; cp .env.dev.example .env
  - [ ] docker compose up -d starts all 3 containers (mlflow, postgres, rustfs) healthy
  - [ ] http://localhost:5000 opens MLflow UI
  - [ ] Python env can connect: import mlflow + set_tracking_uri('http://localhost:5000') succeeds
  - [ ] sage-ai/eval/requirements.txt created (mlflow>=2.15.0, requests, numpy)
  - [ ] sage-ai/README.md updated with Docker Compose startup steps
  - [ ] docker compose down then up again — previous data still present (volumes preserved)
  - Scope: XS | Files: eval/requirements.txt [NEW], README.md [MODIFY]
  - Ref: https://mlflow.org/docs/latest/self-hosting/#docker-compose

- [ ] **T2** Harness skeleton with parameter logging
  - [ ] eval/sage_eval.py creates experiment sage-ai-agent-evaluation
  - [ ] Run records: llm_model, git_commit, retrieval_top_k, retrieval_min_score
  - [ ] Run records: scoring_w1, scoring_w2, scoring_dual_boost, hardware, prompt_version
  - [ ] No secrets logged
  - [ ] Script exits cleanly when MLflow is unreachable (warning only, /ask unaffected)
  - Scope: S | Files: eval/sage_eval.py [NEW]

### Checkpoint 1
- [ ] Docker Compose stack healthy (mlflow + postgres + rustfs all running)
- [ ] Run appears in MLflow UI with all params
- [ ] SPEC criteria 1, 2, 3, 4, 5, 15, 16 satisfied
- [ ] Team review before proceeding

---

## Phase 2 - Slice 2: Metrics visible per run

- [ ] **T3** Wall-clock latency, success, failure, gap rate, P50/P95
  - [ ] Harness calls POST /ask on sage-ai for each question in eval/questions.json
  - [ ] e2e_latency_s logged per request
  - [ ] success_rate logged
  - [ ] failed_count logged
  - [ ] gap_rate logged (parsed from gapFlag in SSE result event)
  - [ ] p50_latency_s and p95_latency_s logged when >= 5 requests
  - [ ] total_requests logged
  - [ ] SSE mid-stream error handled as failure (no crash)
  - Scope: M | Files: eval/sage_eval.py [MODIFY], eval/questions.json [NEW]

- [ ] **T4** Java timing SSE event (sage-ai only, covers graph-rag-service call time)
  - Note: Requires ChatController.java business logic to be in place before starting
  - [ ] System.nanoTime() timers at request start, graph-rag-service call, LLM call
  - [ ] event: timing emitted before event: done with e2e_ms, llm_ms, retrieval_ms
  - [ ] Timing event is fail-soft (silently absent on error, /ask still completes)
  - [ ] ./mvnw test passes
  - [ ] llm_latency_s logged to MLflow (SPEC criterion 7)
  - [ ] retrieval_latency_s logged to MLflow (SPEC criterion 8, = graph-rag-service call time)
  - Scope: M | Files: ChatController.java [MODIFY], eval/sage_eval.py [MODIFY]

- [ ] **T5** Artifact logging
  - [ ] eval_results.json artifact logged per run (question, latency_s, success, gap_flag per row)
  - [ ] sage_config_snapshot.json artifact logged per run (mirror of params)
  - [ ] Both downloadable from MLflow UI Artifacts tab
  - [ ] No secrets or app logs in artifacts
  - Scope: XS | Files: eval/sage_eval.py [MODIFY]

### Checkpoint 2
- [ ] Full run: params + latency metrics + Java internal breakdown + artifacts
- [ ] ./mvnw test green
- [ ] SPEC criteria 6, 7, 8, 9, 10, 11, 13, 14 satisfied
- [ ] Team review before proceeding

---

## Phase 3 - Slice 3: Token metrics and comparison validated

- [ ] **T6** Token metrics from LangChain4j in sage-ai (no changes to graph-rag-service)
  - Note: Requires T4 to be done first
  - [ ] input_tokens, output_tokens extracted from LangChain4j Ollama response in sage-ai
  - [ ] tokens_per_sec = eval_count / (llm_ms / 1000) derived in sage-ai
  - [ ] Values added to event: timing payload
  - [ ] Harness logs input_tokens, output_tokens, tokens_per_sec to MLflow
  - [ ] Missing token counts handled without exception (graceful degradation)
  - [ ] ./mvnw test passes
  - [ ] SPEC criterion 12 satisfied
  - Scope: M | Files: adk/agents/ [MODIFY in sage-ai], eval/sage_eval.py [MODIFY]

- [ ] **T7** Run comparison smoke test
  - [ ] Two runs executed with different retrieval_top_k (sage-ai -> graph-rag-service)
  - [ ] MLflow UI compare view shows param + metric diff side-by-side
  - [ ] Reviewer can identify what changed from UI alone
  - [ ] All 16 SPEC Phase 1 acceptance criteria verified and checked off
  - Scope: XS | Files: eval/sage_eval.py [MINOR MODIFY - add --top-k CLI arg]

### Checkpoint 3 - DONE
- [ ] All 16 SPEC Phase 1 acceptance criteria met
- [ ] ./mvnw test passes
- [ ] README.md complete
- [ ] Team review and sign-off

---

## Open Questions (resolve before blocked tasks start)

- [ ] **Q1** Is ChatController.java implemented? If not, T4 and T6 cannot start.
- [ ] **Q2** Should event: timing be part of the official /ask contract or kept eval-only?
- [ ] **Q3** Which Ollama model is confirmed for POC? (llama3.2:3b is the current default)
