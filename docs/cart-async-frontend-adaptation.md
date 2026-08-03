# 购物车异步扣减前端适配文档

> 适用范围：商城 H5/用户端前端  
> 更新时间：2026-07-23  
> 对应后端：OrderService、CartService、RabbitMQ Outbox、Redis Lua

## 1. 改造说明

本次后端将“订单创建成功后清理购物车”从同步 Feign 调用改为 RabbitMQ 异步事件：

```text
提交订单
  -> 同步预占库存
  -> 创建订单并写入购物车清理 Outbox
  -> 返回订单 ID
  -> RabbitMQ 投递清理事件
  -> CartService 使用 Redis Lua 精确扣减本次购买数量
```

订单提交接口会先返回，购物车 Redis 数据可能在短时间后才完成更新。购物车清理失败不会改变已经创建成功的订单。

## 2. 前端必须修改的内容

### 2.1 保存结算来源

两个结算预览接口新增 `orderSource`：

| 场景 | 接口 | orderSource |
| --- | --- | --- |
| 购物车结算 | `GET /api/order/orderInfo/auth/trade` | `1` |
| 立即购买 | `GET /api/order/orderInfo/auth/buy/{skuId}` | `2` |

购物车结算响应示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "orderSource": 1,
    "totalAmount": 2999.00,
    "orderItemList": [
      {
        "skuId": 14,
        "skuName": "Mac mini",
        "skuPrice": 2999.00,
        "skuNum": 1,
        "thumbImg": "http://..."
      }
    ]
  }
}
```

前端应将 `data.orderSource` 保存在当前结算页状态中，不允许用户编辑。

### 2.2 提交订单时传递 orderSource

```http
POST /api/order/orderInfo/auth/submitOrder
token: <token>
Content-Type: application/json
```

购物车结算：

```json
{
  "requestId": "6d1238874d804475a9148a1066e6ab17",
  "orderSource": 1,
  "userAddressId": 12,
  "feightFee": 0,
  "remark": "",
  "orderItemList": [
    {
      "skuId": 14,
      "skuNum": 1
    }
  ]
}
```

立即购买必须传：

```json
{
  "orderSource": 2
}
```

缺少 `orderSource` 或传入其他值时，后端返回业务码 `230`。成功响应仍为订单数据库 ID，响应结构没有变化。

### 2.3 删除旧的下单后清理调用

提交订单成功后，前端不得再调用：

```http
GET /api/order/cart/auth/deleteChecked
```

该接口会删除调用时所有已勾选商品，可能误删用户在订单提交后新勾选的商品。它只保留给用户主动执行“删除已选商品”的操作，不能作为下单流程的一部分。

## 3. 异步状态处理

### 3.1 推荐处理方式

订单提交成功后：

1. 使用本次提交的 `orderItemList` 乐观更新前端购物车状态。
2. 直接进入订单详情或支付页面，不等待购物车消息消费。
3. 用户下次进入购物车页面时，重新调用购物车列表接口。
4. 以服务器返回的购物车数据覆盖本地缓存。

购物车列表接口保持不变：

```http
GET /api/order/cart/auth/cartList
token: <token>
```

不建议在订单成功后立即连续请求购物车列表，因为消息可能尚未消费，第一次查询仍可能得到旧数据。

### 3.2 本地精确扣减

不要固定删除整个 SKU，应按本次下单数量处理：

```javascript
function applySubmittedItems(cartItems, submittedItems) {
  const purchased = new Map(
    submittedItems.map(item => [String(item.skuId), item.skuNum])
  )

  return cartItems.flatMap(item => {
    const quantity = purchased.get(String(item.skuId))
    if (!quantity) return [item]

    const remaining = item.skuNum - quantity
    return remaining > 0
      ? [{ ...item, skuNum: remaining, isChecked: 0 }]
      : []
  })
}
```

该本地更新仅用于页面即时反馈，Redis 中的最终结果以后端异步消费结果为准。

## 4. requestId 与重复提交

- 同一次下单请求重试必须复用同一个 `requestId`。
- 不得为了等待购物车清理而生成新 `requestId` 再次提交订单。
- 后端会对订单提交、Outbox 事件和 Redis 清理分别进行幂等控制。
- 网络超时但无法确定订单是否创建时，应使用原 `requestId` 重试提交。

## 5. 兼容发布建议

为避免新后端上线后旧前端缺少 `orderSource`：

```javascript
const orderSource =
  tradeData.orderSource ?? (checkoutEntry === 'cart' ? 1 : 2)
```

推荐发布顺序：

1. 前端先支持 `orderSource`，并按入口提供 `1/2` 兜底值。
2. 数据库执行 `docs/sql/20260723_cart_async_cleanup.sql`。
3. 部署 OrderService 和 CartService。
4. 确认 RabbitMQ 中存在 `zjzx.cart.cleanup` 及其死信队列。

## 6. 无需修改的接口

以下前端接口路径、方法和响应结构均未改变：

- 添加购物车
- 查询购物车列表
- 修改商品数量
- 单项勾选与全选
- 删除单个商品与清空购物车
- 订单详情、订单列表和支付接口

订单详情响应会新增 `orderSource` 字段，原有页面不读取该字段也不会受影响。

## 7. 联调验收用例

| 用例 | 操作 | 预期结果 |
| --- | --- | --- |
| 购物车单商品下单 | 购物车中 1 件商品提交订单 | 订单成功，购物车最终不再包含该商品 |
| 部分数量下单 | 当前数量 3，下单数量 1 | 购物车最终保留 2，且取消勾选 |
| 多 SKU 下单 | 勾选多个 SKU 提交 | 只扣减订单中对应 SKU |
| 未勾选商品 | 购物车存在未勾选 SKU | 下单后该 SKU 保持不变 |
| 立即购买 | 从商品详情进入立即购买 | 订单成功，原购物车不发生变化 |
| 重复提交 | 相同 requestId 重试 | 返回同一订单，不重复扣减购物车 |
| 消息重复投递 | 重投同一清理消息 | Redis 购物车只处理一次 |
| 消息消费延迟 | 提交后立即进入支付页 | 订单流程正常，不因购物车暂未更新报错 |
| 库存不足 | 提交订单返回 219 | 不创建订单，不清理购物车 |

## 8. 前端检查清单

- [ ] 结算页状态包含 `orderSource`
- [ ] 购物车入口使用 `1`
- [ ] 立即购买入口使用 `2`
- [ ] 提交订单请求携带 `orderSource`
- [ ] 下单成功后不调用 `deleteChecked`
- [ ] 成功后本地按 SKU 数量乐观扣减
- [ ] 再次进入购物车时重新请求服务器数据
- [ ] 网络重试复用原 `requestId`
- [ ] 对业务码 `219`、`230` 和 `208` 分别处理
