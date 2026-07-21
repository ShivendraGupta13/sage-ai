# Orion API Documentation

HTTP reference for Orion endpoints used by Sage's **seed script (F0)**. Captured from [`orion-apis/sage ai.postman_collection.json`](../orion-apis/sage%20ai.postman_collection.json). Request/response shapes below are documented from that collection — individual fixture JSON payloads are **not** committed.

> **Architecture note:** Nexus (`nexus.talentica.com`) is a web portal that calls this same Orion API. There is no separate Nexus backend. Sage uses the Orion API directly with `api-key` authentication — Nexus and Orion API are the same data source.

---

## Overview

Orion is the sole upstream data source for Sage. All four endpoints are called **at ingest time only** by the Python seed script. The Java ask-time pipeline **never calls Orion** — it queries the pre-built Neo4j store via `POST /retrieve/semantic` and `POST /retrieve/graph`.

### Lifecycle

| Lifecycle | Caller | Purpose |
|-----------|--------|---------|
| **Ingest (F0 seed)** | Python seed script | Build Neo4j graph nodes, edges, and vector embeddings from Orion records |
| **Ask** | Python retrieval service (Neo4j only) | No Orion calls — all data already in Neo4j |

### Ingest fan-out order *(run in this sequence)*

| Step | Endpoint | Output | Notes |
|------|----------|--------|-------|
| 1 (first) | `GET /tech/categories` | `Technology` nodes — full tag vocabulary | Drives steps 2 & 3 |
| 2 (parallel, per tag) | `GET /valueAdd/valueAddsByTag?tag={tag}` | `HardProblem`, `Team`, `Person`, `Document` nodes + all edges | Primary rich-data source |
| 3 (parallel, per tag) | `GET /technology/getTechDigest/label?techDigestLabel={tag}` | Enrich `Technology` nodes with customer coverage | |
| 4 (gap-fill) | `GET /customers/valueAdd/hardProblemsFinancialYear?cycleId=8,7` | Any `HardProblem` not reached via tag fan-out | ⚠️ Confirm `cycleId` values (see [architecture.md D4](architecture.md)) |

### Endpoint selection (ingest purpose only)

| Endpoint | Why called | Neo4j role |
|----------|-----------|------------|
| `valueAddsByTag` | Richest structured record — team, people, summary, doc handle | Primary HP + relationship data |
| `getTechDigest/label` | Customer/project coverage for a technology | Evidence enrichment on `Technology` nodes |
| `tech/categories` | Closed vocabulary of all technology tags | Seed vocabulary for fan-out |
| `hardProblemsFinancialYear` | Full catalog — catches HPs with no tag or missing from fan-out | Gap-fill — filter `ACCEPTED` only |

See [architecture.md §6](architecture.md#6-orion-api--ingest-only-never-at-ask-time) for the full lifecycle rationale.

---

## Base hosts

| Host | Used by |
|------|---------|
| `https://apiorion.talentica.com` | `/tech/*`, `/valueAdd/*`, `/technology/*` |
| `https://apidev-orion.talentica.com` | `/customers/valueAdd/hardProblemsFinancialYear` |

## Authentication (observed)

| Header | Required | Notes |
|--------|----------|-------|
| `Cookie: auth-token=<JWT>; cf_clearance=<value>` | Yes | Browser-style session; both values redacted in fixtures |
| `api-key: <value>` | On `apiorion` host | Present in Postman captures for production host |

Stub mode (`ORION_API_KEY=stub`) uses the Postman collection / local caches configured by the Python seed script — no live HTTP. Only `orion-apis/sage ai.postman_collection.json` is committed in this repo.

---

## 1. Value adds by tag

**Ingest step 2 (primary data source).** Returns the richest per-HP record — team, members, summary, document handle. Called once per tag from the vocabulary returned by `tech/categories`.

**Request**

```http
GET /valueAdd/valueAddsByTag?tag=OpenTelemetry HTTP/1.1
Host: apiorion.talentica.com
api-key: <redacted>
Cookie: auth-token=<redacted>; cf_clearance=<redacted>
```

**Query parameters**

| Param | Type | Example | Description |
|-------|------|---------|-------------|
| `tag` | string | `OpenTelemetry` | Orion tag or tech-digest label |

**Response:** `200 OK` — JSON array of records.

**Sample response**

```json
[
  {
    "fileDetails": {
      "id": 1046,
      "ticketId": 414,
      "fileName": "Rupeek Mail - Observability @Rupeek.pdf",
      "teamId": 52,
      "title": "Application monitoring platform from NewRelic to OpenTelemetry",
      "summary": "- **Title:** Cost Reduction and Transition to a Self-Managed Observability Stack at Rupeek\n\n- **Challenges/Problems:**\n  - Monthly observability expenditure with NewRelic around $7000 deemed excessive.\n  - High per-user and data-ingestion fees contributed significantly to costs.\n\n- **Solution:**\n  - Implemented Prometheus, Loki, Grafana, Tempo, and OpenTelemetry.\n\n- **Results/Impact:**\n  - Positioned to achieve dramatic cost reduction while preserving essential functionalities.",
      "techDigests": "OpenTelemetry, Prometheus, Grafana, Loki",
      "tags": "Fintech, Observability, Cloud, Migration"
    },
    "customersValueAdd": {
      "id": 178025,
      "title": "Application monitoring platform from NewRelic to OpenTelemetry",
      "status": "ACCEPTED",
      "type": "HARD_PROBLEMS",
      "ownerId": [
        { "id": 65, "name": "Hemant Sachdeva", "email": "Hemant.Sachdeva@talentica.com", "userId": null }
      ],
      "teamId": {
        "id": 52,
        "teamName": "Rupeek",
        "teamShortName": "Rupeek",
        "teamLeads": [{ "id": 164, "name": "Tonmoy", "email": "tonmoy.kaushik@talentica.com" }]
      },
      "ticketLink": "https://talenticacontact.freshdesk.com/a/tickets/414",
      "ticketId": 414,
      "impact": null,
      "cycleId": 8
    }
  }
]
```

**Field reference**

| Path | Type | Sage use |
|------|------|----------|
| `fileDetails.summary` | string (markdown) | Knowledge Card summary — pass through unchanged |
| `fileDetails.fileName` | string | Document handle |
| `fileDetails.title` | string | Direct answer input |
| `fileDetails.techDigests` | string (CSV) | Evidence / technology tags |
| `customersValueAdd.id` | number | Stable dedup key |
| `customersValueAdd.status` | string | Filter — exclude `REJECTED` |
| `customersValueAdd.ownerId[]` | array | People on card |
| `customersValueAdd.teamId` | object | Team on card |
| `customersValueAdd.ticketLink` | string | Citation URL |

**Source:** Postman collection request for `valueAddsByTag` (see `orion-apis/sage ai.postman_collection.json`).

---

## 2. Tech digest by label

**Ingest step 3 (coverage enrichment).** Returns customer/project rows for a technology label. Enriches `Technology` nodes in the graph with real-world deployment evidence.

**Request**

```http
GET /technology/getTechDigest/label?techDigestLabel=OpenTelemetry HTTP/1.1
Host: apiorion.talentica.com
```

**Query parameters**

| Param | Type | Example |
|-------|------|---------|
| `techDigestLabel` | string | `OpenTelemetry` |

**Sample response**

```json
[
  {
    "digestId": 1305,
    "customerName": "SFA",
    "projectName": "Sports For All",
    "teamSize": 0,
    "startDate": "2023-10-01",
    "link": "https://prod-strapi-image-bucket.s3.ap-south-1.amazonaws.com/sfa%20logo.jpeg",
    "customerDescription": null
  },
  {
    "digestId": 1344,
    "customerName": "OpenGov",
    "projectName": "OpenGov",
    "teamSize": 0,
    "startDate": "2022-02-01",
    "link": "https://prod-strapi-image-bucket.s3.ap-south-1.amazonaws.com/opengov_inc_logo.jpeg",
    "customerDescription": null
  }
]
```

**Field reference**

| Field | Type | Sage use |
|-------|------|----------|
| `digestId` | number | Stable row identifier |
| `customerName` | string | Coverage evidence |
| `projectName` | string | Coverage evidence |
| `teamSize` | number | Context (often 0 in fixtures) |
| `startDate` | string (date) | Temporal context |

**Source:** Postman collection request for `getTechDigest/label` (see `orion-apis/sage ai.postman_collection.json`).

---

## 3. Tech categories

**Ingest step 1 (must run first).** Returns the full closed vocabulary of technology categories and tags. The seed script calls this first to get the list of tags used in steps 2 and 3.

**Request**

```http
GET /tech/categories HTTP/1.1
Host: apiorion.talentica.com
```

**Sample response**

```json
[
  {
    "id": 44,
    "name": "Web & Middleware",
    "sortOrder": 20,
    "link": "https://prod-strapi-image-bucket.s3.ap-south-1.amazonaws.com/web_and_middleware_0360a24e69.svg",
    "techDigestCount": 50,
    "valueAddCount": 32,
    "blogsCount": 6,
    "teamsCount": 31,
    "technologies": null,
    "teams": null
  },
  {
    "id": 46,
    "name": "Database",
    "sortOrder": 30,
    "techDigestCount": 55,
    "valueAddCount": 46,
    "blogsCount": 4,
    "teamsCount": 32,
    "technologies": null,
    "teams": null
  }
]
```

**Source:** Postman collection request for `tech/categories` (see `orion-apis/sage ai.postman_collection.json`).

---

## 4. Hard problems for financial year

**Ingest step 4 (gap-fill).** Large catalog payload (~6 MB fixture). Used after the tag fan-out to catch any HPs that were not reached via a tag. Filter `status = ACCEPTED` code-side before writing to Neo4j. Never loaded into LLM context.

> ⚠️ **D4** — Confirm `cycleId=8,7` covers the relevant financial cycles. Additional cycle IDs may be needed.

**Request**

```http
GET /customers/valueAdd/hardProblemsFinancialYear?cycleId=8,7 HTTP/1.1
Host: apidev-orion.talentica.com
```

**Query parameters**

| Param | Type | Example |
|-------|------|---------|
| `cycleId` | string (CSV) | `8,7` |

**Sample response**

```json
[
  {
    "id": 122829,
    "title": "Real-time 3-way merge engine for large scale project plans",
    "type": "HARD_PROBLEMS",
    "status": "ACCEPTED",
    "ownerId": [
      { "id": 663, "name": "Motaheer Hassan", "email": "motaheer.hassan@talentica.com", "userId": null }
    ],
    "teamId": {
      "id": 13,
      "teamName": "Realization",
      "teamShortName": "Realiztn-V",
      "teamJiraName": "Realization",
      "teamJiraLink": "https://realization.atlassian.net/",
      "teamType": "PROFITABLE",
      "status": "ACTIVE"
    },
    "ticketId": 8268,
    "ticketLink": "https://talenticacontact.freshdesk.com/a/tickets/8268",
    "tags": "Collaboration, Realtime Systems",
    "techDigests": "Conflict Resolution, Realtime",
    "techDigestModels": [
      { "id": 101, "label": "Conflict Resolution", "shortName": "CR", "version": "1.0", "category": "Architecture & Design" }
    ],
    "cycleId": 8,
    "impact": null,
    "infoLink": null
  }
]
```

**Note:** No `summary` field at the top level — unlike `valueAddsByTag` which nests summary under `fileDetails`.

**Source:** Postman collection request for `hardProblemsFinancialYear` (see `orion-apis/sage ai.postman_collection.json`).

---

## Authentication

> ⚠️ **D5** — Confirm the `api-key` value is current and stable for the POC seed run.

| Method | Header | Host | Used for |
|--------|--------|------|----------|
| API key | `api-key: <value>` | `apiorion.talentica.com` | Steps 1, 2, 3 (preferred for seed script) |
| Cookie | `Cookie: auth-token=<JWT>; cf_clearance=<value>` | `apidev-orion.talentica.com` | Step 4 (dev host; browser session only) |

Offline/stub mode: set `ORION_API_KEY=stub` → seed script uses local/offline data configured against the Postman collection; only `orion-apis/sage ai.postman_collection.json` is committed here.

## Pagination and errors

- No pagination parameters or envelope observed for any endpoint.
- Postman collection has empty `response` blocks — non-200 error shapes are unknown.
- In stub mode, unknown tags return `[]` (never throw).

## Validation

Shapes documented from the committed Postman collection. Live HTTP validation was not possible from the documentation environment (network restricted).
