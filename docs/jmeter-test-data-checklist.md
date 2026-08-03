# JMeter 测试数据提交清单

当前本地数据库已经选定的测试对象和命令见
`docs/jmeter-current-test-dataset.md`。

## 1. 敏感用户数据

把商城 H5 测试账号数据写入 Git 已忽略的文件：

`scripts/jmeter/data/users.csv`

```csv
token,userAddressId,skuId,activityId,keyword
mall-token-user-01,101,15,8,预算5000元的开发电脑
mall-token-user-02,102,15,8,适合办公的小型电脑
```

字段要求：

| 字段 | 要求 |
| --- | --- |
| `token` | 有效商城用户 Token，不是后台管理员 Token |
| `userAddressId` | 必须属于该 Token 对应用户，且地址未删除 |
| `skuId` | 在售 SKU；普通下单和秒杀建议使用不同 SKU |
| `activityId` | 仅秒杀需要，必须是当前已发布活动 |
| `keyword` | Agent 检索或对话问题，建议不同用户使用不同问题 |

不要把 Token 写入文档、提交到 Git 或粘贴到公开日志。提交时只需说明本地 CSV
已经准备好及其路径。

推荐用户数量：

- 登录态只读基线：最少 1 个，推荐不少于最大线程数。
- 普通下单：推荐不少于最大线程数，但允许同一用户创建多个测试订单。
- 秒杀：必须不少于本轮线程数的不同用户；同一活动中每个用户只能成功一次。
- Agent 对话：推荐每次计划调用对应一个不同用户，避免先触发用户维度限流。

## 2. 购物车、订单和普通库存

### 登录态查询

为每个参与用户准备：

1. Redis 中存在 `user:cart:{userId}`。
2. 至少一个有效且勾选的购物车 SKU，数量为正。
3. 为订单分页用户准备不少于 20 条测试订单更接近真实分页负载；最好包含
   `-1/0/1/2/3` 多种订单状态。
4. 记录购物车压测前的 SKU、数量和勾选状态。

### 普通下单

提交以下非敏感数据：

```text
普通下单 skuId：
SKU 名称：
当前 stock_num：
skuNum：
线程档：
每线程迭代次数：
允许创建的最大订单数：
允许消耗的最大库存数：
```

库存准备公式：

```text
计划订单数 = 所有线程档之和 × WriteIterationsPerThread
计划库存量 = 计划订单数 × SkuNum
建议初始库存 >= 计划库存量 × 1.1
```

例如线程档为 `5,10,20`、每线程 2 次、每单 1 件，则最多创建 70 个订单，
建议准备至少 77 件库存。

当前 JMeter 普通下单固定为 `orderSource=2`，即立即购买。若要验证购物车下单后
的异步扣减，需要另外提交：

```text
购物车来源测试用户 userId：
购物车 skuId：
压测前数量：
每次下单数量：
期望压测后数量：
```

执行前还需将脚本扩展为 `orderSource=1`，不能用当前立即购买结果代替购物车
清理验收。

## 3. 秒杀数据

每个并发档使用一个新活动，或使用一批从未参加过该活动的新用户。提交：

```text
activityId：
活动名称：
startTime：
endTime：
活动状态（应为 2-published）：
skuId：
seckillSkuId：
秒杀价格：
totalStock：
availableStock：
seckill_sku 状态（应为 1-active）：
本轮线程数：
用户 CSV 行数：
是否保持网关 IP/用户/活动限流：
```

还需确认：

1. 当前时间处于活动起止时间内。
2. 活动已经通过发布接口完成 Redis 预热。
3. Redis 存在 `seckill:{activityId:skuId}:meta` 和 `stock`。
4. 所有用户地址归属正确。
5. `seckill_order_request` 中不存在这些用户对该活动 SKU 的历史记录。
6. 一次命令只运行一个秒杀线程档。

默认网关限流为 IP 20 次/秒、用户 5 次/秒、活动 200 次/秒。需要明确本轮是测
“默认限流入口”还是在隔离环境提高阈值后测“秒杀后端容量”。

## 4. Agent 对话数据

提交以下信息，不需要提交 DeepSeek API Key：

```text
确认 DEEPSEEK_API_KEY 已配置：是/否
模型名称：
测试问题列表：
线程档：
每线程调用次数：
允许的最大模型调用次数：
是否接受产生 API 费用：是/否
限流测试模式：保持默认限流/隔离环境提高阈值
```

调用量公式：

```text
最大调用次数 = 所有线程档之和 × AgentChatIterationsPerThread
```

默认 Agent 限流为用户 10 次/分钟、IP 30 次/分钟、会话 15 次/分钟。若保持
默认值，超过阈值的 429 应作为限流验收结果，不能计入模型成功 QPS。

## 5. 支付、超时和统计一致性

支付链路不做大批量 QPS 压测，使用少量订单验证最终一致性。建议准备五个订单：

| 样本 | 初始状态 | 操作 | 预期结果 |
| --- | --- | --- | --- |
| A | 待支付 | 正常沙箱支付 | 订单已支付、库存确认、统计增加 |
| B | 待支付 | 不支付并等待超时 | 订单取消、库存释放 |
| C | 待支付 | 超时前完成支付 | 超时消息不能再次关闭订单 |
| D | 已取消 | 模拟晚到支付事实 | 进入 `payment_exception_task` |
| E | 待支付 | 重复投递同一事件 | 消费幂等，不重复确认库存或统计 |

提交：

```text
支付宝沙箱配置已可用：是/否
支付宝回调能到达本地服务：是/否
是否允许真实创建上述测试订单：是/否
是否允许将支付超时临时改为 1 分钟并重启 Order：是/否
若不修改，是否接受等待默认 30 分钟：是/否
是否允许暂停消费者制造消息积压：是/否
是否允许制造一次失败消息验证 DLQ：是/否
是否允许删除带 jmeter-load-test 标记的测试数据：是/否
```

订单创建后需要记录：

- `orderId`、`orderNo`、`requestId`、`userId`、`skuId`、金额；
- 创建前后 `product_sku.stock_num/sale_num`；
- `inventory_request` 和 `inventory_reservation` 状态；
- `payment_info` 与 `payment_exception_task`；
- `mq_outbox`、`mq_consume_log`、`inventory_operation_task`；
- `order_statistics` 当日订单数和金额；
- `seckill_order_request`、`seckill_sku.available_stock`；
- Redis 购物车、清理幂等 Key 和秒杀结果 Key。

重点观察队列：

- `zjzx.order.payment-succeeded`
- `zjzx.order.timeout-delay`
- `zjzx.order.timeout-check`
- `zjzx.order.inventory-completed`
- `zjzx.product.inventory-confirm`
- `zjzx.product.inventory-release`
- `zjzx.manager.order-paid`
- `zjzx.cart.cleanup`
- `zjzx.order.seckill-create`
- 上述业务队列对应的 `.dlq`

## 6. 最终提交摘要

除 `users.csv` 外，请按下面格式回复：

```text
BaseURL：
users.csv 本地路径：

登录态读取线程档：
普通下单 skuId / 当前库存 / skuNum：
普通下单线程档 / 每线程次数：
最大允许订单数 / 库存消耗：

秒杀 activityId / seckillSkuId / skuId：
活动时间 / 状态 / availableStock：
秒杀线程数 / 不同用户数：
秒杀限流模式：

Agent 模型 / 问题列表：
Agent 线程档 / 每线程次数 / 最大调用数：
是否授权产生模型费用：

支付沙箱及回调是否可用：
超时采用 1 分钟还是默认 30 分钟：
是否允许 MQ 故障注入：
是否允许清理测试数据：

RabbitMQ 容器名或管理地址：
Prometheus / Grafana 地址：
```
