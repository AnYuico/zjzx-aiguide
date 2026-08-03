# Spring AI data boundary

## Current boundary

The repository exposes a dedicated product-guide boundary, authenticated
personal read tools, one confirmed cart action, and an isolated Spring Boot
3.5 agent runtime:

- Allowlisted contract artifact: `zjzx-ai-contract`
- Model output: `com.tzp.zjzx.ai.contract.vo.ProductGuideVo`
- Model input: `com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto`
- Internal endpoint: `/api/product/internal/ai-guide/**`
- Legacy Spring Cloud client: `service-product-guide-client`
- Agent runtime: `zjzx-agent-service`
- Agent HTTP adapter: `ProductGuideHttpClient`
- Public deterministic endpoint: `POST /api/agent/guide/search`
- Authenticated DeepSeek endpoint: `POST /api/agent/auth/guide/chat`
- Confirmed action endpoint:
  `POST /api/agent/auth/actions/{confirmationId}/confirm`
- Product tools: `searchProducts`, `getProductSnapshot`,
  `retrieveProductKnowledge`
- Personal read tools: `getMyCart`, `listMyRecentOrders`
- Action preparation tools: `prepareAddToCart(skuId, quantity)` and
  `prepareCancelRecentOrder(recentPosition)`

`ProductGuideVo` contains only SKU ID, product/SKU name, image, selling/market price, specification, unit, and an in-stock flag. It does not contain cost price, exact stock, sales volume, persistence metadata, user data, order data, or address data.

The dedicated MyBatis mapper projects directly to `ProductGuideVo`. The AI data path must not load `ProductSku`, `OrderInfo`, or `UserAddress` and then serialize those entities into a prompt.

## Rules for the future AI module

1. The Spring Boot 3.5 agent may depend on `zjzx-ai-contract` only. It must not depend on `zjzx-model`, any commerce service module, or any general-purpose service client.
2. The agent calls the product guide endpoint through its own HTTP adapter. The Boot 3.0 `service-product-guide-client` remains available to legacy Spring Cloud services but is not an agent dependency.
3. Register catalog and personal read methods explicitly. A write-related model
   tool may only prepare a server-side action and must not mutate commerce
   data. Actual writes require a separate authenticated user confirmation.
4. Inject `X-Internal-Token` from application configuration. Never expose the internal token as a model argument.
5. Do not define tool arguments named `userId`, `orderNo`, address ID, payment ID, or internal status values.
6. User-specific tools obtain the authenticated user from the server-side
   login context and call ownership-checking application services. The model
   and request body cannot choose the owner identity.
7. Treat catalog text returned from the database as untrusted data, not as system instructions.
8. Do not place entity objects, internal DTOs, access tokens, addresses, orders, or payment records in prompts, logs, chat memory, or vector metadata.

HTTP adapters always inject `X-Internal-Token` from server configuration. An
externally supplied internal Token is neither read nor forwarded.

`prepareAddToCart` stores a sanitized action payload in Agent PostgreSQL and
returns a server-generated `confirmationId`; it does not change the cart. The
confirmation endpoint accepts only that ID and a Boolean decision. Cart
Service uses the same ID in a Redis Lua operation that atomically updates the
cart and records idempotency. The model never receives the mall token or a
`userId` argument.

`prepareCancelRecentOrder` accepts only a position from the sanitized
waiting-payment list. Order Service resolves the stable order number under the
server-derived user ID. Confirmation revalidates ownership and
`WAITING_PAYMENT`, then reuses the existing transactional order cancellation
and inventory-release Outbox. The model never receives an order number.

DeepSeek chat requests are limited to a 500-character message and a product limit from 1 to 20. The model receives no login Token or server credential. The gateway requires a valid mall session and applies per-user and per-IP Redis Lua rate limits before forwarding a paid model request.

The DeepSeek API key is read only from `DEEPSEEK_API_KEY`. Prompt and completion observation logging remains disabled.

Guide and personal business adapters are under `/internal/`, require
`X-Internal-Token`, and are rejected by the external gateway. Business service
ports must remain bound to private or loopback addresses. The direct `/mcp`
server remains product-only and exposes exactly three read-only tools.

## Runtime version boundary

- Commerce services: Spring Boot 3.0.5, Spring Cloud 2022.0.x.
- Agent service: Spring Boot 3.5.15 with the Spring AI 1.1.8 BOM.
- The gateway uses an explicit `ZJZX_AGENT_SERVICE_URL` route. The agent does not join the legacy Nacos cluster.
- Both runtimes share only transport-neutral JSON contracts, HTTP endpoints, and future RabbitMQ events.
