# Shopping guide agent iteration 3: PGvector hybrid retrieval

## Architecture boundary

MySQL remains the only source of truth for products, prices, availability and
inventory. PostgreSQL is introduced only as an Agent-side vector index.

```text
MySQL product tables
  -> service-product allowlisted knowledge endpoint
  -> Ollama bge-m3 embeddings
  -> PostgreSQL 16 + PGvector HNSW index
  -> keyword/vector reciprocal-rank fusion
  -> realtime SKU validation through service-product
  -> DeepSeek shopping guide response
```

The vector document does not contain:

- Cost price or exact stock quantity
- User, address, order, payment or cart data
- Internal inventory operation fields

The stored fields are limited to product/SKU IDs, brand/category IDs,
product/SKU names, category path, public specifications, unit, content hash
and update time.

## Local infrastructure

Pinned development versions:

```text
PostgreSQL 16 + pgvector 0.8.2
Ollama 0.32.3
bge-m3 embedding model
```

Pull the images:

```powershell
docker pull pgvector/pgvector:0.8.2-pg16-bookworm
docker pull ollama/ollama:0.32.3
```

Deploy PGvector. The script prompts for credentials when the corresponding
environment variables are absent:

```powershell
$env:AGENT_PGVECTOR_USERNAME = "zjzx_agent"
$env:AGENT_PGVECTOR_PASSWORD = "<local password>"
.\scripts\docker\deploy-pgvector.ps1
```

Deploy Ollama and download `bge-m3`:

```powershell
.\scripts\docker\deploy-ollama-embedding.ps1
```

Both ports are bound to localhost:

```text
PGvector: 127.0.0.1:5432
Ollama:   127.0.0.1:11434
```

The Agent uses Spring AI PGvector `TEXT` document IDs, so the deployment
requires only the `vector` and `hstore` extensions. `uuid-ossp` is not needed.

## Agent environment

Keep the iteration 2 DeepSeek variables and add:

```text
SPRING_AI_MODEL_EMBEDDING=ollama
SPRING_AI_VECTORSTORE_TYPE=pgvector
AGENT_VECTOR_ENABLED=true
AGENT_PGVECTOR_URL=jdbc:postgresql://127.0.0.1:5432/zjzx_agent
AGENT_PGVECTOR_USERNAME=zjzx_agent
AGENT_PGVECTOR_PASSWORD=<local password>
AGENT_PGVECTOR_INITIALIZE_SCHEMA=true
AGENT_PGVECTOR_SCHEMA_VALIDATION=false
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_EMBEDDING_MODEL=bge-m3
OLLAMA_EMBEDDING_KEEP_ALIVE=30m
```

The first Agent startup creates `product_knowledge_vector` and its HNSW
cosine-distance index. Spring AI 1.1.8 validates before it initializes, so
schema validation must remain `false` on the first startup. After the first
successful startup, use:

```text
AGENT_PGVECTOR_INITIALIZE_SCHEMA=false
AGENT_PGVECTOR_SCHEMA_VALIDATION=true
```

When vector-related variables are absent, the Agent starts in the existing
keyword-only mode and does not require Ollama or PGvector.

## Full index rebuild

Restart `service-product` first so the knowledge paging endpoint is available,
then start the Agent with vector retrieval enabled.

The rebuild endpoint is internal and must be called directly on port 8520.
The gateway rejects `/**/internal/**`.

```http
POST http://127.0.0.1:8520/api/agent/internal/index/products/rebuild
X-Internal-Token: <ZJZX_INTERNAL_API_TOKEN>
```

Status:

```http
GET http://127.0.0.1:8520/api/agent/internal/index/products/status
X-Internal-Token: <ZJZX_INTERNAL_API_TOKEN>
```

Successful response example:

```json
{
  "state": "SUCCEEDED",
  "indexedCount": 25,
  "startedAt": "2026-07-28T03:30:00Z",
  "completedAt": "2026-07-28T03:30:08Z",
  "message": "Product knowledge index rebuild completed"
}
```

Each rebuild uses stable `product-sku-{skuId}` IDs and a new generation.
Documents are upserted first. Old-generation documents are deleted only after
all new documents have been embedded and written successfully.

## Hybrid retrieval behavior

The existing public API is unchanged:

```http
POST /api/agent/auth/guide/chat
```

For a product question:

1. Query the live MySQL-backed keyword endpoint.
2. Query PGvector with HNSW cosine similarity.
3. Merge both rankings using reciprocal-rank fusion.
4. Re-query vector-only SKU candidates through `service-product`.
5. Discard unavailable, deleted or off-shelf candidates.
6. Send only validated `ProductGuideVo` objects to DeepSeek.

If vector search fails, the request falls back to live keyword results. If the
product service is unavailable, the Agent does not answer from stale vectors.

## Configuration controls

```text
AGENT_VECTOR_SIMILARITY_THRESHOLD=0.55
AGENT_VECTOR_TIMEOUT=8s
AGENT_VECTOR_CANDIDATE_MULTIPLIER=3
AGENT_INDEX_PAGE_SIZE=100
AGENT_MAX_INDEX_DOCUMENTS=10000
```

The defaults cap retrieval and index sizes. Vector retrieval plus realtime
validation has an eight-second total timeout and falls back to the already
loaded keyword results. The Ollama embedding model remains loaded for thirty
minutes after use so an idle model reload does not normally exceed the hybrid
timeout. Before a performance test, send one warm-up search and exclude it from
the measured samples. No model-supplied argument can change the full-index
paging limit or bypass realtime SKU validation.

## Iteration boundary

This iteration implements the initial full index and hybrid retrieval.
RabbitMQ incremental indexing, dead-letter handling and scheduled index
reconciliation are implemented in
`docs/agent-iteration-4-incremental-index.md`.
