# Sage AI Ask Pipeline — Task Checklist

Source of truth for `/build`: [plan.md](plan.md) · Spec: [docs/SPEC.md](../docs/SPEC.md)

**Status:** Planning complete. **No application code until human review + explicit `/build`.**

## Phase 1: Foundation
- [x] Task 1: Config `@ConfigurationProperties` for `sage.*` YAML
- [x] Task 2: Ask / retrieve / Knowledge Card / health / error DTOs
- [x] Task 3: `ResultMerger` + unit tests

### Checkpoint: Foundation
- [x] Merger tests green; DTOs match contracts
- [x] Human skim of scoring edge cases before clients

## Phase 2: Retrieve clients + health
- [x] Task 4: Graph RAG HTTP clients + fail-soft + contract tests
- [x] Task 5: `GET /health` with Graph RAG reachability

### Checkpoint: Clients + health
- [x] `./mvnw test` green without live Graph RAG
- [x] Manual optional: live `:8000` health when sister is up

## Phase 3: ADK pipeline
- [x] Task 6: LLM/Ollama bean + optional spike test
- [x] Task 7: QueryInterpret agent
- [x] Task 8: Retrieve tools + ParallelAgent agents
- [x] Task 9: Merger tool + Synth + SageRoot SequentialAgent

### Checkpoint: ADK
- [x] Agent tree matches docs; evidence-only prompts reviewed
- [x] Human approve before SSE wiring

## Phase 4: Public ask API
- [x] Task 10: `POST /ask` SSE bridge + validation + correlationId
- [x] Task 11: Ask integration tests (mocked Graph RAG)

### Checkpoint: Ask E2E (mocked)
- [x] Full SSE sequence asserted in tests
- [x] Human demo dry-run with sister seed when available

## Phase 5: Eval + MLflow tracking + docs
- [ ] Task 12: F8 Promptfoo golden set
- [ ] Task 13: README/docs sync with shipped behavior
- [ ] Task 14: MLflow experiment tracking & latency/quality metrics

### Checkpoint: Complete
- [ ] SPEC success criteria 1–10 satisfied for Java POC
- [ ] MLflow Phase 1 Acceptance Criteria 1–16 satisfied
- [ ] Ready for code review / CEO demo with seeded sister service
