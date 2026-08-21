# Solution Design Document
## Pricing Domain Knowledge Platform (Domain RAG)

| Field | Value |
|---|---|
| **Document** | Solution Design Document (SDD) |
| **Version** | 0.9 — Draft for Architecture Review |
| **Author** | Animesh Kumar Sinha, Solution Architect |
| **Date** | August 2026 |
| **Status** | Draft |
| **Audience** | Engineering leadership, Data Governance, Platform Architecture, Legal/HR (transcript scope) |

---

## 1. Executive Summary

### 1.1 Problem Statement

Pricing domain knowledge at Kroger is fragmented across PDFs, Confluence pages, GitHub repositories, Excel configuration workbooks, Java and Python source code, architecture diagrams, database schemas, and recorded meetings. There is no single authoritative place where a new engineer, an analyst, or a manager can ask *"What is a rebalance rule?"* or *"How does the rule engine work?"* and receive a correct, current, and citable answer.

The cost of this fragmentation shows up as repeated onboarding effort, inconsistent definitions across teams, and senior engineers acting as human search indexes.

### 1.2 Proposed Solution

A governed, incrementally-synchronised retrieval platform built on the medallion architecture already standard at Kroger, exposing pricing domain knowledge through two surfaces:

1. **A conversational assistant** (Teams / web) for human question answering.
2. **An MCP server** that makes the same retrieval capability available as tools to any downstream AI system.

The design's central architectural commitment: **the Delta table is the source of truth; the vector index is a derived, disposable artifact.** Every hard requirement — incremental updates, deduplication, deletion propagation, embedding-model migration, access control — becomes a data-engineering problem with known solutions, rather than bespoke vector-store logic.

### 1.3 Key Design Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Delta table as source of truth; index derived | Makes ingestion idempotent; eliminates duplicate-embedding class of bugs by construction |
| D-2 | Deterministic `chunk_id`, separate `content_hash` | Stable identity for MERGE; hash for change detection |
| D-3 | Hybrid retrieval (dense + BM25 + RRF + rerank) | Domain vocabulary is exact-match heavy; vector-only under-performs |
| D-4 | Contextual retrieval at ingest | Largest single measured quality lever available |
| D-5 | Retrieval service first, chat UI second | Chat is one client of many; MCP is the strategic surface |
| D-6 | Curated glossary tier above general RAG | Definitional questions are the dominant workload and demand precision |
| D-7 | Linear pipeline in v1; agentic deferred | Agentic complexity added only when evaluation proves it necessary |
| D-8 | Unity Catalog governance from day one | ACL filtering at retrieval time, not post-generation |

### 1.4 Success Criteria

| Metric | Target (end of Phase 1) |
|---|---|
| Retrieval Recall@20 on golden set | ≥ 0.85 |
| Answer faithfulness (LLM-judge) | ≥ 0.80 |
| Answers carrying a valid citation | 100% |
| P95 end-to-end latency | < 6 s |
| Weekly active users (target org) | ≥ 40 |
| Zero ACL violations in audit sample | Mandatory gate |

---

## 2. Scope

### 2.1 In Scope (Phases 1–3)

- Pricing domain documentation: PDFs, Confluence spaces, design documents
- Pricing rule configuration workbooks (Excel)
- Pricing engine source code: Java, Python
- Architecture and flow diagrams (images)
- Database schema and data-dictionary metadata
- Curated glossary of domain terms
- Conversational assistant and MCP retrieval service

### 2.2 Out of Scope (v1)

- Live transactional pricing data ("what is the current price of SKU X") — routed to existing systems, not this platform
- Customer-facing or store-associate-facing surfaces
- Meeting and call recordings — **conditionally deferred** pending Legal/HR determination (see §9.3)
- Write actions of any kind; the platform is read-only
- Automated decision-making; all output is advisory and cited

### 2.3 Explicit Non-Goals

The platform is not a system of record. It does not resolve contradictions between sources; it surfaces them with provenance and lets a human adjudicate. This is a deliberate design position, not a limitation to be engineered away.

---

## 3. Requirements

### 3.1 Functional

| ID | Requirement | Priority |
|---|---|---|
| FR-1 | Answer natural-language questions about pricing domain concepts | Must |
| FR-2 | Every answer cites source document, section, and last-modified date | Must |
| FR-3 | Retrieve across heterogeneous formats in a single query | Must |
| FR-4 | Reflect source changes within one business day | Must |
| FR-5 | Never surface content the requesting user lacks entitlement to | Must |
| FR-6 | Expose retrieval as callable tools to other AI systems | Should |
| FR-7 | Surface curated canonical definitions in preference to inferred ones | Should |
| FR-8 | Indicate when sources disagree or content is stale | Should |
| FR-9 | Route quantitative questions to structured query rather than retrieval | Could |

### 3.2 Non-Functional

| ID | Requirement | Target |
|---|---|---|
| NFR-1 | P95 query latency | < 6 s |
| NFR-2 | Availability | 99.5% business hours |
| NFR-3 | Ingestion idempotency | Re-running any job produces identical state |
| NFR-4 | Auditability | Every retrieval logged with user, query, chunks returned |
| NFR-5 | Embedding cost | < $X/month steady state (unchanged docs never re-embedded) |
| NFR-6 | Corpus scale | 50k–500k chunks at Phase 3 |

---

## 4. Architecture Overview

### 4.1 Logical Layers

```
┌──────────────────────────────────────────────────────────────┐
│  CONSUMPTION                                                 │
│  Teams Bot  │  Web UI  │  MCP Server  │  Future agents       │
└───────────────────────┬──────────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────────┐
│  RETRIEVAL SERVICE  (the product; everything above is a client)│
│  Glossary lookup → Hybrid search → RRF → Rerank → Assemble    │
│  ACL filter (pre-retrieval)  │  Authority/freshness boost     │
└───────────────────────┬──────────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────────┐
│  INDEX LAYER  (derived, disposable)                           │
│  Vector index (Delta Sync)   │   BM25 / lexical index         │
└───────────────────────▲──────────────────────────────────────┘
                        │  Change Data Feed
┌───────────────────────┴──────────────────────────────────────┐
│  GOLD:   chunk table — the source of truth                    │
│  SILVER: parsed + chunked + contextualised                    │
│  BRONZE: raw captured artifacts + manifest                    │
└───────────────────────▲──────────────────────────────────────┘
                        │
┌───────────────────────┴──────────────────────────────────────┐
│  INGESTION CONNECTORS                                         │
│  Confluence │ SharePoint │ GitHub │ Excel │ DB catalog │ …    │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 Why the Delta Table Is the Source of Truth

Databricks Delta Sync detects additions, updates, and deletions through the Delta transaction log and propagates them to the vector index — embedding new content, updating changed content, removing deleted content — without custom pipeline code. On subsequent syncs, generated embeddings for rows that have not changed are reused rather than recomputed.

This single choice satisfies FR-4, NFR-3, and NFR-5 simultaneously, and removes the entire category of "duplicate embedding on re-ingest" defects that dominate hand-rolled RAG pipelines.

---

## 5. Data Model

### 5.1 Gold Chunk Table Schema

| Column | Type | Purpose |
|---|---|---|
| `chunk_id` | STRING (PK) | `sha256(source_system + source_uri + chunk_ordinal)` — **stable identity** |
| `doc_id` | STRING | `sha256(source_system + source_uri)` |
| `content_hash` | STRING | `sha256(normalized_chunk_text)` — change detection only |
| `chunk_text` | STRING | Contextualised text; the embedded column |
| `raw_chunk_text` | STRING | Pre-contextualisation text, for display |
| `chunk_ordinal` | INT | Position within document |
| `source_system` | STRING | confluence / github / sharepoint / excel / dbcatalog |
| `source_uri` | STRING | Deep link back to origin |
| `doc_type` | STRING | design_doc / code / config / diagram / schema |
| `authority_tier` | INT | 1 = code, 2 = current Confluence, 3 = design doc, 4 = transcript |
| `acl_groups` | ARRAY\<STRING\> | Entitlement groups permitted to see this chunk |
| `source_last_modified` | TIMESTAMP | From source system |
| `last_verified` | TIMESTAMP | Last SME confirmation, where applicable |
| `embedding_model_version` | STRING | Enables selective re-index on model upgrade |
| `chunking_strategy_version` | STRING | Enables selective re-chunk on strategy change |
| `is_active` | BOOLEAN | Soft delete |
| `ingested_at` | TIMESTAMP | Pipeline audit |

### 5.2 Supporting Tables

- **`document_manifest`** — one row per document: `doc_id`, `doc_hash`, `chunk_count`, `last_ingested`. Enables the document-level short-circuit.
- **`glossary`** — curated canonical definitions: `term`, `definition`, `owner`, `approved_date`, `review_due`, `related_terms`, `source_refs`.
- **`source_inventory`** — periodic snapshot of what exists at each source, for delete reconciliation.
- **`query_log`** — every question, user, retrieved chunk IDs, answer, feedback. Feeds both audit and evaluation.

---

## 6. Ingestion Design

### 6.1 The Incremental Update Algorithm

This is the core of the design and the primary answer to the "how do I avoid duplicates when content changes" requirement.

```
FOR each document at source:
  1. doc_hash = sha256(raw_bytes)
  2. IF doc_hash == manifest[doc_id].doc_hash:
        SKIP entirely — no parse, no chunk, no embed
        (this is the cost-control step; ~95% of documents on a typical run)
  3. ELSE:
        parse → chunk → contextualise
        FOR each new chunk:
            chunk_id     = sha256(source_system + source_uri + ordinal)
            content_hash = sha256(normalized_text)
        diff new chunk_hashes against stored chunk_hashes for this doc_id
        →  embed ONLY chunks whose content_hash is new
        →  MERGE INTO gold_chunks ON chunk_id
        →  soft-delete chunk_ids present before but absent now
  4. update manifest[doc_id]
```

**Why `chunk_id` is not a content hash.** Stable identifiers are required to correlate a chunk across versions. If identity were derived from content, every edit would produce a new row rather than an update — which is precisely the duplicate-proliferation failure mode this design exists to prevent.

**Why `MERGE INTO` rather than insert.** MERGE on a stable primary key is idempotent. Running the same ingestion job five times yields identical table state. This is the guarantee that makes reprocessing safe and makes backfills non-frightening.

### 6.2 Deletion Handling

Deletions do not announce themselves. Three mechanisms in combination:

1. **Soft delete on absence** — chunk present in prior version, absent now → `is_active = false`.
2. **Weekly reconciliation** — full inventory comparison of source vs `source_inventory`; documents removed at source are deactivated wholesale.
3. **Webhook-driven hard signals** where a source provides them (Confluence page delete events, GitHub file deletions in a diff).

Reconciliation is the safety net; without it, deleted content persists silently and the platform starts confidently citing documents that no longer exist.

### 6.3 Sync Cadence

| Layer | Cadence | Mechanism |
|---|---|---|
| High-churn sources (Confluence, GitHub) | Hourly | Triggered sync |
| Stable sources (PDF repositories) | Nightly | Triggered sync |
| Full reconciliation | Weekly | Inventory diff |
| Full rebuild | Monthly | Corrects accumulated drift, optimises index structure |

Frequent incremental updates plus periodic full re-indexing is the standard balance of efficiency, freshness, and consistency. Continuous sync mode is available but provisions a persistent streaming cluster and is not justified by any Phase 1 requirement.

### 6.4 Embedding Model Migration

Upgrading the embedding model produces vectors incompatible with existing ones. The `embedding_model_version` column enables the standard mitigation: maintain a parallel index on the new model, backfill in the background, run both behind the retrieval service, compare on the golden set, cut over, then retire the old index. Without the version column this becomes a full-corpus outage.

---

## 7. Format-Specific Processing

This section carries disproportionate weight. Retrieval quality is set here, before any query is ever run.

### 7.1 PDF and Confluence

Layout-aware parsing (`ai_parse_document`, Azure Document Intelligence, or Unstructured). Preserve heading hierarchy as chunk metadata. Convert tables to markdown rather than flattening to prose — flattened tables are among the most reliable sources of confidently wrong answers.

Chunking: section-aware with parent-child linkage. Retrieve the small chunk; supply the parent section to the model.

### 7.2 Excel Workbooks

Excel requires bifurcation by intent, not a single strategy:

| Sheet character | Treatment |
|---|---|
| **Rule / config sheets** (tens to hundreds of rows, each row is a business fact) | One chunk per row, serialised to natural language with column headers inlined: `"Rule REB_004: type=Rebalance, trigger=margin_delta, threshold=0.03, owner=Pricing Ops, effective=2025-04-01"` |
| **Large data sheets** (thousands of rows of transactional or reference data) | **Do not embed.** Register as a governed table; route these questions to text-to-SQL (Phase 3) |
| **Narrative sheets** (notes, README tabs) | Treat as prose; standard document chunking |

The critical judgement: a workbook is not a document. Row-level serialisation is what makes "what is rebalance rule REB_004" answerable; naïve whole-sheet chunking makes it unanswerable.

### 7.3 Source Code (Java, Python)

AST-based chunking via tree-sitter. One chunk per function or class, with a synthetic header carrying repository, file path, package/class, and docstring or leading comment.

Chunking strategy has a statistically significant effect on retrieval-augmented code quality — this is empirically established, not stylistic preference. Fixed-size chunking of source files splits methods mid-body and produces chunks that are syntactically meaningless and semantically misleading.

Additional code-specific enrichment worth the effort:
- Index the **call graph** as metadata, so "how does the rule engine work" can traverse from entry point to implementation.
- Index **commit messages and PR descriptions** separately — they are frequently the only place a design rationale was ever written down.
- Index **test files** — tests are executable specifications and often the clearest statement of intended behaviour.

### 7.4 Diagrams and Images

A vision model generates a structured description at ingest time. The description is embedded; the image URI is stored and rendered alongside the answer. Diagrams are disproportionately valuable for "how does X work" questions and disproportionately neglected in RAG builds.

### 7.5 Database Schema and Data Dictionary

Harvest from Unity Catalog: table names, column names, comments, lineage. Serialise each table as a chunk. This makes "which table holds the rebalance thresholds" answerable and is the prerequisite for Phase 3 text-to-SQL.

### 7.6 Contextual Retrieval (Applied to All Formats)

Before embedding, prepend to each chunk a one-to-two sentence LLM-generated statement situating it within its parent document.

Anthropic's published measurements: contextual embeddings alone reduced top-20 retrieval failure by 35%; combined with contextual BM25, 49%; with reranking added, 67% (5.7% → 1.9% failure rate).

This matters acutely for the target workload. A user asks *"what is a rebalance rule?"*; the chunk that actually defines it sits in section 4.2 of a design document and may never repeat the phrase. The prepended context supplies both the semantic framing the embedding needs and the exact vocabulary BM25 needs.

Cost is controlled with prompt caching — the parent document is cached across all its chunks.

---

## 8. Retrieval Design

### 8.1 Pipeline

```
Query
  ├─ 1. ACL resolution → user's entitlement groups
  ├─ 2. Glossary lookup (exact + fuzzy term match)
  │       └─ hit → return canonical definition + offer supporting detail
  ├─ 3. Query analysis → filters (source, doc_type, recency)
  ├─ 4a. Dense vector search (top 50, ACL-filtered)
  ├─ 4b. BM25 lexical search (top 50, ACL-filtered)
  ├─ 5. Reciprocal Rank Fusion → deduplicate
  ├─ 6. Cross-encoder rerank → top 20
  ├─ 7. Authority + freshness boost
  ├─ 8. Parent-context expansion
  └─ 9. Generate with citation-mandatory prompt
```

### 8.2 Why Hybrid Is Non-Negotiable

Hybrid retrieval consistently outperforms vector-only in production because it captures both semantic meaning and exact terminology — a distinction that matters most in domains with dense specialist vocabulary.

The pricing domain is exactly such a domain: `EDLP`, `clear pricing`, `rebalance rule`, rule IDs, table names, ticket references, class names. Dense embeddings smear these together; BM25 matches them exactly. Neither alone is sufficient.

### 8.3 Authority Tiering

The same term will be defined differently in a 2021 Confluence page, a current design document, and the running code. The platform does not attempt to resolve this. It ranks by authority tier and freshness, and it always cites source and date so that a human can adjudicate.

| Tier | Source | Rationale |
|---|---|---|
| 1 | Curated glossary | SME-approved, explicitly owned |
| 2 | Production source code | Describes what actually happens |
| 3 | Current Confluence (modified < 12 months) | Maintained intent |
| 4 | Design documents | Point-in-time intent, may be superseded |
| 5 | Meeting transcripts | Conversational, unverified, frequently speculative |

Chunks older than a configurable threshold are flagged in the answer rather than suppressed.

### 8.4 The Glossary Tier

For the dominant workload — definitional questions — the highest return on effort is a curated table of 150–300 canonical terms, owned by named SMEs, with review dates.

Workflow: the RAG drafts candidate definitions from the corpus; SMEs approve, edit, or reject; approved entries become tier 1. This is what makes the platform's output defensible when it reaches management. It also converts the platform from a search tool into a governance artifact, which materially improves its organisational standing.

---

## 9. Governance and Security

### 9.1 Access Control

ACLs are enforced as a **pre-retrieval metadata filter**, never as post-generation redaction. A user must never receive a chunk from a document they could not open directly.

Unity Catalog provides the governance substrate: vector indexes sit under the same three-level namespace, inherit permissions, and emit standard audit logs. `acl_groups` is populated at ingest from the source system's own permission model and refreshed on every sync — stale ACLs are a security defect, not a data-quality one.

### 9.2 Audit

Every query logs: user identity, question text, chunk IDs retrieved, chunk IDs cited, answer text, timestamp. Retained per Kroger data-retention policy. This log is simultaneously the security audit trail and the evaluation dataset.

### 9.3 Meeting and Call Recordings — Conditional Deferral

Recorded conversations carry materially different risk from documents: participants did not author them with distribution in mind; they may contain personnel matters, commercially sensitive negotiation content, or speculative statements later reversed.

**Recommendation:** exclude from Phase 1–3. Seek explicit Legal and HR determination before any ingestion, covering consent basis, retention, participant notification, and whether transcript content may be surfaced to users who were not in the meeting. Treat approval as a prerequisite, not a formality.

This is the item most likely to stall the programme if discovered late. Raise it in week one.

### 9.4 Model and Output Governance

- All generation is grounded and citation-mandatory; the system prompt forbids answering from parametric knowledge.
- Explicit "I don't have enough information" path — an unanswered question is a far better outcome than a fabricated one, and the golden set must include unanswerable questions to verify this behaviour.
- No write actions, no autonomous execution, in any phase.

---

## 10. Evaluation

### 10.1 Golden Dataset — Build This First

A golden dataset is a set of (question, expected answer, expected source chunks) triples with human-verified ground truth. It is the step most teams skip and the highest-value investment in evaluation quality.

**Composition target (~150 questions):**

| Category | Count | Purpose |
|---|---|---|
| Definitional ("what is clear pricing") | 50 | Primary workload |
| Procedural ("how does the rule engine work") | 30 | Multi-chunk synthesis |
| Locational ("where is rebalance implemented") | 20 | Code retrieval |
| Comparative ("difference between X and Y") | 20 | Multi-hop |
| Adversarial (ambiguous, near-miss terminology) | 15 | Precision under confusion |
| **Unanswerable** | 15 | Verifies refusal behaviour |

Collected from SMEs and from real onboarding questions **before pipeline construction begins**. It defines "done", it is the regression gate on every change, and it is the artifact that justifies further investment to leadership.

### 10.2 Metrics

Retrieval first — it costs nothing to compute and localises most failures immediately:

| Metric | Target |
|---|---|
| Recall@20 | ≥ 0.85 |
| Precision@5 | ≥ 0.70 |
| MRR | ≥ 0.60 |
| NDCG@10 | ≥ 0.70 |

Then generation, LLM-judged:

| Metric | Target |
|---|---|
| Faithfulness | ≥ 0.80 |
| Context precision | ≥ 0.70 |
| Context recall | ≥ 0.80 |
| Answer relevancy | ≥ 0.80 |

The diagnostic discipline that matters: low recall points at chunking, top-k, or embedding-domain mismatch. Low faithfulness with healthy recall points at the prompt. Measuring only end-to-end answer quality tells you something is wrong without telling you where.

### 10.3 Regression Harness

The golden set runs in CI on every pipeline change. A chunking tweak that improves definitional questions while quietly breaking code retrieval is the normal case, not the exception — the harness exists to catch it.

### 10.4 Production Monitoring

- Thumbs up/down on every answer, written to `query_log`
- Weekly review of low-rated and zero-retrieval queries
- Zero-retrieval queries are the highest-signal input to glossary expansion
- Drift monitoring on retrieval score distributions

---

## 11. Options Analysis — Feasible Solution Paths

Four viable paths exist. They are presented with genuine trade-offs rather than as a foregone conclusion.

### Option A — Databricks-Native Build (Recommended)

Mosaic AI Vector Search with Delta Sync, Unity Catalog governance, Databricks model serving, custom ingestion in the existing platform.

| | |
|---|---|
| **Strengths** | Governance and lineage inherited rather than rebuilt; no second copy of enterprise data; Delta Sync eliminates the hardest custom code; sits inside existing platform skills and cost centre; full control over format-specific processing, which is where quality lives for this corpus |
| **Weaknesses** | Genuine engineering effort; connector work is unglamorous and underestimated; no vendor to escalate to |
| **Effort** | ~14 weeks to Phase 2 |
| **Best when** | Data already lives in the platform; governance is non-negotiable; corpus needs format-specific handling |

### Option B — Turnkey Enterprise Search (Glean, Onyx, Dust)

| | |
|---|---|
| **Strengths** | Fastest time to value; connector count exceeds 100 apps out of the box; permission-aware results respecting source ACLs; polished UX |
| **Weaknesses** | Glean indexes and stores a **centralised copy** of enterprise data — for regulated data this expands audit scope and attack surface, a concern hybrid and federated architectures avoid. Generic chunking will not handle AST-aware code or row-level Excel serialisation. Pricing is opaque and enterprise-tier. Onyx (MIT, self-hostable) mitigates the data-copy and cost objections but requires operational ownership. |
| **Effort** | 4–8 weeks |
| **Best when** | Breadth across many SaaS tools matters more than depth on a specialist corpus |

### Option C — Hyperscaler Managed RAG (Azure AI Search, Bedrock KB, Vertex)

| | |
|---|---|
| **Strengths** | Zero-ops within the cloud ecosystem; strong when data already lives there |
| **Weaknesses** | Single-cloud lock-in, including model choice; sits outside Unity Catalog, creating a second governance perimeter for the same data |
| **Effort** | 6–10 weeks |
| **Best when** | The organisation is decisively single-cloud and governance can be centralised there |

### Option D — Framework Assembly (LangChain/LlamaIndex + Pinecone/Weaviate)

| | |
|---|---|
| **Strengths** | Maximum control; portable; large community |
| **Weaknesses** | You own the connector framework, permission model, chunking pipeline, reranker integration, chat surface, and every synchronisation edge case. The incremental-sync logic in §6.1 becomes bespoke code you maintain forever. |
| **Effort** | 20+ weeks |
| **Best when** | Requirements are genuinely unserved by platform options |

### 11.1 Decision Matrix

| Criterion | Weight | A: Databricks | B: Turnkey | C: Hyperscaler | D: Framework |
|---|---|---|---|---|---|
| Governance / UC integration | 25% | 5 | 3 | 2 | 2 |
| No secondary data copy | 15% | 5 | 2 | 3 | 3 |
| Format-specific quality (code, Excel) | 20% | 5 | 2 | 3 | 5 |
| Time to first value | 15% | 3 | 5 | 4 | 1 |
| Extensibility to agents / MCP | 10% | 5 | 3 | 3 | 5 |
| Total cost of ownership (3 yr) | 15% | 4 | 2 | 3 | 3 |
| **Weighted score** | | **4.55** | **2.85** | **2.90** | **3.05** |

### 11.2 Recommendation and Honest Caveat

**Option A**, with two qualifications stated plainly:

1. **A hybrid is legitimate.** If broad SaaS-wide search is separately desired, a turnkey platform for general enterprise search alongside a specialist domain platform for pricing is a defensible architecture. They serve different problems and the specialist corpus is where generic tools underperform.

2. **In-house builds fail more often than vendor deployments.** MIT's 2025 GenAI Divide report found 95% of enterprise GenAI pilots fail to reach measurable P&L impact, with vendor-partner deployments succeeding roughly 67% of the time versus 33% for in-house builds. This is a real base rate and should not be waved away.

   The mitigations built into this design are deliberate responses to it: a narrow Phase 1, a golden set defining success before construction, a named SME for the glossary, and measured recall as the gate for further funding. If those mitigations are not staffed, Option B becomes the more honest recommendation regardless of the matrix score.

---

## 12. Phased Delivery Plan

### Phase 0 — Foundation (Weeks 1–2)

| Deliverable | Owner |
|---|---|
| Golden question set (~150, SME-sourced) | Architect + SMEs |
| Source inventory with volume and churn estimates | Architect |
| ACL and entitlement group mapping | Architect + Security |
| Legal/HR determination request on transcripts | Architect + Legal |
| Named glossary owner identified | Engineering leadership |

**Gate:** golden set signed off. No pipeline code before this.

### Phase 1 — Narrow and Excellent (Weeks 3–8)

Scope: PDFs and Confluence only.

- Bronze/Silver/Gold pipeline with §6.1 incremental algorithm
- Contextual retrieval at ingest
- Hybrid retrieval + RRF + reranking
- ACL filtering, UC governance
- Teams bot with mandatory citations
- Evaluation harness in CI

**Gate:** Recall@20 ≥ 0.85, faithfulness ≥ 0.80 on golden set. Pilot with one team.

### Phase 2 — Breadth (Weeks 9–14)

- GitHub connector, AST chunking, call-graph metadata
- Excel connector with bifurcated processing
- Diagram/image descriptions
- Curated glossary live, SME workflow operational
- Reconciliation and delete handling hardened

**Gate:** golden set maintained above Phase 1 thresholds with 3× corpus.

### Phase 3 — Platform (Weeks 15–20)

- MCP server exposing `search_pricing_knowledge`, `get_definition`, `find_code_for_rule`, `find_owning_team`
- Query router: glossary / documents / code / structured
- Text-to-SQL over registered pricing tables
- Database catalog ingestion

**Gate:** at least one downstream system consuming via MCP.

### Phase 4 — Conditional

Agentic retrieval loop, multi-hop planning, self-correction.

**Precondition:** evaluation must demonstrate a specific, quantified class of question that single-shot retrieval fails and agentic retrieval solves. Agentic complexity is where budget is consumed and quality often does not improve. It is not a default destination.

---

## 13. Risks

| ID | Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|---|
| R-1 | Transcript ingestion blocked or delayed by Legal/HR | Med | High | Excluded from v1; determination requested week 1 |
| R-2 | ACL mapping proves incomplete at a source | High | Med | Fail-closed default; audit sample before pilot; no launch without clean audit |
| R-3 | Scope expands to all six formats in v1 | High | **High** | Phase gates with explicit deliverables; this is the single most likely cause of failure |
| R-4 | Contradictory definitions erode trust | Med | High | Authority tiering + mandatory citation + glossary; contradiction surfaced, not hidden |
| R-5 | Glossary has no sustained SME owner | Med | Med | Named owner is a Phase 0 gate; review dates enforced |
| R-6 | Embedding cost exceeds forecast | Low | Low | Document-level short-circuit; unchanged rows never re-embedded |
| R-7 | Low adoption after launch | High | Med | Pilot with one team; instrument from day one; zero-retrieval queries drive glossary |
| R-8 | Users treat output as authoritative rather than advisory | Med | Med | Citation-mandatory UX; explicit framing in bot; staleness flags |
| R-9 | Embedding model upgrade forces full outage | Med | Low | `embedding_model_version` column; dual-index migration |

---

## 14. Open Questions

1. Which Databricks workspace and Unity Catalog schema will host the platform, and who owns the cost centre?
2. What is the authoritative entitlement group source — AD, Okta, or per-system ACLs — and can it be resolved at query time within latency budget?
3. Which specific Confluence spaces and GitHub repositories constitute the Phase 1 corpus, and who signs off on that boundary?
4. Who is the named glossary owner, and what is their committed time allocation?
5. Does an existing Kroger LLM gateway or approved-model list constrain the generation and reranking model choices?
6. What is the transcript determination timeline, and does it gate Phase 3 planning?
7. Is there an existing enterprise search initiative that this should federate with rather than duplicate?

---

## 15. Appendix — Design Principles

The principles below are the ones that should survive contact with implementation pressure.

1. **The index is disposable; the table is not.** Any design that cannot be rebuilt from the Gold table is wrong.
2. **Identity is deterministic; content is hashed.** Never conflate the two.
3. **Idempotency is a gate, not an aspiration.** If re-running a job changes state, the job is defective.
4. **Filter before you retrieve.** ACL enforcement after generation is not enforcement.
5. **Cite always.** An uncited answer is an unverifiable claim.
6. **Refuse cleanly.** "I don't know" is a correct answer and must be tested for.
7. **Measure retrieval before generation.** Most failures are retrieval failures wearing a generation costume.
8. **Narrow and excellent beats broad and mediocre.** The corpus can always grow; lost trust rarely returns.
9. **Contradiction is surfaced, not resolved.** The platform informs judgement; it does not replace it.
10. **Complexity must be earned by evidence.** Agentic retrieval, graph RAG, and multi-agent orchestration are answers to measured problems, not starting positions.
