# Orion API Documentation

HTTP reference for Orion endpoints used by Sage. Captured from `postman/sage ai.postman_collection.json` and validated against fixture payloads in `postman/`.

---

## Overview

Orion is the internal expertise and value-add catalog. Sage uses four GET endpoints in two lifecycles:

| Lifecycle | Caller | Purpose |
|-----------|--------|---------|
| **Ingest** | Python Graph RAG | Build graph nodes, edges, and vector embeddings from Orion records |
| **Ask** | Java ADK agents | Fetch structured records for Knowledge Card assembly at query time |

### Endpoint selection guide

| If you need… | Call this endpoint | Why |
|--------------|-------------------|-----|
| Teams, owners, extractive summary, document handle for a known tag | `valueAddsByTag` | Richest structured record; primary ask-time source |
| Customer/project coverage for a technology label | `getTechDigest/label` | Secondary evidence for confidence scoring |
| Closed vocabulary for tag extraction | `tech/categories` | Taxonomy lookup — not a direct answer source |
| Broad hard-problem discovery by financial cycle | `hardProblemsFinancialYear` | Catalog seed / fallback — too large for LLM context |

### Ask-time vs ingest-time (why both?)

The graph index is **built from** Orion, but ask-time Orion calls are **not redundant**:

| | Orion at ask-time | Graph `POST /retrieve` |
|---|---|---|
| Query style | Tag/label parameter | Natural language |
| Returns | Full `fileDetails` + `customersValueAdd` objects | Scored `passage` + slim metadata |
| Best for | Deterministic routing when tag is known | Fuzzy questions, multi-hop, synonym matching |
| Summary | Canonical `fileDetails.summary` field | Indexed passage chunk |
| Freshness | Live Orion | Last ingest sync |

**Agent mapping:**

| Endpoint | ADK agent / tool | Session key |
|----------|------------------|-------------|
| `valueAddsByTag` | `OrionValueAdd` → `valueAddsByTag(tag)` | `orion_value_add_hits` |
| `getTechDigest/label` | `OrionTechCoverage` → `techDigestByLabel(label)` | `orion_coverage_hits` |
| `tech/categories` | `TagExtractor` (pre-agent, not an LLM tool) | — |
| `hardProblemsFinancialYear` | Optional `OrionHardProblemsSearch` (not in default fan-out) | — |
| — | `GraphRagRetrieve` → `retrieveFromGraph(query)` | `graph_rag_hits` |

See [architecture.md §6](architecture.md#6-orion-at-ask-time-vs-ingest-time) for the full design rationale.

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

Stub mode (`ORION_API_KEY=stub`) reads committed `postman/*.json` fixtures — no live HTTP.

---

## 1. Value adds by tag

Primary ask-time endpoint. Returns expertise records with narrative summary and provenance.

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

**Fixture:** `postman/valueAddsByTag?tag=OpenTelemetry.json`

---

## 2. Tech digest by label

Secondary ask-time endpoint. Returns customer/project coverage rows for a technology label.

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

**Fixture:** `postman/https-::apiorion.talentica.com:technology:getTechDigest:label?techDigestLabel=OpenTelemetry.json`

---

## 3. Tech categories

Taxonomy endpoint. Used for tag vocabulary and ingest seeding — not a direct answer source.

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

**Fixture:** `postman/https-::apiorion.talentica.com:tech:categories.json`

---

## 4. Hard problems for financial year

Catalog endpoint. Large payload (~6 MB fixture). Used for ingest seeding and filtered code-side fallback — never loaded into LLM context.

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

**Fixture:** `postman/Hard Problems for the financial year.json`

---

## Pagination and errors

- No pagination parameters or envelope observed for any endpoint.
- Postman collection has empty `response` blocks — non-200 error shapes are unknown.
- In stub mode, unknown tags return `[]` (never throw).

## Validation

Shapes validated from committed fixtures only. Live HTTP validation was not possible from the documentation environment (network restricted).
