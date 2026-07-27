# Todo: Ask low-cost correctness

- [x] **T1 — Parallel retrieve (tiny)**  
  - Acceptance: When `techNeeded` non-empty, semantic and graph are launched concurrently in `SageAskService`.  
  - Verify: `./mvnw test -Dtest=AskIntegrationTest`  
  - Files: `SageAskService.java`, optionally thin unit test

- [x] **T2 — Route-first `directAnswer` (tiny)**  
  - Acceptance: Non-empty results → `Yes — N team(s)… (title).`; empty → existing no-prior-art string.  
  - Verify: unit test on formatter + AskIntegrationTest asserts one-liner shape  
  - Files: `DirectAnswerFormatter.java` (new), `SageAskService.java`, test

- [x] **T4 — techNeeded recovery (small)**  
  - Acceptance: Empty interpret tags + semantic `technologies` → graph called with recovered tags; card `techNeeded` reflects recovery.  
  - Verify: unit `TechNeededRecoveryTest` + ask/integration mock sequence  
  - Files: `TechNeededRecovery.java` (new), `SageAskService.java`, tests

## Deferred (not this plan)

- **T3 — Degraded ≠ gap** (Retrieve failure → not a true knowledge gap; optional `degraded` on card; ask-api note) — cover later  
- Score floor / minScore alignment  
- TechnologyNormalizer  
- ADK live wiring / doc overhaul  
- Eval harness
