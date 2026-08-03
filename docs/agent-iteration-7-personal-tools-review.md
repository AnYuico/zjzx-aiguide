# Agent Iteration 7: Personal Tool Security Review

## Review decision

Iteration 7 passes the architecture review only with a staged scope.

- Keep the existing `/mcp` server product-only. Its shared API key does not
  identify a mall user.
- Do not expose `userId`, mall `token`, `orderNo`, address ID or payment ID as
  model or MCP arguments.
- First implementation batch: authenticated cart read, sanitized recent-order
  read, and a two-phase add-to-cart action.
- Second implementation batch: cancel an unpaid recent order through the same
  confirmation protocol.
- Defer order submission, payment, order hiding, cart clearing and address
  operations.
- Do not publish a "user profile" tool until the project has real, consented
  preference data. The current user table contains account and identity data,
  not a shopping preference profile.

No personal or transactional tool may be registered by generic annotation
scanning. Every tool remains explicitly whitelisted.

## Batch 7.1 implementation status

Batch 7.1 is implemented.

- `AgentUserPrincipalVo`, `AgentCartItemVo` and `AgentOrderSummaryVo` are in
  `zjzx-ai-contract`.
- User Service resolves the mall token through `LoginSessionService` and
  returns only the active user's ID and nickname.
- Cart Service returns a sanitized Redis cart projection.
- Order Service queries recent orders by server-resolved `user_id` and returns
  no order number, receiver, address, phone or payment identifiers.
- Agent creates `getMyCart` and `listMyRecentOrders` per authenticated chat
  request. The tools capture the resolved principal and expose no identity
  argument.
- `/mcp` remains product-only with exactly three tools.
- Iteration 7.1 adds no write operation. Confirmation and idempotency remain
  Batch 7.2 gates.

Enable the feature only after User, Cart and Order services are running:

```text
AGENT_PERSONAL_TOOLS_ENABLED=true
ZJZX_INTERNAL_API_TOKEN=<same non-default token used by internal services>
USER_SERVICE_BASE_URL=http://127.0.0.1:8512
CART_SERVICE_BASE_URL=http://127.0.0.1:8513
ORDER_SERVICE_BASE_URL=http://127.0.0.1:8514
```

The feature is disabled by default. When enabled, a missing or invalid mall
`token` returns HTTP 401. Personal-service failures return HTTP 503. The mall
token is used only as a transport header for principal resolution and is never
included in the prompt or tool schema.

## Batch 7.2 implementation status

Batch 7.2 is implemented.

- `prepareAddToCart(skuId, quantity)` is registered only in an authenticated
  chat request when both personal tools and actions are enabled.
- Preparation validates a real product snapshot and stores a `PENDING`
  action in the Agent PostgreSQL database. It does not modify the cart.
- The confirmation endpoint resolves the current user from the mall token and
  accepts only `confirmationId` plus the `confirmed` decision.
- PostgreSQL condition updates serialize concurrent confirmation attempts.
- Cart Service uses Redis Lua to update the cart and store the same
  `confirmationId` as an idempotency marker in one atomic operation.
- A retry after a dependency failure reuses the original confirmation ID.
- User cancellation cannot overwrite an action that has already entered
  `EXECUTING`.
- `/mcp` remains product-only with exactly three tools.

Run `docs/sql/20260729_agent_action_request.sql` against the Agent PostgreSQL
database before setting:

```text
AGENT_PERSONAL_TOOLS_ENABLED=true
AGENT_PERSONAL_ACTIONS_ENABLED=true
```

The frontend contract and smoke-test steps are documented in
`docs/agent-iteration-7-2-cart-confirmation.md`.

## Evidence from the current repository

### Identity boundary

`AuthGlobalFilter` validates mall sessions for `/api/**/auth/**`, including the
Agent chat and confirmation endpoints. The Agent resolves the token through
the User Service and binds the resulting principal to a per-request tool
context.

The direct MCP endpoint uses a shared `X-MCP-API-Key`. It is intentionally not
routed through Gateway and therefore has no mall-user identity.

Relevant code:

- `zjzx-server-gateway/.../filter/AuthGlobalFilter.java`
- `zjzx-agent-service/.../controller/ShoppingGuideChatController.java`
- `zjzx-agent-service/.../security/McpApiKeyWebFilter.java`
- `zjzx-agent-service/.../mcp/McpProductToolConfiguration.java`

Decision: personal tools are initially available only to the authenticated
Agent chat path. They are not added to `/mcp`.

### User profile

`UserInfo` contains account fields such as username, password, phone, OpenID,
last-login data and status. `UserInfoVo` contains only nickname and avatar.
`UserProfileInternalDto` contains user ID and nickname.

No preference tag, preferred category, budget range, consent flag or behavior
profile table was found.

Relevant code:

- `zjzx-model/.../entity/user/UserInfo.java`
- `zjzx-model/.../vo/h5/UserInfoVo.java`
- `zjzx-model/.../dto/internal/UserProfileInternalDto.java`
- `zjzx-service/service-user/.../UserInfoServiceImpl.java`

Decision: use the user service only to resolve the authenticated principal.
Do not call this account data a shopping profile and do not send it to the
model, except an optional nickname required for user-facing wording.

### Cart

The cart is stored in Redis under `user:cart:{userId}`. Reads are naturally
user-scoped through `AuthContextUtil`.

`addToCart` performs a Redis read-modify-write and increments `skuNum`.
Repeating the same request increments the quantity again. It currently has no
request ID and is not idempotent. Several cart mutations also use HTTP GET.

Relevant code:

- `zjzx-service/service-cart/.../controller/CartController.java`
- `zjzx-service/service-cart/.../service/impl/CartServiceImpl.java`
- `zjzx-model/.../vo/h5/CartItemVo.java`

Decision:

| Candidate | Decision | Reason |
| --- | --- | --- |
| `getMyCart()` | Allow | Read-only and useful for product advice |
| `prepareAddToCart(skuId, quantity)` | Allow in batch 1 | Preparation does not mutate the cart |
| `prepareSetCartQuantity(skuId, quantity)` | Defer | Requires an atomic idempotent Redis contract |
| `prepareRemoveCartItem(skuId)` | Defer | Destructive and lower guide value |
| `prepareClearCart()` | Prohibit in iteration 7 | Broad destructive action |

The actual add operation must use a dedicated POST internal endpoint and a
Redis Lua script that atomically:

1. checks the action request ID;
2. updates the cart hash;
3. records the request ID with a TTL.

The same action request ID must never increment the quantity twice.

### Orders

Order reads already include `user_id` ownership conditions. User cancellation
uses a conditional update from waiting-payment to cancelled and sends the
inventory-release event through the transactional Outbox. Repeated cancellation
of an already cancelled order returns successfully.

Order submission already uses `requestId`, `order_submit_request`, unique
indexes and authoritative product/address queries. It also reserves inventory
and creates a payment obligation, so it is outside the first Agent write scope.

`OrderDetailVo` contains receiver name, phone and full address. It must not be
provided to the model.

Relevant code:

- `zjzx-service/service-order/.../OrderInfoServiceImpl.java`
- `zjzx-service/service-order/.../OrderInfoMapper.xml`
- `zjzx-service/service-order/.../OrderSubmitRequestService.java`
- `zjzx-service/service-order/.../OrderCreationService.java`
- `zjzx-model/.../vo/order/OrderDetailVo.java`

Decision:

| Candidate | Decision | Reason |
| --- | --- | --- |
| `listMyRecentOrders(status, limit)` | Allow | Sanitized read with server-bound user |
| `getMyRecentOrderStatus(position)` | Allow | No arbitrary order number argument |
| `prepareCancelRecentOrder(position)` | Allow in batch 2 | Existing state condition and Outbox support retry |
| `prepareSubmitOrder(...)` | Prohibit in iteration 7 | Address, inventory and payment-obligation risk |
| `prepareDeleteOrder(...)` | Defer | Hides history and has little guide value |
| Payment/refund tools | Prohibit | Financial action |

Order tool output is restricted to:

```text
recentPosition
status
statusText
totalAmount
createdAt
expiresAt
productNames
```

It excludes database IDs, `orderNo`, request IDs, receiver data, address,
phone, payment record, remark and internal status metadata.

## Trusted user resolution

The model and frontend request body cannot choose a user.

Recommended flow:

1. Gateway validates the mall `token`.
2. Agent receives the token as transport authentication data, never as prompt
   or tool input.
3. Agent calls a new user-service internal endpoint with both
   `X-Internal-Token` and the mall token.
4. User service resolves `LoginPrincipal` through `LoginSessionService` and
   returns a minimal `AgentUserPrincipalVo`.
5. Agent constructs an immutable per-request tool context containing only the
   resolved user ID.
6. Internal cart/order adapters receive that server-derived user ID.

Tokens must not be written to PostgreSQL, Redis chat memory, logs, traces,
tool results or model messages.

## Two-phase confirmation protocol

Business writes are never executed directly by a model tool.

### Phase 1: prepare

The model may call a prepare tool. The Agent validates the current product or
order state and stores an action record:

```json
{
  "confirmationId": "server-generated-uuid",
  "actionType": "ADD_TO_CART",
  "summary": "将 Mac mini 16G x1 加入购物车",
  "expiresAt": "2026-07-29T16:30:00+08:00",
  "requiresConfirmation": true
}
```

Preparation performs no cart or order mutation.

### Phase 2: confirm

The frontend displays the exact summary and sends:

```http
POST /api/agent/auth/actions/{confirmationId}/confirm
token: <mall-session-token>
Content-Type: application/json

{
  "confirmed": true
}
```

The Agent verifies:

- the current authenticated user owns the confirmation;
- the action is still pending and has not expired;
- the stored action type and payload hash have not changed;
- the target product/order is still in an executable state.

The frontend cannot submit an action type, user ID, SKU payload or order
identifier to the confirm endpoint.

## End-to-end idempotency

`confirmationId` is also the downstream action request ID.

The Agent action record uses these states:

```text
PENDING -> EXECUTING -> SUCCEEDED
                     -> FAILED_RETRYABLE
PENDING -> EXPIRED
PENDING -> REJECTED
```

Required guarantees:

- `confirmation_id` is unique.
- The record is bound to one user ID and one payload hash.
- A repeated confirmation after success returns the stored result.
- A retry after an unknown network result calls the downstream service with
  the same request ID.
- The downstream cart/order service independently deduplicates that request
  ID.
- A different user receives not-found, not ownership details.
- An expired or rejected confirmation can never execute.

Agent confirmation records belong in the Agent PostgreSQL database, not the
mall MySQL database. The record stores sanitized JSON payloads and results and
never stores a mall token.

## Internal API boundary

New personal adapters must use dedicated `/internal/agent/**` endpoints.

- Gateway continues to return 404 for every path segment named `internal`.
- External `X-Internal-Token` headers continue to be removed.
- Business service ports remain loopback/private.
- Internal endpoints return dedicated AI contract DTOs, never mall entities,
  `CartInfo`, `OrderInfo` or `OrderDetailVo`.

## Implementation order

### Batch 7.1

1. Add authenticated-principal resolution in User Service and Agent.
2. Add `AgentCartItemVo` and `AgentOrderSummaryVo` to `zjzx-ai-contract`.
3. Add internal current-user cart and sanitized recent-order endpoints.
4. Register `getMyCart` and `listMyRecentOrders` only for authenticated chat.
5. Keep `/mcp` discovery at exactly the three product tools.

### Batch 7.2

1. Add the Agent action-request table and confirmation API.
2. Add `prepareAddToCart`.
3. Add cart Redis Lua idempotency using `confirmationId`.
4. Add replay, expiry, ownership and concurrent-confirm tests.

### Batch 7.3

1. Add `prepareCancelRecentOrder`.
2. Revalidate waiting-payment status during confirmation.
3. Reuse the existing conditional cancellation and inventory-release Outbox.
4. Add lost-response and duplicate-confirmation tests.

Batch 7.3 is implemented.

- The model selects only `recentPosition` from the sanitized
  `WAITING_PAYMENT` list.
- Order Service resolves the position under the server-derived user ID; the
  stable order number remains internal.
- Confirmation reuses the 7.2 action state machine and payload hash.
- Order cancellation uses the existing ownership/status condition, local
  transaction, inventory task and RabbitMQ Outbox.
- Paid-before-confirmation is rejected without releasing inventory.
- Duplicate confirmation and lost-response retry do not publish duplicate
  release operations.
- Run `docs/sql/20260729_agent_action_cancel_order.sql` before using the new
  action type.

The frontend contract is documented in
`docs/agent-iteration-7-3-order-cancellation.md`.

## Acceptance gates

- Tool schemas contain no `userId`, token, `orderNo`, address or payment ID.
- A forged user identity header cannot select another user.
- Cart/order output contains no phone, address, receiver, password or internal
  entity fields.
- Prepare calls never mutate mall data.
- Writes without a valid pending confirmation are impossible.
- Concurrent confirmation calls produce one business mutation.
- Reusing a successful confirmation returns the same result.
- Reusing a confirmation as another user returns not-found.
- Expired confirmations do not execute.
- Agent restart after an unknown downstream response can retry safely.
- Existing MCP negative suite remains `29/29`.
- Agent, Gateway, User, Cart and Order regression tests all pass.
