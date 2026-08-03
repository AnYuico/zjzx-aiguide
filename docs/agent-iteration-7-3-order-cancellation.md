# Agent 迭代 7.3：确认后取消待付款订单

## 功能边界

本迭代允许 Agent 准备取消当前用户的近期待付款订单，但模型本身仍不能
直接取消订单。

- 模型工具：`prepareCancelRecentOrder(recentPosition)`
- 选择依据：`listMyRecentOrders("WAITING_PAYMENT", limit)` 返回的位置
- 实际取消：用户调用统一确认接口后执行
- 支持状态：仅 `WAITING_PAYMENT`
- 禁止参数：`userId`、商城 Token、`orderNo`、地址和支付标识
- 不支持：取消已支付订单、退款、删除订单、提交订单、修改订单金额

`recentPosition` 是待付款订单列表中的位置，从 1 开始。真实 `orderNo`
只在 Order Service、Agent 内部 HTTP 请求和 PostgreSQL 动作 payload 中
使用，不会返回给模型或前端。

## 执行链路

1. 模型调用 `listMyRecentOrders("WAITING_PAYMENT", limit)`。
2. 用户明确要求取消其中一项后，模型调用
   `prepareCancelRecentOrder(recentPosition)`。
3. Order Service 按 `user_id + WAITING_PAYMENT + recentPosition` 解析候选订单。
4. Agent 将订单号写入带 SHA-256 哈希的内部动作 payload，状态为 `PENDING`。
5. 前端只收到摘要、`confirmationId` 和过期时间。
6. 用户确认后，Agent 条件更新 `PENDING -> EXECUTING`。
7. Order Service 执行带用户归属条件的
   `order_status = 0 -> -1` 更新。
8. 同一 MySQL 事务内写订单日志、库存释放任务和 RabbitMQ Outbox。
9. Agent 保存 `SUCCEEDED` 结果；重复确认直接返回已保存结果。

如果支付先于确认完成，订单状态已经变为待发货，条件更新失败，取消动作返回
冲突且不会创建库存释放事件。

## PostgreSQL 增量脚本

7.2 表已经创建后，还需要在 `zjzx_agent` 执行：

```text
docs/sql/20260729_agent_action_cancel_order.sql
```

该脚本只扩展 `ck_agent_action_type`，允许：

```text
ADD_TO_CART
CANCEL_RECENT_ORDER
```

MySQL 不需要新增表或字段。订单取消复用现有 `order_info`、
`order_log`、库存操作任务和 MQ Outbox。

## 前端适配

聊天接口与确认接口均不变化。

### 待确认动作

```json
{
  "answer": "已准备取消待付款订单，请在界面确认。",
  "pendingActions": [
    {
      "confirmationId": "6e77e7da-ec59-4d5d-88ea-8db1a88f9947",
      "actionType": "CANCEL_RECENT_ORDER",
      "summary": "取消第 1 个近期待付款订单：Mac mini 16G，金额 ¥1999.00",
      "expiresAt": "2026-07-29T10:30:00Z",
      "requiresConfirmation": true
    }
  ]
}
```

前端按 `actionType` 显示取消订单确认样式，但不得自行补充订单号、收货人、
地址或支付信息。

### 确认执行

```http
POST /api/agent/auth/actions/{confirmationId}/confirm
token: <mall-session-token>
Content-Type: application/json

{
  "confirmed": true
}
```

成功响应：

```json
{
  "confirmationId": "6e77e7da-ec59-4d5d-88ea-8db1a88f9947",
  "status": "SUCCEEDED",
  "summary": "取消第 1 个近期待付款订单：Mac mini 16G，金额 ¥1999.00",
  "message": "待付款订单已取消",
  "replayed": false
}
```

重复确认返回 HTTP 200，`replayed=true`，不会重复写订单日志或重复发送库存
释放消息。

### 前端处理建议

- 确认成功后刷新订单列表。
- `409 ACTION_CONFLICT`：提示订单状态已经变化并刷新列表。
- `410 ACTION_EXPIRED`：移除确认条目并引导重新查询。
- `503 ACTION_UNAVAILABLE`：保留原 `confirmationId`，稍后原样重试。
- 用户点击取消时仍发送 `{"confirmed": false}`，不会触碰订单。

## 一致性与幂等

- Agent PostgreSQL 主键保证一个 `confirmationId` 只有一个动作。
- 动作 payload 哈希防止订单目标被修改。
- Order Service 使用 `order_no + user_id + order_status=0` 条件更新。
- 已取消订单再次调用返回 `applied=false, replayed=true`。
- 库存释放 Outbox 的事件 ID 按 `orderNo` 稳定生成。
- 取消事务回滚时，订单状态、日志、任务和 Outbox 一起回滚。
- 响应丢失时，Agent 使用原确认号重试；Order Service 根据订单状态返回重放。

## 冒烟测试

1. 创建一个待付款订单并确认订单列表中可见。
2. 对话要求查看待付款订单，确认响应不包含订单号和地址。
3. 要求取消第 1 个订单，检查准备阶段订单状态仍为 `0`。
4. 调用确认接口，检查订单状态变为 `-1`。
5. 检查库存释放 Outbox/任务只生成一份。
6. 重复调用同一个确认接口，检查 `replayed=true`。
7. 准备另一个取消动作后先完成支付，再确认取消，应返回 409。
8. 使用另一个用户 Token 确认，应返回 404。
9. MCP `tools/list` 仍应只有三个商品只读工具。

