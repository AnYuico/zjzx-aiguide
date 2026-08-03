# RabbitMQ v1

## Scope

This iteration keeps stock reservation synchronous and moves these operations to reliable events:

- Alipay success -> order paid -> inventory confirmation.
- Order timeout -> order closed -> inventory release.
- Order paid -> daily order statistics increment.
- Cart checkout order created -> exact purchased quantities removed from Redis cart.
- Seckill Redis admission -> asynchronous order creation with MySQL final stock checks.

The submit-order request now requires `orderSource` (`1` cart, `2` buy now); its response remains
unchanged. Payment and cart propagation are eventually consistent.

## Prerequisites

1. Deploy RabbitMQ with the AMQP port available to the five participating backend services.
2. Apply the stock-reservation baseline in `docs/sql/20260711_inventory_reservation.sql`
   before enabling the MQ code. Existing partially upgraded databases can use the focused
   `20260722_*_repair.sql` scripts instead of rerunning non-idempotent `ALTER TABLE` statements.
3. Apply `docs/sql/20260722_rabbitmq_outbox.sql` to `db_zjzx`.
4. Apply `docs/sql/20260723_cart_async_cleanup.sql` to add `order_info.order_source`.
5. Run `docs/sql/20260722_schema_integrity_check.sql` and resolve every `FAIL` or `SKIPPED`
   result before starting the MQ-enabled services.
6. Apply `docs/sql/20260723_seckill_v1.sql` before enabling seckill endpoints.
7. Provide these environment variables when defaults are not suitable:

```text
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VHOST=/
```

The RabbitMQ `guest` account can only connect from localhost by default. Use a dedicated account
when RabbitMQ and the Java services do not run on the same host.

For local Windows Docker deployment, run `scripts/docker/deploy-rabbitmq.ps1`. The script reads
`RABBITMQ_USERNAME` and `RABBITMQ_PASSWORD` from the current environment, or prompts for them when
they are absent. It deploys RabbitMQ separately from Nacos and connects both containers to the
`zjzx-net` Docker network.

## Reliability model

- Business data and `mq_outbox` are written in one local transaction.
- The publisher marks an Outbox row sent only after correlated publisher confirmation.
- Unroutable messages are detected through mandatory returns.
- Database consumers claim `(consumer_name, event_id)` in `mq_consume_log` in the same transaction
  as the business change.
- The cart consumer stores a 30-day Redis idempotency key and adjusts the exact SKU quantities in
  one Lua script. It does not use the MySQL consume log because its business state is in Redis.
- Listener failures retry three times and then enter a dedicated dead-letter queue.
- Inventory confirmation and release create `inventory_operation_task` rows. A delayed Feign
  fallback runs only when the normal MQ result event is not received.
- The order service scans expired unpaid orders every minute as a delayed-message fallback.
- The manager still recomputes the previous day's paid-order statistics at 02:00.
- Seckill admission uses Redis Lua; pending sends are retried after publisher-confirm failures.
- The order consumer uses three unique keys plus a conditional `available_stock > 0` update.
- Failed creation and cancelled-order stock returns use separate idempotent Lua rollback markers.

RabbitMQ delivery is at least once. Database unique keys, conditional order transitions and the
inventory reservation state machine provide business idempotency.

## Main routes

| Routing key | Producer | Consumer |
| --- | --- | --- |
| `payment.succeeded` | service-pay | service-order |
| `order.timeout.delay` | service-order | delay queue |
| `order.timeout.check` | delay queue | service-order |
| `inventory.confirm.requested` | service-order | service-product |
| `inventory.release.requested` | service-order | service-product |
| `inventory.operation.completed` | service-product | service-order |
| `order.paid` | service-order | server-manager |
| `cart.cleanup.requested` | service-order | service-cart |
| `seckill.order.requested` | service-product | service-order |

## Operational checks

```sql
select status, count(*) from mq_outbox group by status;
select * from mq_outbox where status = 2 order by id desc;
select * from inventory_operation_task where status <> 1 order by id desc;
select * from payment_exception_task where status = 0 order by id desc;
```

Outbox status `2`, messages in `*.dlq`, inventory task status `2`, and unresolved payment exception
tasks require investigation. A late payment is recorded for refund handling and never reopens a
closed order.

The current cart Lua script targets the project's standalone Redis deployment. Before moving the
cart to Redis Cluster, migrate the cart key and cleanup idempotency key to a shared hash tag so both
keys remain in the same slot.
