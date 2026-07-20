# Sage SPEC — document index

Master index for the Sage expertise-locator POC. Product requirements live in the feature document; system boundaries and HTTP contracts live under `docs/`. The only project README is at the repo root.

## Read order

| Order | Doc | Purpose |
|-------|-----|---------|
| 1 | [feature-document.md](../feature-document.md) | Product scope F0–F8, Knowledge Card, non-goals |
| 2 | [architecture.md](architecture.md) | System design, ownership, ask lifecycle, §12 contracts |
| 3 | [spec-coverage-map.md](spec-coverage-map.md) | What future specs must cover (Spring / ADK / Graph RAG) + ownership split |
| 4 | [contracts/ask-api.md](contracts/ask-api.md) | Sage public `POST /ask` SSE + `GET /health` |
| 5 | [contracts/retrieve-api.md](contracts/retrieve-api.md) | Graph RAG `/retrieve/*` (what Java calls) |
| 6 | [google-java-adk-usage.md](google-java-adk-usage.md) | ADK agent tree, tools, fail-soft behavior |
| 7 | [orion-api-documentation.md](orion-api-documentation.md) | Orion ingest HTTP (Python seed only) |

## Sister service (Python)

| Doc | Location |
|-----|----------|
| Graph RAG README | `../../graph-rag-service/README.md` |
| Neo4j schema + ingest | `../../graph-rag-service/GraphRAG_Schema_Data_Ingestion.md` |
| Contract alignment | `../../graph-rag-service/docs/CONTRACTS.md` |
| Runtime retrieve DTOs | `../../graph-rag-service/app/models/api_contracts.py` |

## Ownership (no ambiguity)

| Concern | Owner |
|---------|--------|
| Neo4j + docker-compose + embeddings + Orion ingest + `/retrieve/*` | **graph-rag-service** |
| ADK ask pipeline, SSE `POST /ask`, merge/scoring, Knowledge Card | **sage-ai** |
| Documented ask + retrieve HTTP contracts | **both** (see contract SoT below) |

## Contract source of truth

| Contract | Documented SoT | Runtime SoT |
|----------|----------------|-------------|
| Sage public ask/health | [contracts/ask-api.md](contracts/ask-api.md) | Java Spring controllers (when implemented) |
| Graph RAG retrieve/health/reseed | [contracts/retrieve-api.md](contracts/retrieve-api.md) | `graph-rag-service/app/models/api_contracts.py` |

Architecture narrative also appears in [architecture.md §12](architecture.md#12-inter-service-api-contracts). If narrative and these files diverge, **the contract files + `api_contracts.py` win** for field names and shapes.

Rules:

1. Java never invents retrieve response fields.
2. Python must not change field names, casing, or types without updating `api_contracts.py` and [contracts/retrieve-api.md](contracts/retrieve-api.md).
3. Mixed casing is intentional: camelCase domain fields (`problemStatement`, `vectorScore`) and snake_case transport knobs (`top_k`, `doc_id`).
4. IDs (`doc_id`, `personId`, `teamId`) are always strings.
5. Empty retrieval → `200` + `hits: []`; Neo4j failure → `500` + `detail`; Java fail-softs to empty hits.
6. Ask-time never calls Orion (ingest/reseed only on Python).

## Ports

| Service | Port |
|---------|------|
| Sage Java | `8080` |
| Graph RAG FastAPI | `8000` |
| Neo4j Bolt / Browser | `7687` / `7474` |
| Ollama | `11434` |

## Env (Java)

| Variable | Default |
|----------|---------|
| `SAGE_GRAPH_RAG_BASE_URL` | `http://localhost:8000` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` |
