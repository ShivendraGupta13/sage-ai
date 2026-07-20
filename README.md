# Sage — Expertise Locator + Prior-Art Concierge (POC)

Internal AI assistant: *Who here already solved this, and where is the proof?*

## Quick start

### 1. Infrastructure (Neo4j — owned by graph-rag-service)

Neo4j runs only from the sister Python service. Do **not** start Neo4j from this repo.

```bash
cd ../graph-rag-service
cp .env.example .env   # if needed
docker compose up -d neo4j
```

Ports: Neo4j Bolt `:7687`, Browser `:7474`.

### 2. Python retrieval service (`:8000`)

```bash
cd ../graph-rag-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# Offline seed uses Orion fixtures from this repo:
# python scripts/seed_orion.py --offline ../sage-ai/orion-apis
uvicorn app.main:app --reload --port 8000
```

### 3. Java Sage service (`:8080`)

```bash
# Optional: Ollama for LLM query interpretation
ollama serve && ollama pull llama3.2:3b

./mvnw spring-boot:run
```

Base URL for Graph RAG (default): `SAGE_GRAPH_RAG_BASE_URL=http://localhost:8000`

### 4. Ask via Postman or curl

Import Orion fixtures / collections from [`orion-apis/`](orion-apis/).

```bash
curl -N -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"query":"How did we migrate to OpenTelemetry?"}'
```

## Documentation

| Doc | Purpose |
|-----|---------|
| [feature-document.md](feature-document.md) | Product scope F0–F8 |
| [docs/SPEC.md](docs/SPEC.md) | Master spec + contract index |
| [docs/contracts/](docs/contracts/) | Inter-service API contracts |
| [docs/architecture.md](docs/architecture.md) | System design |
| [docs/google-java-adk-usage.md](docs/google-java-adk-usage.md) | Google Java ADK agent tree |
| [docs/orion-api-documentation.md](docs/orion-api-documentation.md) | Orion ingest API (Python only) |
| Sister: `../graph-rag-service/` | Neo4j, retrieve APIs, schema/ingest |

## Tests

```bash
./mvnw test
# Ollama spike (optional): OLLAMA_SPIKE=true ./mvnw test -Dtest=QueryInterpretSpikeTest
```
