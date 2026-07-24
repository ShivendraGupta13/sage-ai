# Sage AI Ask Pipeline — Task Checklist

Source of truth for `/build`: [plan.md](plan.md) · Spec: [docs/SPEC.md](../docs/SPEC.md)

**Status:** Planning complete. **No application code until human review + explicit `/build`.**

## Phase 1: Foundation
- [ ] Task 1: Config `@ConfigurationProperties` for `sage.*` YAML
- [ ] Task 2: Ask / retrieve / Knowledge Card / health / error DTOs
- [ ] Task 3: `ResultMerger` + unit tests

### Checkpoint: Foundation
- [ ] Merger tests green; DTOs match contracts
- [ ] Human skim of scoring edge cases before clients

## Phase 2: Retrieve clients + health
- [ ] Task 4: Graph RAG HTTP clients + fail-soft + contract tests
- [ ] Task 5: `GET /health` with Graph RAG reachability

### Checkpoint: Clients + health
- [ ] `./mvnw test` green without live Graph RAG
- [ ] Manual optional: live `:8000` health when sister is up

## Phase 3: ADK pipeline
- [ ] Task 6: LLM/Ollama bean + optional spike test
- [ ] Task 7: QueryInterpret agent
- [ ] Task 8: Retrieve tools + ParallelAgent agents
- [ ] Task 9: Merger tool + Synth + SageRoot SequentialAgent

### Checkpoint: ADK
- [ ] Agent tree matches docs; evidence-only prompts reviewed
- [ ] Human approve before SSE wiring

## Phase 4: Public ask API
- [ ] Task 10: `POST /ask` SSE bridge + validation + correlationId
- [ ] Task 11: Ask integration tests (mocked Graph RAG)

### Checkpoint: Ask E2E (mocked)
- [ ] Full SSE sequence asserted in tests
- [ ] Human demo dry-run with sister seed when available

## Phase 5: Eval + docs
- [ ] Task 12: F8 Promptfoo golden set
- [ ] Task 13: README/docs sync with shipped behavior

### Checkpoint: Complete
- [ ] SPEC success criteria 1–10 satisfied for Java POC
- [ ] Ready for code review / CEO demo with seeded sister service

---

## Workstream: Retrieve fixture capture (offline Java)

- [x] `scripts/capture_retrieve_fixtures.py` + `src/test/resources/fixtures/graphrag/`
- [x] Captured 67 hard-problem retrieve pairs; `manifest.json` complete
- [x] `happy-path-queries.json` (232 + 257, both-paths)
- [x] `RecordedRetrieveFixtureTest`, `ResultMergerRecordedFixtureTest`, GraphRagClient recorded fixture test
- [ ] Review + push to `fix/adk-list-arg-deserialization` (do **not** merge to `main`)
