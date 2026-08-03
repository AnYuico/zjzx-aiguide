# Agent iteration 4: transactional product events and incremental indexing

## Scope

Iteration 4 keeps MySQL as the authoritative product database and PGVector as
a derived search index.

The implemented flow is:

1. Manager changes `product`, `product_sku`, and `product_details`.
2. The same MySQL transaction inserts a `PRODUCT_KNOWLEDGE_CHANGED` record
   into `mq_outbox`.
3. The existing Outbox publisher sends the persistent event to RabbitMQ.
4. Agent consumes the event and requests the current allowlisted product
   knowledge snapshot from Product.
5. Agent upserts current SKU documents and removes stale SKU documents.
6. Agent records the event in PostgreSQL only after the vector mutation
   completes.
7. Failed messages are retried three times and then routed to a dedicated DLQ.
8. A periodic authoritative full rebuild repairs lost events, DLQ events, and
   historical drift.

The event contains only `eventId`, `productId`, `reason`, and `changedAt`.
It does not carry product entities, cost price, inventory records, or user data.

## Consistency model

Product writes and the Outbox insert use the same local MySQL transaction.
Therefore a committed product change always has a durable event, while a
rolled-back product change cannot leave an event behind.

The consumer fetches the latest Product snapshot instead of replaying the
payload as product state. Duplicate or out-of-order events therefore converge
to the current MySQL state.

Vector updates are idempotent because document IDs are stable:

```text
product-sku-{skuId}
```

The consumer writes `agent_mq_consume_log` only after all vector additions and
deletions succeed. A crash before that insert causes RabbitMQ redelivery. The
same vector mutation can safely run again.

## RabbitMQ topology

| Type | Name |
| --- | --- |
| Event exchange | `zjzx.product.knowledge.events` |
| Routing key | `product.knowledge.changed` |
| Agent queue | `zjzx.agent.product-knowledge-changed` |
| Dead exchange | `zjzx.product.knowledge.dlx` |
| Dead routing key | `product.knowledge.changed.dead` |
| Dead queue | `zjzx.agent.product-knowledge-changed.dlq` |

The business queue is durable and messages published by the Outbox are
persistent. The Agent listener uses bounded concurrency, automatic
acknowledgement after successful return, three retries, and no infinite
requeue.

## Database preparation

The existing MySQL tables from
`docs/sql/20260722_rabbitmq_outbox.sql` are reused.

Run the following script against PostgreSQL database `zjzx_agent`:

```text
docs/sql/20260728_agent_product_index_mq.sql
```

It creates the Agent-side RabbitMQ consume log and its unique idempotency
constraint.

## Startup configuration

Product and Manager continue to use the existing RabbitMQ and internal API
environment variables.

Agent requires:

```text
AGENT_VECTOR_ENABLED=true
SPRING_AI_MODEL_EMBEDDING=ollama
SPRING_AI_VECTORSTORE_TYPE=pgvector
AGENT_PRODUCT_INDEX_MQ_ENABLED=true
AGENT_PRODUCT_INDEX_RECONCILIATION_ENABLED=true
ZJZX_INTERNAL_API_TOKEN=<same token used by Product>
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<username>
RABBITMQ_PASSWORD=<password>
```

Optional reconciliation settings:

```text
AGENT_PRODUCT_INDEX_RECONCILIATION_INITIAL_DELAY_MS=300000
AGENT_PRODUCT_INDEX_RECONCILIATION_FIXED_DELAY_MS=21600000
AGENT_PRODUCT_INDEX_RECONCILIATION_TIMEOUT=30m
```

Recommended startup order:

1. PostgreSQL/PGVector, Ollama, and RabbitMQ.
2. Product with the new product-snapshot endpoint.
3. Agent with incremental MQ enabled, so the durable queue is declared.
4. Manager with the Outbox publisher enabled.
5. Trigger one initial full rebuild.

## Verification

Initial full rebuild:

```http
POST /api/agent/internal/index/products/rebuild
X-Internal-Token: <token>
```

After changing, auditing, publishing, taking down, or deleting a product,
check MySQL:

```sql
SELECT event_id, event_type, status, retry_count, last_error
FROM mq_outbox
WHERE producer = 'server-manager'
  AND event_type = 'PRODUCT_KNOWLEDGE_CHANGED'
ORDER BY id DESC
LIMIT 10;
```

Check PostgreSQL:

```sql
SELECT consumer_name, event_id, consume_time
FROM agent_mq_consume_log
ORDER BY id DESC
LIMIT 10;

SELECT id,
       metadata->>'productId' AS product_id,
       metadata->>'skuId' AS sku_id,
       metadata->>'contentHash' AS content_hash
FROM product_knowledge_vector
ORDER BY id;
```

RabbitMQ should normally show zero ready messages in both the business queue
and DLQ. A DLQ message means incremental processing failed after all retries.
The periodic full rebuild repairs the search index but intentionally leaves
the DLQ message available for diagnosis.

## Operational boundaries

- Product prices, images, and stock are still read from Product at query time.
- Inventory deductions and sales counters do not produce embedding events.
- Brand or category name changes are repaired by scheduled reconciliation;
  they do not currently fan out one event per affected product.
- The current coordinator serializes full and incremental writes inside one
  Agent process. Running multiple Agent replicas requires a leader-elected
  reconciliation task or a PostgreSQL advisory lock.
- Frontend APIs and response objects are unchanged.

Next: [Iteration 5 observability, resilience, rate limiting, and security evaluation](agent-iteration-5-observability-resilience-security.md).
