# Sage Java — public API contract

Consumed by Postman / curl / future UI. Implemented by sage-ai (`:8080`).

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
| `done` | `{}` | Stream close |
| `error` | `{"message":"..."}` | On any unrecoverable error |

## `GET /health`

```json
{
  "status": "ok",
  "semanticServiceReachable": true,
  "graphServiceReachable": true
}
```

Health should report reachability of Graph RAG retrieve endpoints (same base URL for both semantic and graph).

## Downstream

On each ask, Java calls the sister Graph RAG service — see [retrieve-api.md](retrieve-api.md).
