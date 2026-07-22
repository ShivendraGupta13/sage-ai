# Sage Java — public API contract

Consumed by Postman / curl / future UI. Implemented by sage-ai (`:8080`).

Application HTTP / SSE errors use the shared Error schema in [architecture.md §12](../architecture.md#12-inter-service-api-contracts) — not redefined here.

## `POST /ask`

| | |
|---|---|
| **Request** | `{ "query": "string" }` |
| **Response** | `text/event-stream` (SSE) |

### SSE event sequence

| Event type | `data` payload | When |
|---|---|---|
| `status` | `{"message":"Interpreting query…"}` | Immediately on receipt |
| `status` | `{"message":"Identified problem state and tech context","problemStatement":"...","techNeeded":["..."]}` | After `QueryInterpret` |
| `status` | `{"message":"Searching knowledge base…"}` | Parallel search launched |
| `status` | `{"message":"Ranking results…"}` | Both searches returned |
| `result` | Full Knowledge Card JSON ([architecture.md §11](../architecture.md)) | After `KnowledgeCardSynth` |
| `done` | `{}` | Stream close (success) |
| `error` | `{"message","detail","code","correlationId"}` | Mid-stream unrecoverable; then close without `done` |

### Errors

| When | Status / event | Body |
|------|----------------|------|
| Invalid/missing `query`, bad JSON (before stream) | HTTP `400` | Shared Error schema ([architecture §12](../architecture.md#12-inter-service-api-contracts)) |
| Unrecoverable after SSE opened | SSE `error` | Subset: `message`, `detail`, `code`, `correlationId` |

## `GET /health`

```json
{
  "status": "ok",
  "semanticServiceReachable": true,
  "graphServiceReachable": true
}
```

Process up → `200`. Use `status: "degraded"` when a reachability flag is false (not the Error schema). Reports Graph RAG retrieve reachability (same base URL for semantic and graph).

## Downstream

On each ask, Java calls the sister Graph RAG service — see [retrieve-api.md](retrieve-api.md). Retrieve `5xx` is fail-softed to empty hits; it does not become an ask SSE `error` by itself.
