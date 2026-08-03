# 秒杀活动后台管理 API

## 1. 调用约定

- 管理端 Base URL：`http://localhost:8501`
- 请求头：`token: <管理员登录 token>`
- 前端只调用 `/admin/product/seckill/**`，不要调用商品服务的
  `/api/product/internal/seckill/**`。
- 所有响应使用统一结构：`{ "code": 200, "message": "操作成功", "data": ... }`。
- 当前接口受后台登录拦截器保护；`X-Internal-Token` 由管理服务在服务间调用时添加，
  前端不传该请求头。

## 2. 活动状态

| 状态值 | 含义 | 可编辑 | 可发布 | 可下架 |
| --- | --- | --- | --- | --- |
| 0 | 草稿 | 是 | 是 | 是 |
| 1 | Redis 预热中 | 否 | 否 | 是 |
| 2 | 已发布 | 否 | 重复发布为幂等操作 | 是 |
| 3 | 下架收敛中 | 否 | 否 | 重复下架为幂等操作 |
| 4 | 已结束 | 否 | 否 | 重复下架为幂等操作 |

已发布活动不允许直接改价格、活动库存或 SKU。需要变更时先下架，再新建活动，
避免 Redis 活动库存与 MySQL 配置在运行中分叉。

## 3. 活动列表

```http
GET /admin/product/seckill/{page}/{limit}?status={status}
token: <token>
```

- `page` 从 1 开始。
- `limit` 范围为 1 至 100。
- `status` 可选，取值为 0 至 4。
- 列表按创建时间、活动 ID 倒序。

响应中的 `data` 为 PageHelper `PageInfo`，活动列表位于 `data.list`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 1,
    "pageNum": 1,
    "pageSize": 10,
    "list": [
      {
        "id": 1,
        "name": "周末限时秒杀",
        "startTime": "2026-07-25 10:00:00",
        "endTime": "2026-07-25 12:00:00",
        "status": 0,
        "skuCount": 2,
        "createTime": "2026-07-24 12:00:00",
        "updateTime": "2026-07-24 12:00:00",
        "skuList": null
      }
    ]
  }
}
```

## 4. 选择参与活动的 SKU

复用现有商品管理接口，不新增重复的商品查询：

```http
GET /admin/product/product/{page}/{limit}
GET /admin/product/product/getById/{productId}
```

前端先分页选择商品，再从商品详情的 `productSkuList` 中选择 SKU。创建活动时使用：

- `productSkuList[].id` 作为 `skuId`。
- `salePrice` 作为秒杀价上限提示。
- `stockNum` 作为活动库存上限提示。
- 仅允许选择 `status = 1` 的可售 SKU。

前端校验用于及时提示，商品服务保存活动时仍会重新查询 SKU 并执行相同校验。

## 5. 活动详情

```http
GET /admin/product/seckill/getById/{activityId}
token: <token>
```

`skuList` 返回管理端需要的完整活动配置：

```json
{
  "id": 1,
  "name": "周末限时秒杀",
  "startTime": "2026-07-25 10:00:00",
  "endTime": "2026-07-25 12:00:00",
  "status": 0,
  "skuCount": 1,
  "createTime": "2026-07-24 12:00:00",
  "updateTime": "2026-07-24 12:00:00",
  "skuList": [
    {
      "id": 1,
      "skuId": 101,
      "skuName": "示例商品 SKU",
      "thumbImg": "http://...",
      "originalPrice": 199.00,
      "seckillPrice": 99.00,
      "totalStock": 10,
      "availableStock": 10,
      "limitPerUser": 1,
      "status": 0
    }
  ]
}
```

## 6. 新建活动

```http
POST /admin/product/seckill/save
token: <token>
Content-Type: application/json
```

```json
{
  "name": "周末限时秒杀",
  "startTime": "2026-07-25 10:00:00",
  "endTime": "2026-07-25 12:00:00",
  "skuList": [
    {
      "skuId": 101,
      "seckillPrice": 99.00,
      "totalStock": 10
    }
  ]
}
```

校验规则：

1. 活动名称必填且不超过 100 个字符。
2. 结束时间必须晚于开始时间和当前时间。
3. 单个活动包含 1 至 100 个不重复 SKU。
4. SKU 必须存在且处于可售状态。
5. 秒杀价不能为负，不能高于 SKU 当前售价，最多两位小数。
6. 活动库存必须大于 0，且不能超过 SKU 当前物理库存。
7. 当前版本每名用户对每个活动 SKU 限购 1 件。

成功时 `data` 为新活动 ID，活动初始状态为 0。

## 7. 修改草稿

```http
PUT /admin/product/seckill/updateById/{activityId}
token: <token>
Content-Type: application/json
```

请求体与新建活动一致。修改仅允许状态为 0 的草稿活动；后端在同一个本地事务内：

1. 条件更新活动时间和名称。
2. 删除旧草稿 SKU 配置。
3. 重新校验并写入新的 SKU、价格和活动库存。

任意 SKU 校验或写入失败时，整个修改事务回滚。

## 8. 发布活动

```http
POST /admin/product/seckill/publish/{activityId}
token: <token>
```

发布过程依次执行：

1. 活动由草稿进入预热中。
2. 将活动时间、价格和活动库存预热到 Redis，准入状态保持关闭。
3. MySQL 活动和 SKU 状态更新为已发布、可用。
4. 开启 Redis 秒杀准入。

重复发布已发布活动是幂等操作。发布与下架并发时，发布流程会再次检查 MySQL
最终状态；若活动已经进入下架流程，会重新关闭 Redis 准入并返回状态错误。

## 9. 下架活动

```http
POST /admin/product/seckill/offline/{activityId}
token: <token>
```

- 草稿下架：MySQL 活动和 SKU 直接更新为已结束，不创建 Redis 活动数据。
- 已发布活动下架：先关闭 Redis 新请求准入，再将活动更新为下架收敛中。
- 维护任务等待待发布消息、处理中请求和结果写回收敛后，将活动更新为已结束。
- 状态为 3 或 4 时重复下架直接返回成功。

默认收敛宽限时间为 5 分钟，最大等待时间为 30 分钟，可通过
`SECKILL_ENDING_GRACE_MS` 和 `SECKILL_FORCE_FINISH_GRACE_MS` 配置。

## 10. 常见业务码

| code | 含义 | 前端处理 |
| --- | --- | --- |
| 200 | 成功 | 刷新列表或详情 |
| 208 | 管理员登录失效 | 清理会话并跳转登录页 |
| 230 | 请求参数校验失败 | 展示后端 message |
| 234 | 活动不存在 | 刷新列表 |
| 235 | 活动状态不允许当前操作 | 刷新详情和状态 |
| 9999 | 商品服务不可达或系统异常 | 保留表单并提示稍后重试 |

## 11. 运行配置

管理服务需要与商品服务使用相同的内部 Token：

```text
ZJZX_INTERNAL_API_TOKEN=<本机环境变量中的共享内部 Token>
PRODUCT_SERVICE_BASE_URL=http://127.0.0.1:8511
```

本次接口不需要修改数据库表结构，使用现有的 `seckill_activity` 和
`seckill_sku` 表。
