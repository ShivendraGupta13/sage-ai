# Sage SPEC — document index

Master index for the Sage expertise-locator POC. Product requirements live in the feature document; system boundaries and HTTP contracts live under `docs/`.

## Read order

| Order | Doc | Purpose |
|-------|-----|---------|
| 1 | [feature-document.md](../feature-document.md) | Product scope F0–F8, Knowledge Card, non-goals |
| 2 | [architecture.md](architecture.md) | System design, ownership, ask lifecycle, §12 contracts |
| 3 | [contracts/](contracts/) | Inter-service API contracts (ask + retrieve) |
| 4 | [google-java-adk-usage.md](google-java-adk-usage.md) | ADK agent tree, tools, fail-soft behavior |
| 5 | [orion-api-documentation.md](orion-api-documentation.md) | Orion ingest HTTP (Python seed only) |

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
| Documented ask + retrieve HTTP contracts | **both** (see [contracts/README.md](contracts/README.md)) |

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
