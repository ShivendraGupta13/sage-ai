# Inter-service API contracts

## Source of truth

| Contract | Documented SoT | Runtime SoT |
|----------|----------------|-------------|
| Sage public ask/health | [ask-api.md](ask-api.md) | Java Spring controllers (when implemented) |
| Graph RAG retrieve/health/reseed | [retrieve-api.md](retrieve-api.md) | [`graph-rag-service/app/models/api_contracts.py`](../../../graph-rag-service/app/models/api_contracts.py) |

Architecture narrative and examples also appear in [architecture.md §12](../architecture.md#12-inter-service-api-contracts). If narrative and these files diverge, **these contract files + `api_contracts.py` win** for field names and shapes.

## Rules

1. **Java never invents** retrieve response fields. Clients must deserialize only what `retrieve-api.md` / `api_contracts.py` define.
2. **Python must not change** field names, casing, or types without updating **both** `api_contracts.py` and [retrieve-api.md](retrieve-api.md) (and architecture §12 examples).
3. Mixed casing is intentional and frozen: camelCase for domain fields (`problemStatement`, `techNeeded`, `vectorScore`) and snake_case for transport knobs (`top_k`, `min_score`, `doc_id`, `query_time_ms`).
4. IDs (`doc_id`, `personId`, `teamId`) are always **strings**, never integers.
5. Empty retrieval → HTTP `200` with `hits: []`. Neo4j/service failure → HTTP `500` with `{"detail": "..."}`; Java fail-softs to empty hits.
6. Ask-time path never calls Orion. Orion is ingest/reseed only (Python).

## Related

- [google-java-adk-usage.md §8](../google-java-adk-usage.md) — tool → HTTP mapping
- Sister [CONTRACTS.md](../../../graph-rag-service/docs/CONTRACTS.md)
