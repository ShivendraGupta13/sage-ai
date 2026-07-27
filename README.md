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
# Offline seed may use the Postman collection from this repo:
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

Import [`orion-apis/sage ai.postman_collection.json`](orion-apis/sage%20ai.postman_collection.json) into Postman.

- **Orion** folder — ingest/discovery (set `orionApiKey`, `orionCookie`)
- **Sage AI** folder — `GET /health`, `POST /retrieve/semantic|graph`, `POST /ask` (`baseUrl` → `http://localhost:8080`, `graphRagBaseUrl` → `http://localhost:8000`)

```bash
curl -N -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"query":"How did we migrate to OpenTelemetry?"}'

curl -s http://localhost:8080/health
```

## Documentation

| Doc | Purpose |
|-----|---------|
| [feature-document.md](feature-document.md) | Product scope F0–F8 |
| [docs/SPEC.md](docs/SPEC.md) | Master spec + contract index |
| [docs/contracts/ask-api.md](docs/contracts/ask-api.md) | Sage public ask/health contract |
| [docs/contracts/retrieve-api.md](docs/contracts/retrieve-api.md) | Graph RAG retrieve contract |
| [docs/architecture.md](docs/architecture.md) | System design |
| [docs/google-java-adk-usage.md](docs/google-java-adk-usage.md) | Google Java ADK agent tree |
| [docs/orion-api-documentation.md](docs/orion-api-documentation.md) | Orion ingest API (Python only) |
| Sister: `../graph-rag-service/` | Neo4j, retrieve APIs, schema/ingest |

## Tests & Evaluations

### Automated Test Suite
Run unit and integration tests:
```bash
./mvnw test
```

*Note:* To run the optional gated Ollama connection spike test, set `OLLAMA_SPIKE=true`:
```bash
OLLAMA_SPIKE=true ./mvnw test -Dtest=QueryInterpretSpikeTest
```

### E2E Quality Evaluation (Promptfoo)
Evaluations are run using [Promptfoo](https://promptfoo.dev) against the active `POST /ask` stream.
1. Ensure the Java service is running: `./mvnw spring-boot:run`
2. Run the evaluation suite:
   ```bash
   npx promptfoo eval -c evaluation/promptfooconfig.yaml
   ```
This suite evaluates Happy Paths, specific technical queries, and out-of-domain boundaries (asserting `gapFlag=true` and candidate Hard Problem notifications).

---

## Configuration Properties

The following keys can be overridden in `src/main/resources/application.yml` or via system environment variables:

| YAML Key | Environment Override | Default | Purpose |
|---|---|---|---|
| `sage.graph-rag.base-url` | `SAGE_GRAPH_RAG_BASE_URL` | `http://localhost:8000` | Downstream python search endpoint |
| `sage.adk.llm.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama service endpoint |
| `sage.adk.llm.model-name` | `SAGE_LLM_MODEL` | `llama3.2:3b` | Ollama target model |
| `sage.retrieval.top-k` | `SAGE_RETRIEVAL_TOP_K` | `5` | Retrieval default top-K size |
| `sage.scoring.w1` | `SAGE_SCORING_W1` | `0.6` | Semantic search score weight |
| `sage.scoring.w2` | `SAGE_SCORING_W2` | `0.4` | Graph search score weight |
| `sage.scoring.dual-match-boost` | `SAGE_SCORING_DUAL_BOOST` | `0.1` | Boosting for dual matches |
| `sage.scoring.min-score` | `SAGE_SCORING_MIN_SCORE` | `0.60` | Minimum score threshold |

