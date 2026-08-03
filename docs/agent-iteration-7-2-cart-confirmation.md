# Agent 迭代 7.2：确认后加入购物车

## 实现边界

本迭代只允许 Agent 准备“加入购物车”动作。模型不能直接修改购物车，
也不能接收或生成 `userId`、登录 Token、订单号等身份或交易参数。

- 模型工具：`prepareAddToCart(skuId, quantity)`
- 实际写入：仅由用户确认接口触发
- 用户身份：从请求头中的商城 Token 解析，前端不能传 `userId`
- 幂等标识：服务端生成的 `confirmationId`
- MCP：仍只暴露原有 3 个只读商品工具
- 暂不支持：改数量、移除/清空购物车、提交订单、支付

## 执行链路

1. 用户在已登录的导购会话中提出“把某商品加入购物车”。
2. 模型先通过商品工具选择真实 SKU，再调用 `prepareAddToCart`。
3. Agent 校验商品快照并向 PostgreSQL 写入 `PENDING` 动作记录。
4. 聊天响应通过 `pendingActions` 返回待确认动作，购物车此时不变。
5. 前端展示 `summary`，用户点击确认或取消。
6. Agent 再次从商城 Token 解析用户，并按用户查询动作记录。
7. Agent 使用条件更新抢占 `PENDING -> EXECUTING`。
8. Cart Service 使用同一个 `confirmationId` 执行 Redis Lua。
9. Lua 在一个原子操作中更新购物车 Hash，并写入幂等 Key。
10. Agent 持久化 `SUCCEEDED`；重复确认直接返回已保存结果。

## 数据库准备

先在 Agent 使用的 PostgreSQL 数据库 `zjzx_agent` 执行：

```text
docs/sql/20260729_agent_action_request.sql
```

动作记录只保存服务端解析的用户 ID、脱敏动作 JSON、摘要、哈希和状态，
不会保存商城 Token。

## 启用配置

Agent 8520 服务增加：

```text
AGENT_PERSONAL_TOOLS_ENABLED=true
AGENT_PERSONAL_ACTIONS_ENABLED=true
ZJZX_INTERNAL_API_TOKEN=<与 User/Cart/Order 服务一致的内部 Token>
USER_SERVICE_BASE_URL=http://127.0.0.1:8512
CART_SERVICE_BASE_URL=http://127.0.0.1:8513
ORDER_SERVICE_BASE_URL=http://127.0.0.1:8514
AGENT_ACTION_CONFIRMATION_TTL=5m
AGENT_ACTION_EXECUTION_LEASE=30s
AGENT_ACTION_MAX_CART_QUANTITY=10
```

Cart 8513 服务继续使用相同的 `ZJZX_INTERNAL_API_TOKEN`。可选配置：

```text
AGENT_CART_ACTION_IDEMPOTENCY_TTL_DAYS=30
AGENT_CART_ACTION_MAX_REQUEST_QUANTITY=10
AGENT_CART_ACTION_MAX_TOTAL_QUANTITY=99
```

## 前端适配

### 1. 聊天响应新增字段

原聊天接口不变：

```http
POST /api/agent/auth/guide/chat
token: <mall-session-token>
Content-Type: application/json
```

响应新增 `pendingActions`。没有待确认动作时固定返回空数组：

```json
{
  "answer": "已为你准备加入购物车，请确认。",
  "mode": "AI",
  "model": "deepseek-v4-flash",
  "products": [],
  "pendingActions": [
    {
      "confirmationId": "d0b2abec-b950-4a6f-94f6-8f54647d2db6",
      "actionType": "ADD_TO_CART",
      "summary": "将 Mac mini 16G x1 加入购物车",
      "expiresAt": "2026-07-29T09:30:00Z",
      "requiresConfirmation": true
    }
  ]
}
```

前端应按 `pendingActions` 渲染确认条目，只展示后端返回的摘要，不自行拼装
SKU、价格或用户信息。

### 2. 用户确认或取消

```http
POST /api/agent/auth/actions/{confirmationId}/confirm
token: <mall-session-token>
Content-Type: application/json

{
  "confirmed": true
}
```

取消时发送：

```json
{
  "confirmed": false
}
```

请求体只能包含 `confirmed`。不得附带 `userId`、`skuId`、数量或动作类型。

成功响应：

```json
{
  "confirmationId": "d0b2abec-b950-4a6f-94f6-8f54647d2db6",
  "status": "SUCCEEDED",
  "summary": "将 Mac mini 16G x1 加入购物车",
  "message": "商品已加入购物车",
  "replayed": false
}
```

同一个确认请求已成功执行时，重复提交仍返回 HTTP 200，
且 `replayed` 为 `true`。前端不得生成新的确认号重试。

### 3. 错误处理

| HTTP | code | 前端处理 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 提示请求格式错误，不重试 |
| 401 | `AUTHENTICATION_REQUIRED` | 清理登录态并重新登录 |
| 404 | `ACTION_NOT_FOUND` | 提示动作不存在或不属于当前用户 |
| 409 | `ACTION_CONFLICT` | 提示动作已执行、取消或正在处理中 |
| 410 | `ACTION_EXPIRED` | 移除确认条目，引导重新发起 |
| 503 | `ACTION_UNAVAILABLE` | 保留原 `confirmationId`，允许稍后重试 |

前端应在确认按钮点击后进入加载状态，避免连续点击；这只是交互优化，
后端仍通过 PostgreSQL 条件更新和 Redis Lua 保证并发安全。

## 幂等与故障语义

- PostgreSQL `confirmation_id` 主键防止重复动作记录。
- 动作按 `confirmation_id + user_id` 查询，其他用户统一得到 404。
- `PENDING/FAILED_RETRYABLE -> EXECUTING` 使用条件更新抢占。
- Cart 幂等 Key：
  `user:cart:agent-action:{userId}:{confirmationId}`。
- Cart Lua 同时更新 `user:cart:{userId}` 和幂等 Key。
- 相同确认号和相同负载返回重放成功。
- 相同确认号对应不同负载返回冲突。
- Agent 收到未知网络结果后仍使用原确认号重试，因此不会重复增加数量。

## 验收建议

1. 准备动作后先检查购物车，数量不得变化。
2. 确认一次后数量只增加一次。
3. 重复确认同一 `confirmationId`，数量不再增加且返回 `replayed=true`。
4. 两个并发确认请求只能有一个真正执行。
5. 使用另一个用户 Token 确认应返回 404。
6. Cart 暂停时确认返回 503；恢复后用原确认号重试成功。
7. `/mcp` 的 `tools/list` 仍应只返回 3 个商品只读工具。
