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
# Required for LLM query interpretation (without it, /ask falls back to the raw query)
ollama serve && ollama pull llama3.2

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

### MLflow Experiment Tracking & Evaluation Harness

1. **Start the MLflow Tracking Server & OpenTelemetry Stack:**
   ```bash
   cd mlflow
   cp .env.example .env     # review defaults if needed
   docker compose up -d     # starts postgres + rustfs (S3) + mlflow-server (v3.14.0) + otel-collector (port 4317)
   ```
   *UI available at:* `http://localhost:5000`

2. **Run Batch Evaluation Harness:**
   ```bash
   # From project root:
   eval\.venv\Scripts\python.exe eval\sage_eval.py [--top-k 5] [--questions eval/questions.json]
   ```
   *Features logged:* Parameters (`llm_model`, `git_commit`, `retrieval_top_k`, `scoring_w1`/`w2`), Step Metrics (`e2e_latency_s`, `llm_latency_s`, `retrieval_latency_s`, `input_tokens`, `output_tokens`, `tokens_per_sec`), Aggregates (`success_rate`, `gap_rate`, `p50_latency_s`, `p95_latency_s`), and downloadable Artifacts (`eval_results.json`, `sage_config_snapshot.json`).

3. **Live User Prompt Telemetry (OpenTelemetry Agent):**
   *Prerequisite: Ensure that the Python Graph RAG service (port 8000) is running.*
   To stream live user prompts (`POST /ask` from Postman, Web UI, or cURL) into MLflow's **Traces** tab:
   ```powershell
   # Windows PowerShell:
   .\scripts\run_sage_with_otel.ps1

   # macOS / Linux:
   ./scripts/run_sage_with_otel.sh
   ```
   Open `http://localhost:5000` and select the **Traces** tab to view live, interactive visual waterfall span trees (`POST /ask` -> `QueryInterpreter` -> `GraphRagClient` -> `Ollama`).
   
   **Custom Span Attributes Tracked:**
   - **GenAI / LLM Attributes:** `gen_ai.system` (`ollama`), `gen_ai.request.model` (`llama3.2:latest`), `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `gen_ai.usage.total_tokens`
   - **RAG / Knowledge Base Attributes:** `rag.query`, `rag.num_results`, `rag.gap_flag`
   - **System Metadata Attributes:** `sage.correlation_id`, `sage.problem_statement`, `sage.tech_needed`

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

