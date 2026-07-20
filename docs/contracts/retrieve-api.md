# Graph RAG — retrieve API contract

APIs that **sage-ai** calls on **graph-rag-service** (`:8000`).

Runtime SoT: [`app/models/api_contracts.py`](../../../graph-rag-service/app/models/api_contracts.py).  
Must stay identical to that module and to [architecture.md §12](../architecture.md#12-inter-service-api-contracts).

Base URL (Java): `SAGE_GRAPH_RAG_BASE_URL` (default `http://localhost:8000`).

---

## `POST /retrieve/semantic`

Vector similarity search on `problemStatement`. Called by Java `SemanticSearchAgent`.

### Request

```json
{
  "problemStatement": "Safely fetching external images from emails without SSRF exposure",
  "top_k": 5,
  "min_score": 0.60
}
```

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `problemStatement` | string | required | |
| `top_k` | int | `5` | Range 1–20 |
| `min_score` | float | `0.60` | Range 0.0–1.0 |

### Response `200`

```json
{
  "hits": [{
    "doc_id": "178025",
    "source": "orion_metadata",
    "vectorScore": 0.83,
    "passage": "Implemented a server-side proxy using got-scrubbing library…",
    "metadata": {
      "title": "SSRF-safe external image loader",
      "teamName": "Payments Platform",
      "teamId": "42",
      "people": [{ "personId": "65", "name": "Priya Sharma" }],
      "technologies": ["SSRF mitigation", "npm", "Node.js"],
      "documentLink": "https://talenticacontact.freshdesk.com/a/tickets/1234",
      "category": "HARD_PROBLEMS",
      "sourceAttribution": "Orion API"
    }
  }],
  "query_time_ms": 210,
  "total_found": 1
}
```

---

## `POST /retrieve/graph`

Cypher graph traversal on `techNeeded[]` tags. Called by Java `GraphTraversalAgent`.

### Request

```json
{
  "techNeeded": ["SSRF mitigation", "npm image proxy", "email rendering"],
  "top_k": 5
}
```

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `techNeeded` | string[] | required | |
| `top_k` | int | `5` | Range 1–20 |

### Response `200`

```json
{
  "hits": [{
    "doc_id": "178025",
    "source": "orion_metadata",
    "graphScore": 1.0,
    "matchedTags": ["SSRF mitigation"],
    "pathDescription": "Technology[SSRF mitigation] → HardProblem[SSRF-safe image loader] → Team[Payments Platform]",
    "passage": "Implemented a server-side proxy using got-scrubbing library…",
    "metadata": {
      "title": "SSRF-safe external image loader",
      "teamName": "Payments Platform",
      "teamId": "42",
      "people": [{ "personId": "65", "name": "Priya Sharma" }],
      "technologies": ["SSRF mitigation", "npm", "Node.js"],
      "documentLink": "https://talenticacontact.freshdesk.com/a/tickets/1234",
      "category": "HARD_PROBLEMS",
      "sourceAttribution": "Orion API"
    }
  }],
  "query_time_ms": 180,
  "total_found": 1
}
```

---

## Shared hit `metadata`

Identical for semantic and graph hits:

| Field | Type | Default |
|-------|------|---------|
| `title` | string | required |
| `teamName` | string | `"N/A"` |
| `teamId` | string | `"0"` |
| `people` | `{ personId: string, name: string }[]` | `[]` |
| `technologies` | string[] | `[]` |
| `documentLink` | string \| null | `null` |
| `category` | string | `"HARD_PROBLEMS"` |
| `sourceAttribution` | string | `"Orion API"` |

---

## Common rules (both retrieve endpoints)

| Field / case | Rule |
|--------------|------|
| `hits` | Always an array — never `null` |
| Empty result | `200` with `hits: []` — never error on no-match |
| Neo4j / internal failure | `500` with `{"detail": "..."}`; Java fail-softs to `[]` |
| `passage` | Extractive from indexed summary — never LLM-paraphrased at this boundary |
| `metadata` | Card-complete — Java never calls Orion at ask-time |
| `personId` / `teamId` / `doc_id` | Always strings |

---

## `GET /health` (Graph RAG)

```json
{
  "status": "ok",
  "last_seed_run": "ISO-8601",
  "indexed_records": 1204,
  "neo4j_reachable": true
}
```

---

## `POST /admin/reseed` (optional; not on ask path)

Java does **not** call this during ask.

### Request

```json
{ "force_refresh": false }
```

### Response `202 Accepted`

```json
{ "job_id": "...", "status": "started" }
```
