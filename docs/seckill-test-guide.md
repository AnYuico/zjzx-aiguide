# 秒杀 V1 验证指南

## 前置条件

1. 执行 `docs/sql/20260723_seckill_v1.sql`。
2. 启动 MySQL、Redis、Nacos、RabbitMQ、Gateway、Product、User、Order。
3. 创建并发布一个活动，准备多个测试用户 Token 及各自有效地址。
4. `TOKENS` 中每个 Token 对应不同用户；一人一单测试不能只复用同一 Token。

当前活动维护接口是服务间接口，网关会拒绝 `/internal/**`。本地测试从
Product 服务所在机器调用；执行前把时间、SKU 和发布路径中的活动 ID
替换为本次测试数据：

```powershell
$headers = @{
  "X-Internal-Token" = $env:ZJZX_INTERNAL_API_TOKEN
  "Content-Type" = "application/json"
}
$body = @{
  name = "秒杀冒烟活动"
  startTime = "2026-07-23 19:00:00"
  endTime = "2026-07-23 20:00:00"
  skuList = @(
    @{ skuId = 101; seckillPrice = 99.00; totalStock = 10 }
  )
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8511/api/product/internal/seckill/activities" `
  -Headers $headers -Body $body
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8511/api/product/internal/seckill/activities/1/publish" `
  -Headers $headers
```

## k6 并发验证

```powershell
$env:BASE_URL="http://localhost:8500"
$env:ACTIVITY_ID="1"
$env:SKU_ID="101"
$env:TOKENS="token-user-1,token-user-2,token-user-3"
$env:ADDRESS_IDS="9,15,21"
$env:VUS="20"
$env:ITERATIONS="100"
k6 run scripts/k6/seckill-smoke.js
```

同一个 `RUN_ID` 会让同一用户复用相同 `requestId`，可验证重复请求不会重复建单。
更换 `RUN_ID` 后仍复用同一批用户，可验证一人一单限制。
`ADDRESS_IDS` 必须与 `TOKENS` 按顺序对应；单用户测试可以只设置 `ADDRESS_ID`。

## 故障恢复验证

1. 停止 Order 服务，提交秒杀请求，确认 Redis `pending` 在发布确认后清除且 RabbitMQ 队列有消息。
2. 重启 Order，确认 `seckill_order_request` 最终变为 `SUCCESS` 或明确的 `FAILED`。
3. 停止 RabbitMQ 后提交请求，确认 Redis `pending` 保留；恢复 RabbitMQ 后等待 Product 定时任务重发。
4. 对未支付秒杀订单触发超时，确认物理库存释放后请求状态变为 `CANCELLED`，活动库存只增加一次。
5. 重复投递同一消息，确认 `request_id`、`order_no` 和 `(activity_id,user_id,sku_id)` 均无重复记录。

最后执行 `docs/sql/20260723_seckill_verification.sql`。所有检查应为 `PASS`，
并人工确认 `successful_orders <= total_stock`、`product_sku.stock_num >= 0`。
