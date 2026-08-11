# ArcLedger

ArcLedger is a stateful narrative intelligence engine for long-form fiction. It turns scenes into explicit, versioned entity memory; retrieves the newest canonical knowledge through synthetic questions; flags continuity breaks before they overwrite canon; and answers story questions with source references.

The name reflects the core architecture: every story arc is maintained as an auditable, append-only ledger of facts and state transitions—not a regenerated summary and not a generic chatbot.

## Why it exists

Long stories accumulate state: injuries persist, possessions move, relationships change, and characters learn things they cannot later forget without explanation. Chunk-only RAG often retrieves a semantically similar but obsolete passage. ArcLedger models narrative state directly and makes version validity a first-class retrieval signal.

## Processing architecture

```mermaid
flowchart TD
    A["New scene"] --> B["Structured entity and fact extraction"]
    B --> C["Entity resolution"]
    C --> D["Load latest canonical state"]
    D --> E["Deterministic reconciliation"]
    E -->|"ADD / UPDATE"| F["Append entity-state version"]
    E -->|"UNCHANGED"| G["Keep canonical state"]
    E -->|"CONTRADICTION"| H["Record continuity issue"]
    F --> I["Generate synthetic questions"]
    I --> J["Embed with version metadata"]
    J --> K["Semantic + entity + validity + recency retrieval"]
    K --> L["Grounded Q&A and scene validation"]
```

The application separates orchestration from focused services:

- `EntityExtractionService` calls a configured `LanguageModelClient` and deserializes structured JSON.
- `EntityResolutionService` maintains story-scoped entity identity.
- `EntityStateService` reads the active canonical facts.
- `StateReconciliationService` deterministically classifies `ADD`, `UPDATE`, `UNCHANGED`, and `CONTRADICTION`.
- `EntityStateVersionService` appends immutable snapshots and supersedes old facts without deleting them.
- `SyntheticQuestionGenerationService` creates multiple retrieval surfaces for each valid state.
- `NarrativeVectorStore` retains story, entity, version, scene, timestamp, and current/obsolete metadata.
- `NarrativeRetrievalService` filters obsolete state and makes ranking weights explicit.
- `ConsistencyValidationService` catches cross-fact continuity breaks such as two-handed actions after an arm loss.
- `NarrativeQuestionAnsweringService` returns grounded answers and source identifiers, or `INSUFFICIENT_CONTEXT`.

## High-level design (HLD)

### System context

ArcLedger exposes one REST boundary for authors, editors, and future writing tools. Internally, commands and queries share canonical narrative storage but follow separate paths: scene ingestion evolves memory, while retrieval reads only valid story state.

```mermaid
flowchart LR
    Client["Authoring tool / API client"] -->|"REST / JSON"| API["ArcLedger API"]
    API --> Ingestion["Narrative ingestion pipeline"]
    API --> Query["Retrieval and Q&A pipeline"]
    Ingestion --> LLM["Configured LLM provider"]
    Ingestion --> Canon["Canonical narrative store"]
    Ingestion --> Vector["Synthetic-question vector index"]
    Query --> Canon
    Query --> Vector
    Query --> LLM
```

The LLM is an interpretation boundary, not the source of truth. ArcLedger accepts structured extraction from the provider, then applies deterministic reconciliation and knowledge-kind rules before canonical state can change.

### Logical components

| Layer | Components | Responsibility |
| --- | --- | --- |
| API | `StoryController`, `SceneController`, `EntityController`, `QuestionController` | Validate requests, delegate work, and shape REST responses. |
| Orchestration | `NarrativeProcessingPipeline` | Coordinate scene ingestion without embedding domain rules in controllers. |
| Interpretation | `LanguageModelClient`, `EntityExtractionService`, prompt templates | Convert prose into typed entities, facts, changes, and evidence through a provider-neutral contract. |
| Canonical memory | `EntityResolutionService`, `EntityStateService`, `StateReconciliationService`, `EntityStateVersionService` | Resolve identity, classify mutations, preserve history, and expose the latest valid state. |
| Continuity | `ConsistencyValidationService` | Detect same-field and cross-field contradictions before state mutation. |
| Retrieval | `SyntheticQuestionGenerationService`, `EmbeddingService`, `NarrativeVectorStore`, `NarrativeRetrievalService` | Build and search synthetic-query vectors with story, entity, version, scene, and validity metadata. |
| Answering | `NarrativeQuestionAnsweringService` | Generate an answer only from current retrieved state and return provenance. |
| Persistence | Spring Data JPA repositories | Persist the story hierarchy, facts, versions, questions, and validation results. |

### Scene ingestion flow

```mermaid
sequenceDiagram
    actor Client
    participant API as Scene API
    participant Pipeline as Processing pipeline
    participant Extractor as Entity extraction
    participant Memory as Canonical memory
    participant Validator as Consistency validator
    participant Index as Synthetic-query index

    Client->>API: POST scene text
    API->>Pipeline: process(scene)
    Pipeline->>Extractor: extract structured entities and facts
    Extractor-->>Pipeline: entities, facts, intent, evidence
    loop Each resolved entity
        Pipeline->>Memory: load latest active facts
        Pipeline->>Validator: compare incoming facts with canon
        alt Valid ADD or UPDATE
            Validator-->>Pipeline: accepted mutation
            Pipeline->>Memory: append state version and supersede old facts
            Pipeline->>Index: generate and embed state questions
        else UNCHANGED
            Validator-->>Pipeline: retain current state
        else CONTRADICTION or ambiguous
            Validator-->>Pipeline: reject mutation and persist issue
        end
    end
    Pipeline-->>API: processed scene
    API-->>Client: scene ID and processing status
```

The state update and its version metadata are persisted together. A rejected fact creates evidence for review but cannot silently replace active canon.

### Grounded question-answering flow

```mermaid
sequenceDiagram
    actor Client
    participant API as Question API
    participant Retrieval as Narrative retrieval
    participant Index as Vector index
    participant Memory as Canonical memory
    participant QA as Grounded answering

    Client->>API: GET ask?query=...
    API->>Retrieval: retrieve(storyId, query)
    Retrieval->>Index: semantic search with story metadata filter
    Index-->>Retrieval: ranked synthetic questions
    Retrieval->>Retrieval: apply entity, validity, version, and recency signals
    Retrieval->>Memory: resolve current entity state and provenance
    Memory-->>Retrieval: active facts and source scenes
    Retrieval->>QA: grounded context only
    alt Sufficient canonical evidence
        QA-->>API: answer with scene and version references
    else Missing or ambiguous evidence
        QA-->>API: INSUFFICIENT_CONTEXT
    end
    API-->>Client: structured answer
```

### Data design

```mermaid
erDiagram
    STORY ||--o{ CHAPTER : contains
    STORY ||--o{ SCENE : owns
    CHAPTER ||--o{ SCENE : contains
    STORY ||--o{ NARRATIVE_ENTITY : defines
    NARRATIVE_ENTITY ||--o{ ENTITY_FACT : has
    NARRATIVE_ENTITY ||--o{ ENTITY_STATE_VERSION : versions
    SCENE ||--o{ ENTITY_STATE_VERSION : originates
    ENTITY_STATE_VERSION ||--o{ ENTITY_FACT : records
    ENTITY_STATE_VERSION ||--o{ SYNTHETIC_QUESTION : generates
    SCENE ||--o{ CONSISTENCY_RESULT : produces
    NARRATIVE_ENTITY ||--o{ CONSISTENCY_RESULT : concerns
```

`EntityFact.active` identifies the canonical value for a fact key. When an explicit change is accepted, the previous row becomes inactive and records the replacement fact ID. `EntityStateVersion` stores both the changed facts and the complete resulting state, providing an inspectable point-in-time snapshot without destroying earlier knowledge.

### Retrieval and consistency strategy

Retrieval is story-scoped first, then ranked using explicit signals:

```text
score = semantic similarity
      + entity-name relevance
      + current-version validity
      + recency
      - obsolete-state penalty
```

Current questions are eligible for grounded answers; obsolete questions remain available for history and auditing. Consistency validation uses the same current-state boundary, so an older fact cannot override a newer fact merely because its text is semantically closer.

### Deployment view

The current implementation is a single Spring Boot service with an embedded H2 database and an in-process vector adapter. These ports are intentionally replaceable: production deployment can move relational state to PostgreSQL, embeddings to a managed model, and vectors to pgvector or another ANN store without changing the domain pipeline.

```mermaid
flowchart TB
    subgraph Current["Local / evaluation deployment"]
        App["ArcLedger Spring Boot service"]
        H2["H2 relational store"]
        LocalVector["In-process vector adapter"]
        App --> H2
        App --> LocalVector
    end

    subgraph Production["Recommended production evolution"]
        LB["API gateway / load balancer"] --> Nodes["Stateless ArcLedger instances"]
        Nodes --> PG["PostgreSQL + pgvector"]
        Nodes --> Queue["Durable ingestion queue"]
        Nodes --> Providers["LLM and embedding providers"]
        Nodes --> Telemetry["Logs, metrics, and traces"]
    end
```

### Key design decisions

- **Append-only state history:** supports auditability, time travel, and reliable superseding.
- **Deterministic reconciliation first:** uses an LLM only where semantic interpretation is necessary.
- **Canonical-state isolation:** `INFERENCE` and `UNKNOWN` never become facts automatically.
- **Synthetic questions over chunk-only indexing:** aligns stored vectors with likely user questions and continuity checks.
- **Explicit ranking metadata:** prevents obsolete but semantically similar passages from dominating retrieval.
- **Provider ports:** keeps LLM, embedding, vector, and database choices replaceable.
- **Synchronous baseline:** keeps local operation simple; the service boundary permits later queue-based ingestion.

## Stateful RAG concepts demonstrated

| Concern | ArcLedger approach |
| --- | --- |
| Incremental memory | Atomic facts are added or explicitly updated per scene. |
| Versioned knowledge | Every accepted mutation creates an immutable state snapshot. |
| Superseding | Replaced facts remain in history and point to their replacement. |
| Synthetic-query retrieval | Natural questions, rather than raw chunks alone, are embedded. |
| Dense retrieval | A deterministic local embedding adapter provides a zero-secret baseline. |
| Metadata-aware ranking | Story/entity filters, version validity, and scene provenance are retained. |
| Recency awareness | Current-state and time-decay signals are explicit in scoring. |
| Hallucination control | Only `FACT` knowledge mutates canon; `INFERENCE` and `UNKNOWN` do not. |
| Plot-hole detection | Contradictions are persisted as structured results with evidence. |
| Grounded generation | Answers use current canonical hits and include source scene/version IDs. |

## Technology

- Java 17+
- Spring Boot 3.3
- Spring Web, Validation, Data JPA
- H2 for a low-infrastructure local store
- Pluggable LLM and embedding interfaces
- JUnit 5, AssertJ, Mockito, MockMvc

No API key is required for the built-in deterministic provider. For real model-backed extraction, the optional OpenAI Responses adapter uses JSON output through the provider-neutral `LanguageModelClient` boundary. See the [official Responses API documentation](https://platform.openai.com/docs/api-reference/responses).

## Run locally

```bash
git clone <repository-url>
cd arc-ledger
cp .env.example .env
set -a && source .env && set +a
mvn spring-boot:run
```

The default in-memory database is useful for quick evaluation. Loading `.env` switches to a persistent H2 file under `data/`, which is ignored by Git.

To enable model-backed extraction:

```bash
export ARCLEDGER_LLM_PROVIDER=openai
export OPENAI_API_KEY='your-key'
export ARCLEDGER_LLM_MODEL='gpt-4.1-mini'
mvn spring-boot:run
```

Prompt templates live in `src/main/resources/prompts/`; long prompts are not scattered through service classes.

## API walkthrough

Create a story:

```bash
curl -sS -X POST http://localhost:8080/stories \
  -H 'Content-Type: application/json' \
  -d '{"title":"The Last Meridian","description":"A city at the edge of time"}'
```

Create a chapter using the returned story ID:

```bash
curl -sS -X POST http://localhost:8080/stories/$STORY_ID/chapters \
  -H 'Content-Type: application/json' \
  -d '{"number":1,"title":"The Siege"}'
```

Submit scenes. Processing is automatic:

```bash
curl -sS -X POST http://localhost:8080/stories/$STORY_ID/chapters/$CHAPTER_ID/scenes \
  -H 'Content-Type: application/json' \
  -d '{"sequence":1,"rawText":"John has black hair. John is in London."}'

curl -sS -X POST http://localhost:8080/stories/$STORY_ID/chapters/$CHAPTER_ID/scenes \
  -H 'Content-Type: application/json' \
  -d '{"sequence":2,"rawText":"John loses his left arm during the battle."}'
```

Inspect memory and history:

```bash
curl -sS http://localhost:8080/stories/$STORY_ID/entities
curl -sS http://localhost:8080/stories/$STORY_ID/entities/$ENTITY_ID
curl -sS http://localhost:8080/stories/$STORY_ID/entities/$ENTITY_ID/history
```

Submit a suspicious scene and inspect its result:

```bash
curl -sS -X POST http://localhost:8080/stories/$STORY_ID/chapters/$CHAPTER_ID/scenes \
  -H 'Content-Type: application/json' \
  -d '{"sequence":3,"rawText":"John holds one sword in each hand."}'

curl -sS http://localhost:8080/stories/$STORY_ID/scenes/$SCENE_ID/consistency
```

Ask a grounded question:

```bash
curl -sS --get http://localhost:8080/stories/$STORY_ID/ask \
  --data-urlencode "query=What happened to John's left arm?"
```

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/stories` | Create a story. |
| `POST` | `/stories/{storyId}/chapters` | Add an ordered chapter. |
| `POST` | `/stories/{storyId}/chapters/{chapterId}/scenes` | Store and process a scene. |
| `GET` | `/stories/{storyId}/entities` | List current entity state. |
| `GET` | `/stories/{storyId}/entities/{entityId}` | Inspect one entity. |
| `GET` | `/stories/{storyId}/entities/{entityId}/history` | Inspect every state version. |
| `GET` | `/stories/{storyId}/ask?query=...` | Ask against current canon. |
| `GET` | `/stories/{storyId}/scenes/{sceneId}/consistency` | Read structured continuity results. |

## Tests

```bash
mvn test
mvn clean package
```

Coverage focuses on deterministic behavior: add/update/unchanged/contradiction classification, irreversible state, hallucination protection, fact superseding, version history, current-state retrieval, metadata filtering, recency/version preference, end-to-end plot-hole detection, grounded Q&A, and REST validation.

## Current limitations

- The zero-secret embedding adapter is a compact hashing baseline, not a production semantic model.
- The in-process vector store scans story questions and is intended for local evaluation and moderate corpora.
- The rule-based offline extractor recognizes a deliberately small set of narrative constructions; use the OpenAI adapter or add another `LanguageModelClient` for open-domain prose.
- Entity alias/coreference resolution currently uses normalized names rather than a learned identity model.
- Scene processing is synchronous; large manuscripts should move pipeline work to a durable queue.

## Roadmap

- PostgreSQL + pgvector adapter with ANN indexing and transactional metadata filters
- Production embedding providers and hybrid lexical/dense retrieval
- LLM-assisted semantic reconciliation behind deterministic safety rules
- Alias, pronoun, timeline, and temporal-interval resolution
- Relationship graph and event causality memory
- Async ingestion, retries, idempotency keys, and observability
- Evaluation datasets for retrieval freshness and contradiction precision/recall
- Authentication and multi-tenant story isolation

## Repository hygiene

Secrets, local databases, model files, Maven caches, build output, and IDE metadata are excluded. Copy `.env.example` for local configuration; never commit `.env`.
