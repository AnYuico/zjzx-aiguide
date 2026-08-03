# 紫金甄选商城 JMeter 压测方案

## 1. 测试目标

本方案用于测量当前本地微服务环境中关键接口的稳定成功 QPS，而不是追求一次
瞬时峰值。稳定容量按以下条件判断：

- 业务成功率不低于 99%；
- P95 不超过对应场景 SLA；
- 压测期间应用没有持续 Full GC、连接池耗尽或线程池排队；
- MySQL、Redis、RabbitMQ 和 PGVector 没有不可恢复的积压；
- 写场景结束后，订单、库存、Outbox 和 MQ 消费结果能够对账。

报告中的核心指标是 `SuccessQps`。`Qps` 包含断言失败的请求，不能作为业务
承载能力。

## 2. 已覆盖场景

| scenario | 接口 | 类型 | 默认 P95 SLA |
| --- | --- | --- | ---: |
| `product_list` | `GET /api/product/1/{limit}` | 商品分页 | 500 ms |
| `product_detail` | `GET /api/product/item/{skuId}` | 商品详情 | 500 ms |
| `seckill_activity_list` | `GET /api/product/seckill/activities` | 活动列表 | 500 ms |
| `cart_read` | `GET /api/order/cart/auth/cartList` | 购物车查询 | 800 ms |
| `order_list` | `GET /api/order/orderInfo/auth/1/{limit}` | 订单查询 | 800 ms |
| `checkout_trade` | `GET /api/order/orderInfo/auth/trade` | 结算预览 | 1000 ms |
| `order_submit` | `POST /api/order/orderInfo/auth/submitOrder` | 下单和库存预占 | 1500 ms |
| `seckill_submit` | 秒杀异步提交接口 | Redis Lua + MQ | 1000 ms |
| `seckill_same_request` | 相同请求并发重放 | requestId 幂等 | 1000 ms |
| `seckill_user_limit` | 同一用户不同请求并发 | 一人一单 | 1000 ms |
| `mixed_read` | 商品、活动、购物车、订单、结算混合 | 30 分钟稳定性 | 1000 ms |
| `agent_search` | `POST /api/agent/guide/search` | MySQL + PGVector 混合检索 | 2000 ms |
| `agent_chat` | `POST /api/agent/auth/guide/chat` | DeepSeek 对话 | 15000 ms |

支付接口不纳入自动压测，避免批量创建真实支付宝交易。订单超时关闭和 MQ
补偿属于异步一致性验收，应通过队列积压、Outbox 状态和最终数据库结果衡量，
不能只看 HTTP QPS。

## 3. 文件说明

- `scripts/jmeter/zjzx-critical-api.jmx`：原生 JMeter 5.6.x 测试计划。
- `scripts/jmeter/Run-ZjzxJmeter.ps1`：阶梯压测和 QPS 汇总脚本。
- `scripts/jmeter/data/users.csv.example`：无密钥数据模板。
- `scripts/jmeter/tools/New-ZjzxTestUsers.ps1`：通过真实登录和地址接口生成本地用户数据。
- `scripts/jmeter/tools/New-ZjzxJmeterCohorts.ps1`：从 1000 个账号切分普通订单和秒杀用户组。
- `scripts/jmeter/tools/Test-ZjzxOrderRun.ps1`：普通订单、预占、Outbox、超时释放对账。
- `scripts/jmeter/tools/Test-ZjzxSeckillRun.ps1`：Lua、MQ、异步订单和恢复对账。
- `scripts/jmeter/Run-ZjzxStabilityTest.ps1`：30 分钟混合读及运行时指标采样。
- `scripts/jmeter/results/`：运行结果，已加入 `.gitignore`。
- `docs/jmeter-test-data-checklist.md`：测试数据和执行授权清单。
- `docs/jmeter-test-user-generation.md`：测试环境批量账号接口的启用与关闭步骤。

脚本按响应体业务码断言。商城接口通常只有 `code=200` 才计为成功；秒杀场景的
`code=237` 售罄单独计为预期业务拒绝，不计入技术错误。
`seckill_user_limit` 专项中的 `code=239` 也计为预期拒绝；其他场景中的 239
仍为错误。相同 `requestId` 专项还会断言全部响应只有一个订单号。Agent 接口
必须返回直接 JSON，并包含预期字段。

## 4. 环境与数据准备

JMeter 是压测发起端，不是商城依赖服务，无需放进 Docker。建议在 Windows
压测机上使用 JDK 17，下载并解压 Apache JMeter 5.6.3，然后设置：

```powershell
$env:JMETER_HOME = "D:\tools\apache-jmeter-5.6.3"
```

官方下载页：`https://jmeter.apache.org/download_jmeter.cgi`。下载后应按页面
提供的 SHA-512 校验文件完整性。只有需要统一 CI 环境时才考虑 JMeter 容器，
且不应与被测微服务共享同一容器。

正式压测前确认：

1. Gateway、Product、User、Cart、Order、Pay、Agent 均使用待测版本。
2. MySQL、Redis、Nacos、RabbitMQ、PostgreSQL/PGVector 和 OTel 可用。
3. 压测机与服务端分离；如果都在同一台 Windows 主机，结果只能代表本机组合
   容量，需要同时观察 JMeter 自身 CPU。
4. 使用专用测试用户、地址、SKU 和秒杀活动，不使用真实业务数据。
5. 普通下单 SKU 库存必须大于本轮预计成功请求总数。
6. 秒杀 CSV 每个 Token 对应不同用户，并拥有有效地址；活动库存按测试目标设置。
7. 写压测前记录 SKU 初始库存，压测后核对订单成功数、库存和预占记录。

创建本地 `scripts/jmeter/data/users.csv`，字段如下：

```csv
token,userAddressId,skuId,activityId,keyword
mall-token-user-1,9,15,3,Mac mini
mall-token-user-2,12,15,3,Mac mini
```

该文件已被 Git 忽略，不应提交 Token。

1000 个用户生成后切分数据：

```powershell
.\scripts\jmeter\tools\New-ZjzxJmeterCohorts.ps1 `
  -SkuId 14 `
  -OrderUserCount 20 `
  -SeckillActivity50 <activity-50-id> `
  -SeckillActivity100 <activity-100-id> `
  -SeckillActivity200 <activity-200-id> `
  -SeckillInvariantActivity <invariant-activity-id>
```

四个活动必须彼此独立。建议库存依次为 50、100、100、2；200 用户档使用
100 件活动库存，以同时观测 100 个 MQ 受理和 100 个 `237` 售罄拒绝。各档
执行后必须等待库存释放或主动取消测试订单，再运行下一档。

测试用户较多时不要手工伪造 Token，也不要直接向 Redis 写登录态。按照
[`jmeter-test-user-generation.md`](jmeter-test-user-generation.md) 临时启用
测试账号内部接口，再由脚本调用正式登录和地址接口生成该文件。

## 5. 先验证计划

不产生请求，只校验参数和数据文件：

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 -PlanOnly
```

登录态场景：

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -PlanOnly `
  -Scenarios cart_read,order_list,checkout_trade `
  -DataFile .\scripts\jmeter\data\users.csv
```

## 6. 推荐执行顺序

### 6.1 公开只读基线

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -Scenarios product_list,product_detail,seckill_activity_list `
  -ThreadLevels 10,30,60,100 `
  -DurationSeconds 60 `
  -DataFile .\scripts\jmeter\data\users.csv
```

### 6.2 登录态查询

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -Scenarios cart_read,order_list,checkout_trade `
  -ThreadLevels 10,30,60 `
  -DurationSeconds 60 `
  -DataFile .\scripts\jmeter\data\users.csv
```

### 6.3 Agent 混合检索

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -Scenarios agent_search `
  -ThreadLevels 2,5,10,20 `
  -DurationSeconds 60 `
  -Keyword "适合编程的小型电脑"
```

### 6.4 普通下单

该场景会真实创建订单、预占库存并产生超时消息：

```powershell
.\scripts\jmeter\tools\Test-ZjzxOrderRun.ps1 `
  -Phase Before `
  -RunId ordinary_20260730_01 `
  -SkuId 14 `
  -ExpectedOrders 20

.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -IncludeWrites `
  -Scenarios order_submit `
  -ThreadLevels 20 `
  -WriteIterationsPerThread 1 `
  -DurationSeconds 30 `
  -OrderSource 1 `
  -RunId ordinary_20260730_01 `
  -DataFile .\scripts\jmeter\data\order-users.csv

.\scripts\jmeter\tools\Test-ZjzxOrderRun.ps1 `
  -Phase AfterSubmit `
  -RunId ordinary_20260730_01 `
  -SkuId 14 `
  -ExpectedOrders 20
```

每次请求生成新的 `requestId`，用于测量真实下单吞吐，不是重复提交幂等测试。
预计订单数为所有线程档之和乘以 `WriteIterationsPerThread`；预计库存消耗再乘以
`SkuNum`。`OrderSource=1` 会额外验证购物车异步清理。30 分钟后执行：

```powershell
.\scripts\jmeter\tools\Test-ZjzxOrderRun.ps1 `
  -Phase AfterTimeout `
  -RunId ordinary_20260730_01 `
  -SkuId 14 `
  -ExpectedOrders 20
```

### 6.5 秒杀突发

`seckill_submit` 每个线程只提交一次；线程数必须有同等数量的不同用户。由于
一人一单，同一批用户不能连续跑多个阶梯，因此一次命令只能指定一个线程档：

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -IncludeWrites `
  -Scenarios seckill_submit `
  -ThreadLevels 100 `
  -RampSeconds 2 `
  -DataFile .\scripts\jmeter\data\users.csv
```

若要比较 20、50、100 三档，应为每档准备新的活动或全新的用户数据，并分别
执行三次命令；否则后续请求会因一人一单失败，不能用于计算吞吐量。

当前 Gateway 默认按单 IP 限制秒杀请求为 20 次/秒。单机 JMeter 的结果会先测到
网关限流能力。若要测后端极限，应在隔离环境临时提高测试配置中的限流阈值，
或使用多台 JMeter 压测机；不能直接移除生产限流逻辑。

每档运行前后使用：

```powershell
.\scripts\jmeter\tools\Test-ZjzxSeckillRun.ps1 `
  -Phase Before `
  -ActivityId <activity-id> `
  -SkuId 14 `
  -ExpectedAccepted <accepted-count>

.\scripts\jmeter\tools\Test-ZjzxSeckillRun.ps1 `
  -Phase AfterConsumer `
  -ActivityId <activity-id> `
  -SkuId 14 `
  -ExpectedAccepted <accepted-count>
```

若需连续复用同一 SKU 测试多个活动，可在 `AfterConsumer` 对账通过后，通过真实
用户取消接口释放该档订单，而不是直接修改数据库：

```powershell
.\scripts\jmeter\tools\Cancel-ZjzxSeckillOrders.ps1 `
  -JtlFile <本档 samples.jtl> `
  -DataFile <本档 seckill-users-N.csv>

.\scripts\jmeter\tools\Test-ZjzxSeckillRun.ps1 `
  -Phase AfterTimeout `
  -ActivityId <activity-id> `
  -SkuId 14 `
  -ExpectedAccepted <accepted-count>
```

取消脚本仅从 JTL 读取订单号和地址 ID，再从本地忽略的 CSV 映射 Token；不会
打印 Token，也不会直接写 MySQL。执行下一档前，必须确认活动库存、Redis 库存和
物理库存均恢复到该档快照值。

幂等与一人一单专项：

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -IncludeWrites `
  -Scenarios seckill_same_request `
  -ThreadLevels 20 `
  -FixedRequestId "idem-activity-<id>-001" `
  -DataFile .\scripts\jmeter\data\seckill-idempotency-user.csv

.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -IncludeWrites `
  -Scenarios seckill_user_limit `
  -ThreadLevels 20 `
  -DataFile .\scripts\jmeter\data\seckill-user-limit-user.csv
```

消费者重启恢复必须使用全新活动：先停止 OrderService，再执行秒杀提交，然后
运行 `Test-ZjzxSeckillRun.ps1 -Phase WhileConsumerStopped`；重启 OrderService
后运行 `-Phase AfterConsumer`。前一阶段要求消息进入 RabbitMQ Ready，后一阶段
要求队列恢复基线、数据库只创建一次订单且死信不增长。

在消费者停止且 `WhileConsumerStopped` 通过后，可额外复制一条队列消息验证消费
幂等。若原本有 2 条唯一消息，复制后 RabbitMQ Ready 应为 3；重启后数据库中的
唯一请求、唯一订单号和订单仍必须分别只有 2 条：

```powershell
$env:RABBITMQ_USERNAME = "<本地管理用户>"
$env:RABBITMQ_PASSWORD = "<本地管理密码>"
.\scripts\jmeter\tools\Publish-ZjzxDuplicateSeckillMessage.ps1 `
  -ExpectedReadyBefore 2
```

### 6.6 Agent 对话

该场景会调用 DeepSeek API，存在费用和第三方限流，只进行小并发：

```powershell
.\scripts\jmeter\Run-ZjzxJmeter.ps1 `
  -IncludeAgentChat `
  -Scenarios agent_chat `
  -ThreadLevels 1,2,5 `
  -AgentChatIterationsPerThread 1 `
  -DurationSeconds 60 `
  -DataFile .\scripts\jmeter\data\users.csv `
  -Keyword "预算5000元推荐一台适合开发的电脑"
```

Gateway 默认 Agent 限流为用户 10 次/分钟、IP 30 次/分钟、会话 15 次/分钟。
压测报告应区分“真实模型吞吐”和“被网关限流后的入口吞吐”。
预计模型调用上限为所有线程档之和乘以 `AgentChatIterationsPerThread`；上例最多
调用 8 次。

报告单列 HTTP 429、HTTP 5xx、模型回退次数与
`upstream_rate_limit`。模型调用异常会被确定性导购回退兜底，因此 HTTP 200
不能证明 DeepSeek 未限流；还要查询：

```promql
sum by (reason) (zjzx_agent_model_fallbacks_total)
```

### 6.7 30 分钟混合读稳定性

五个业务服务需加载 Actuator 配置，Prometheus Targets 中 8511-8515 和
RabbitMQ 均应为 `UP`：

```powershell
.\scripts\jmeter\Run-ZjzxStabilityTest.ps1 `
  -JMeterHome $env:JMETER_HOME `
  -Threads 100 `
  -DurationSeconds 1800 `
  -SampleIntervalSeconds 15 `
  -DataFile .\scripts\jmeter\data\read-users.csv
```

混合比例为商品分页 35%、商品详情 15%、秒杀活动 15%、购物车 20%、订单列表
10%、结算预览 5%。脚本同步记录 JVM 堆、线程、Hikari、MySQL、Redis 和
RabbitMQ 队列状态。

## 7. 如何认定项目 QPS

运行器会为每个线程阶梯生成：

- `samples.jtl`：原始采样；
- `jmeter.log`：JMeter 日志；
- `qps-summary.csv`：全部阶梯汇总；
- `qps-report.md`：稳定 QPS 结论；
- 可选 HTML 报告：增加 `-GenerateHtml`。

不要使用技术错误率很高时的最大 `Qps`。秒杀报告还应同时记录请求接受 QPS 和
预期售罄拒绝率。项目某场景的可写入报告值应取：

> 满足错误率不超过 1%、P95 达标、服务无持续积压时，最高阶梯的
> `SuccessQps`。

如果 60 线程仍稳定，应继续增加线程阶梯；如果 QPS 已不再增长且 P95、错误率
持续上升，前一个稳定阶梯就是当前环境容量。

## 8. 服务端观测与写场景对账

压测期间至少观察：

- Gateway、Product、Order、Cart、Agent 的 `http.server.requests`；
- JVM CPU、堆、GC 暂停、Tomcat/Netty 活跃线程；
- Hikari 活跃连接和等待连接数；
- MySQL 慢查询、锁等待和 CPU；
- Redis 命令延迟、连接数和内存；
- RabbitMQ publish/ack、ready、unacked 和死信队列；
- Agent 请求总数、耗时、熔断和降级计数；
- PGVector 查询耗时。

订单压测后确认：

1. 成功订单数不大于库存扣减量；
2. `product_sku.stock_num >= 0`；
3. 每个成功订单只有一条有效库存预占；
4. Outbox 最终发送成功，MQ 消费日志不存在同事件重复生效；
5. 超时关闭后订单、库存预占和购物车清理最终一致。

秒杀压测后继续执行 `docs/sql/20260723_seckill_verification.sql`，并确认成功订单
不超过活动库存、同一用户没有重复秒杀订单。
