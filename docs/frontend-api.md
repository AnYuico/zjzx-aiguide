# 紫金甄选商城前台 API 文档

> 基于当前仓库源码整理，更新时间：2026-07-30。
> 本文仅覆盖商城前台接口，不包含 `zjzx-manager` 管理系统接口。
> 购物车异步扣减的专项前端改造步骤见
> [购物车异步扣减前端适配文档](cart-async-frontend-adaptation.md)。
> Agent 前端必须统一经 Gateway 访问，不得直连 8520、MCP 或任何
> `/internal/**` 接口。

## 1. 接入约定

### 1.1 网关地址

本地开发统一通过 Gateway 调用：

```text
http://127.0.0.1:8500
```

前端只保存环境相关的 BaseURL，本文后续路径均为相对路径。不要直接依赖
8511-8515 或 8520 服务端口。

### 1.2 请求格式

- JSON 请求体：`Content-Type: application/json`
- 登录凭证请求头：`token: <登录接口返回的 token>`
- 金额字段均使用 JSON number，对应后端 `BigDecimal`
- ID 字段对应 Java `Long`。若前端运行环境可能丢失大整数精度，建议内部按字符串保存和传递
- `createTime`、`updateTime` 格式为 `yyyy-MM-dd HH:mm:ss`
- 当前部分修改操作使用 GET，这是现有后端定义，前端必须按本文 HTTP Method 调用

### 1.3 鉴权

登录成功后，后端返回一个字符串 token，并以 `auth:user:token:{token}` 为 Redis Key 保存 30 天。缓存值仅包含 `userId`、`username` 和 `authVersion`，不包含密码或个人资料。

```http
token: <登录接口返回的 token>
```

本文标记为“需要登录”的接口必须携带该请求头。当前后端没有刷新 token 和退出登录接口，前端退出时清除本地 token 即可。

Gateway 会统一校验所有 `/api/**/auth/**` 路径。token 缺失、缓存不存在、
账号被停用、密码或权限版本变化时，网关返回业务码 `208`。前端收到 `208`
后应只执行一次全局会话清理，删除 token、用户信息、地址选择和动态页面状态，
再跳转登录页。

### 1.4 统一响应

商城业务接口的响应结构为：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

业务异常通常仍返回 HTTP 200，前端必须同时判断 HTTP 状态和响应体中的 `code`。

Agent 接口是独立的 Spring Boot 3.5 服务，成功响应不套上述 `Result`：

```json
{
  "answer": "根据你的预算，推荐以下商品。",
  "mode": "AI",
  "model": "deepseek-v4-flash",
  "products": [],
  "pendingActions": []
}
```

Agent 自身异常使用 HTTP 状态码和字符串错误码：

```json
{
  "code": "INVALID_REQUEST",
  "message": "请求参数格式错误"
}
```

但请求先被 Gateway 拒绝时仍使用商城响应格式，例如登录失效的 `208` 和
限流的 `242`。前端请求层必须同时兼容这两种结构。

| code | 含义 | 前端建议 |
| --- | --- | --- |
| 200 | 操作成功 | 使用 `data` |
| 201 | 用户名或密码错误 | 提示后停留在登录页 |
| 202 | 验证码错误 | 提示重新输入或获取验证码 |
| 204 | 数据异常 | 提示操作失败并刷新数据 |
| 208 | 用户未登录 | 清除 token 并跳转登录页 |
| 209 | 用户名已存在 | 提示更换账号 |
| 216 | 账号已停用 | 禁止继续登录 |
| 219 | 库存不足 | 返回商品页或刷新结算页 |
| 220 | 库存请求参数错误 | 不应由正常前端请求触发 |
| 221 | 库存预占状态异常 | 提示稍后查询订单状态 |
| 222 | 订单提交标识为空 | 生成非空 `requestId` 后提交 |
| 234 | 秒杀活动不存在 | 刷新活动列表 |
| 235 | 秒杀活动状态异常 | 禁止提交并刷新活动 |
| 236 | 秒杀活动未开始或已结束 | 禁用秒杀按钮 |
| 237 | 秒杀商品已售罄 | 展示售罄 |
| 238 | 秒杀请求已受理 | 使用原 `requestId` 查询结果 |
| 239 | 每位用户限购一件 | 禁止再次提交 |
| 240 | 秒杀请求处理失败 | 展示失败并停止轮询 |
| 241 | 秒杀请求过于频繁 | 延迟后查询原请求结果 |
| 242 | Agent 请求过于频繁 | 按 `Retry-After` 等待后再发送 |
| 243 | 当前订单状态不允许支付 | 停止支付并刷新订单状态 |
| 9999 | 系统异常 | 提示稍后重试，避免盲目重复下单 |

## 2. 接口总览

| 模块 | Method | Path | 登录 | 说明 |
| --- | --- | --- | --- | --- |
| 首页 | GET | `/api/product/index` | 否 | 首页分类与畅销商品 |
| 分类 | GET | `/api/product/category/findCategoryTree` | 否 | 完整分类树 |
| 品牌 | GET | `/api/product/brand/findAll` | 否 | 品牌列表 |
| 商品 | GET | `/api/product/{page}/{limit}` | 否 | 商品搜索与分页 |
| 商品 | GET | `/api/product/item/{skuId}` | 否 | SKU 商品详情 |
| Agent | GET | `/api/agent/health` | 否 | Agent 进程级存活检查 |
| Agent | POST | `/api/agent/guide/search` | 否 | 商品混合检索，不调用对话模型 |
| Agent | POST | `/api/agent/auth/guide/chat` | 是 | 智能导购对话与待确认动作准备 |
| Agent | POST | `/api/agent/auth/actions/{confirmationId}/confirm` | 是 | 确认或拒绝 Agent 准备的动作 |
| 秒杀 | GET | `/api/product/seckill/activities` | 否 | 已发布活动列表 |
| 秒杀 | GET | `/api/product/seckill/activity/{activityId}` | 否 | 活动与 SKU |
| 秒杀 | POST | `/api/product/seckill/auth/activity/{activityId}/sku/{skuId}/submit` | 是 | 异步提交秒杀 |
| 秒杀 | GET | `/api/product/seckill/auth/activity/{activityId}/sku/{skuId}/result/{requestId}` | 是 | 查询异步结果 |
| 用户 | GET | `/api/user/sms/sendCode/{phone}` | 否 | 发送注册验证码 |
| 用户 | POST | `/api/user/userInfo/register` | 否 | 注册 |
| 用户 | POST | `/api/user/userInfo/login` | 否 | 登录 |
| 用户 | GET | `/api/user/userInfo/auth/getCurrentUserInfo` | 是 | 当前用户信息 |
| 地址 | GET | `/api/user/userAddress/auth/findUserAddressList` | 是 | 当前用户地址列表 |
| 地址 | POST | `/api/user/userAddress/auth` | 是 | 新增当前用户收货地址 |
| 地址 | PUT | `/api/user/userAddress/auth/{id}` | 是 | 修改当前用户收货地址 |
| 地址 | DELETE | `/api/user/userAddress/auth/{id}` | 是 | 逻辑删除当前用户收货地址 |
| 地区 | GET | `/api/user/region/children/{parentCode}` | 否 | 按父编码查询下级地区 |
| 购物车 | GET | `/api/order/cart/auth/addToCart/{skuId}/{skuNum}` | 是 | 新增商品或调整数量 |
| 购物车 | GET | `/api/order/cart/auth/cartList` | 是 | 购物车列表 |
| 购物车 | DELETE | `/api/order/cart/auth/deleteCart/{skuId}` | 是 | 删除商品 |
| 购物车 | GET | `/api/order/cart/auth/checkCart/{skuId}/{isChecked}` | 是 | 单项勾选 |
| 购物车 | GET | `/api/order/cart/auth/allCheckCart/{isChecked}` | 是 | 全选或取消全选 |
| 购物车 | GET | `/api/order/cart/auth/clearCart` | 是 | 清空购物车 |
| 购物车 | GET | `/api/order/cart/auth/deleteChecked` | 是 | 删除已勾选商品 |
| 订单 | GET | `/api/order/orderInfo/auth/trade` | 是 | 购物车结算预览 |
| 订单 | GET | `/api/order/orderInfo/auth/buy/{skuId}` | 是 | 立即购买预览 |
| 订单 | POST | `/api/order/orderInfo/auth/submitOrder` | 是 | 幂等提交订单并预占库存 |
| 订单 | GET | `/api/order/orderInfo/auth/{orderId}` | 是 | 按订单 ID 查询 |
| 订单 | GET | `/api/order/orderInfo/auth/{page}/{limit}` | 是 | 当前用户订单分页 |
| 订单 | GET | `/api/order/orderInfo/auth/getOrderInfoByOrderNo/{orderNo}` | 是 | 按订单号查询 |
| 订单 | POST | `/api/order/orderInfo/auth/{orderNo}/cancel` | 是 | 幂等取消待支付订单并异步释放库存 |
| 订单 | DELETE | `/api/order/orderInfo/auth/{orderNo}` | 是 | 隐藏当前用户的已取消或已完成订单 |
| 支付 | GET | `/api/order/alipay/submitAlipay/{orderNo}` | 是 | 获取当前用户待付款订单的支付宝 WAP 支付表单 |

## 3. 商品接口

### 3.1 首页数据

```http
GET /api/product/index
```

请求参数：无。

`data` 类型：`IndexVo`

```json
{
  "categoryList": [CategoryVo],
  "productSkuList": [ProductSkuVo]
}
```

`categoryList` 只包含一级分类；`productSkuList` 为销量倒序的前 10 个在售 SKU。

### 3.2 分类树

```http
GET /api/product/category/findCategoryTree
```

请求参数：无。`data` 为 `CategoryVo[]`，子分类递归放在 `children`。

### 3.3 品牌列表

```http
GET /api/product/brand/findAll
```

请求参数：无。`data` 为 `BrandVo[]`。

### 3.4 商品分页与搜索

```http
GET /api/product/{page}/{limit}
```

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | integer | 是 | 页码，从 1 开始 |
| limit | integer | 是 | 每页数量 |

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 否 | SKU 名称模糊查询 |
| brandId | long | 否 | 品牌 ID |
| category1Id | long | 否 | 一级分类 ID |
| category2Id | long | 否 | 二级分类 ID |
| category3Id | long | 否 | 三级分类 ID |
| order | integer | 否 | `1` 销量降序，`2` 价格升序，`3` 价格降序；默认 `1` |

示例：

```http
GET /api/product/1/20?keyword=牛奶&category3Id=103&order=2
```

`data` 类型：`PageInfo<ProductSkuVo>`，不包含成本价和逻辑删除字段；仅返回商品已上架、审核通过且 SKU 状态为上架的记录。

### 3.5 商品详情

```http
GET /api/product/item/{skuId}
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| skuId | long | 是 | SKU ID |

`data` 类型：`ProductItemVo`。规格切换时，可用 `skuSpecValueMap` 根据规格 JSON 找到目标 `skuId`，然后重新请求本接口。商品或 SKU 不可售时返回业务码 204；轮播图和详情图列表会过滤空 URL。

## 4. 用户与地址接口

### 4.1 发送短信验证码

```http
GET /api/user/sms/sendCode/{phone}
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| phone | string | 是 | 注册手机号，同时作为注册 `username` |

验证码为 4 位数字，在 Redis 中有效 5 分钟。有效期内重复调用会直接返回成功，不会重新生成验证码。响应 `data` 为 `null`。

### 4.2 用户注册

```http
POST /api/user/userInfo/register
Content-Type: application/json
```

```json
{
  "username": "13800138000",
  "password": "user-password",
  "nickName": "张三",
  "code": "1234"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 必须与发送验证码时的 phone 一致 |
| password | string | 是 | 用户密码 |
| nickName | string | 是 | 昵称 |
| code | string | 是 | 4 位短信验证码 |

注册成功后 `data` 为 `null`，前端继续调用登录接口。

### 4.3 用户登录

```http
POST /api/user/userInfo/login
Content-Type: application/json
```

```json
{
  "username": "13800138000",
  "password": "user-password"
}
```

成功响应中的 `data` 是 token 字符串：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "<登录接口返回的 token>"
}
```

### 4.4 当前用户信息

```http
GET /api/user/userInfo/auth/getCurrentUserInfo
token: <token>
```

`data`：

```json
{
  "nickName": "张三",
  "avatar": "http://.../defaultIcon.png"
}
```

### 4.5 收货地址管理

```http
GET /api/user/userAddress/auth/findUserAddressList
token: <token>
```

`data` 类型：`UserAddressVo[]`，不包含地址所属用户 ID 和逻辑删除字段。
列表按默认地址优先、地址 ID 倒序返回。

新增地址：

```http
POST /api/user/userAddress/auth
token: <token>
Content-Type: application/json
```

修改地址：

```http
PUT /api/user/userAddress/auth/{id}
token: <token>
Content-Type: application/json
```

新增与修改共用请求体：

```json
{
  "name": "张三",
  "phone": "13800138000",
  "tagName": "家",
  "provinceCode": "110000",
  "cityCode": "110100",
  "districtCode": "110101",
  "address": "建国路88号1单元101室",
  "isDefault": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 收货人姓名，最多 20 个字符 |
| phone | string | 是 | 中国大陆 11 位手机号 |
| tagName | string | 否 | 地址标签，最多 20 个字符 |
| provinceCode | string | 是 | `region.level=1` 的六位编码 |
| cityCode | string | 是 | 必须属于所选省份 |
| districtCode | string | 是 | 必须属于所选城市 |
| address | string | 是 | 不包含省市区的详细地址，最多 100 个字符 |
| isDefault | integer | 否 | `0` 非默认，`1` 默认；首个地址会被自动设为默认 |

新增和修改成功时 `data` 为完整 `UserAddressVo`。后端从登录态获取
`userId`，并根据 `region` 表生成 `fullAddress`；前端不得提交这两个字段。
修改或删除其他用户的地址统一返回业务码 `244`。

删除地址：

```http
DELETE /api/user/userAddress/auth/{id}
token: <token>
```

删除采用逻辑删除。若删除的是默认地址，后端会从剩余地址中自动补选一个默认地址。

### 4.6 省市区级联

```http
GET /api/user/region/children/{parentCode}
```

- 查询省级列表：`parentCode=0`
- 查询市级列表：`parentCode=省编码`，例如 `110000`
- 查询区县列表：`parentCode=市编码`，例如 `110100`

`data` 类型为 `RegionVo[]`。无下级地区或父编码格式错误时返回空数组。
保存地址时后端仍会重新校验省、市、区的层级和父子关系，不能只依赖前端级联选择器。

## 5. 购物车接口

购物车数据保存在 Redis Hash：`user:cart:{userId}`。以下接口全部需要 token。

### 5.1 新增商品或调整数量

```http
GET /api/order/cart/auth/addToCart/{skuId}/{skuNum}
token: <token>
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| skuId | long | 是 | SKU ID |
| skuNum | integer | 是 | 新商品时为初始数量；已存在时为数量增量 |

现有实现使用“增量”语义：购物车原数量为 2，调整为 5 时传 `skuNum=3`；调整为 1 时传 `skuNum=-1`。前端应保证最终数量大于 0，数量降为 0 时调用删除接口。

### 5.2 购物车列表

```http
GET /api/order/cart/auth/cartList
token: <token>
```

`data` 类型：`CartItemVo[]`，按加入时间倒序返回；无数据时返回空数组。响应不包含 `userId` 和 Redis 内部时间字段。

### 5.3 删除单个商品

```http
DELETE /api/order/cart/auth/deleteCart/{skuId}
token: <token>
```

成功响应 `data` 为 `null`。

### 5.4 修改单项勾选状态

```http
GET /api/order/cart/auth/checkCart/{skuId}/{isChecked}
token: <token>
```

`isChecked`：`1` 选中，`0` 取消选中。

### 5.5 全选或取消全选

```http
GET /api/order/cart/auth/allCheckCart/{isChecked}
token: <token>
```

`isChecked`：`1` 全选，`0` 全部取消。

### 5.6 清空购物车

```http
GET /api/order/cart/auth/clearCart
token: <token>
```

删除当前用户的整个购物车 Redis Key。

### 5.7 删除已勾选商品

```http
GET /api/order/cart/auth/deleteChecked
token: <token>
```

该接口仅供用户手动清理已勾选项。购物车来源订单创建成功后，后端会通过 RabbitMQ
异步精确扣减本次订单中的 SKU 数量，前端不得再次调用本接口，否则可能删除用户新勾选的商品。

## 6. 订单接口

### 6.1 购物车结算预览

```http
GET /api/order/orderInfo/auth/trade
token: <token>
```

读取购物车中 `isChecked=1` 的商品，转换为订单项并计算商品总金额。

`data` 类型：

```json
{
  "orderSource": 1,
  "totalAmount": 199.80,
  "orderItemList": [OrderItemVo]
}
```

`orderSource=1` 表示购物车结算，提交订单时必须原样传回。

### 6.2 立即购买预览

```http
GET /api/order/orderInfo/auth/buy/{skuId}
token: <token>
```

返回指定 SKU、数量为 1 的 `TradeVo`。若页面允许用户改变购买数量，提交订单时在 `orderItemList[].skuNum` 中传最终正整数。
响应中的 `orderSource=2` 表示立即购买，提交订单时必须原样传回。

### 6.3 提交订单

```http
POST /api/order/orderInfo/auth/submitOrder
token: <token>
Content-Type: application/json
```

推荐请求体：

```json
{
  "requestId": "6d1238874d804475a9148a1066e6ab17",
  "orderSource": 1,
  "userAddressId": 12,
  "feightFee": 0,
  "remark": "工作日送达",
  "orderItemList": [
    {
      "skuId": 1001,
      "skuNum": 2
    },
    {
      "skuId": 1002,
      "skuNum": 1
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | string | 是 | 单次下单幂等标识，建议 UUID 去连字符，长度不超过 64 |
| orderSource | integer | 是 | `1` 购物车结算，`2` 立即购买；使用预览接口返回值 |
| userAddressId | long | 是 | 收货地址 ID |
| feightFee | number | 否 | 字段名按源码为 `feightFee`；不传时为 0 |
| remark | string | 否 | 订单备注 |
| orderItemList | array | 是 | 至少一项 |
| orderItemList[].skuId | long | 是 | SKU ID |
| orderItemList[].skuNum | integer | 是 | 正整数购买数量 |

重要规则：

1. 同一次点击或网络重试必须复用同一个 `requestId`，不要为每次重试重新生成。
2. 新的一笔订单必须生成新的 `requestId`。
3. 前端不需要提交 SKU 名称、图片和价格；即使提交，后端也会按 `skuId` 重新查询并覆盖。
4. 重复 SKU 会按 `skuId` 合并数量。
5. 后端在创建订单前预占库存，库存不足返回 `219`。
6. 后端商品总金额为实时 SKU 价格乘数量之和；`feightFee` 单独保存，当前支付宝金额使用 `totalAmount`。
7. 成功响应 `data` 是订单数据库 ID，不是订单号。
8. `orderSource=1` 时，订单创建事务会写入购物车清理 Outbox；Cart 服务异步按本次订单数量扣减。`orderSource=2` 不修改购物车。

前端生成幂等标识示例：

```javascript
const requestId = crypto.randomUUID().replaceAll('-', '')
```

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 12345
}
```

### 6.4 按订单 ID 查询

```http
GET /api/order/orderInfo/auth/{orderId}
token: <token>
```

`data` 类型：`OrderDetailVo`，包含 `orderItemList`。服务端同时按当前登录用户 ID 校验订单归属。

### 6.5 当前用户订单分页

```http
GET /api/order/orderInfo/auth/{page}/{limit}?orderStatus={orderStatus}
token: <token>
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | integer | 是 | 页码，从 1 开始 |
| limit | integer | 是 | 每页数量 |
| orderStatus | integer | 否 | 不传表示全部订单 |

订单状态：

| orderStatus | 含义 |
| --- | --- |
| -1 | 已取消 |
| 0 | 待付款 |
| 1 | 待发货 |
| 2 | 已发货 |
| 3 | 已完成/已收货 |

`data` 类型：`PageInfo<OrderDetailVo>`，每条订单会附带 `orderItemList`，且只查询当前登录用户的订单。

### 6.6 按订单号查询

```http
GET /api/order/orderInfo/auth/getOrderInfoByOrderNo/{orderNo}
token: <token>
```

`data` 类型：`OrderDetailVo`，包含 `orderItemList`，并按当前登录用户校验订单归属。

### 6.7 用户主动取消订单

```http
POST /api/order/orderInfo/auth/{orderNo}/cancel
token: <token>
```

请求体：无。`orderNo` 必须按后端返回的字符串原样传递，不能转为数字、截断或重新格式化。

处理规则：

1. 只能取消当前登录用户自己的待支付订单，即 `orderStatus=0`。
2. 取消成功后订单状态变为 `-1`，后端记录 `cancelTime`、`cancelReason` 和订单操作日志。
3. 后端在取消事务内写入库存释放 Outbox 消息，库存由 RabbitMQ 消费者异步释放；前端不需要调用库存接口。
4. 对已经取消的订单重复调用时按幂等成功处理，不会重复释放库存。
5. 已支付、已发货或已完成订单返回业务码 `232`，前端应停止支付并刷新订单状态。
6. 订单不存在或不属于当前登录用户均返回 `231`，避免泄露其他用户订单信息。

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

常见业务响应：

| code | message | 前端处理 |
| --- | --- | --- |
| 200 | 操作成功 | 关闭确认弹窗并刷新订单列表或详情 |
| 208 | 用户未登录 | 清理会话并跳转登录页 |
| 230 | 请求参数校验失败 | 检查订单号是否为空或超过 64 字符 |
| 231 | 订单不存在 | 提示订单不存在并刷新列表 |
| 232 | 当前订单状态不允许取消 | 提示状态已变化并重新查询订单 |

调用示例：

```javascript
await request(`/api/order/orderInfo/auth/${encodeURIComponent(orderNo)}/cancel`, {
  method: 'POST'
})
```

注意：当前项目对“取消后才收到支付成功回调”的情况会记录支付异常任务，但尚未实现自动退款接口。前端在取消成功后不得继续发起该订单的支付。

### 6.8 用户删除订单

```http
DELETE /api/order/orderInfo/auth/{orderNo}
token: <token>
```

请求体：无。该接口是用户侧逻辑删除，不会物理删除订单，也不会修改 `orderStatus`。

处理规则：

1. 只允许删除当前登录用户自己的已取消订单 `-1` 或已完成订单 `3`。
2. 待付款、待发货和已发货订单不能删除，返回业务码 `233`。
3. 删除成功后订单不再出现在商城订单列表、按 ID 查询和按订单号查询中。
4. 重复删除同一订单按幂等成功处理。
5. 订单业务数据、订单项、支付记录和操作日志继续保留；支付回调、MQ 补偿和后台统计不受用户删除影响。
6. 订单不存在或不属于当前登录用户均返回 `231`。

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

常见业务响应：

| code | message | 前端处理 |
| --- | --- | --- |
| 200 | 操作成功 | 从当前列表移除该订单 |
| 208 | 用户未登录 | 清理会话并跳转登录页 |
| 230 | 请求参数校验失败 | 检查订单号格式 |
| 231 | 订单不存在 | 刷新订单列表 |
| 233 | 当前订单状态不允许删除 | 提示状态变化并重新查询订单 |

调用示例：

```javascript
await request(`/api/order/orderInfo/auth/${encodeURIComponent(orderNo)}`, {
  method: 'DELETE'
})
```

前端只应在 `orderStatus === -1 || orderStatus === 3` 时展示“删除订单”按钮。后端部署前必须先执行 [订单用户删除字段迁移](sql/20260722_order_user_delete.sql)。

## 7. 支付接口

### 7.1 获取支付宝 WAP 支付表单

```http
GET /api/order/alipay/submitAlipay/{orderNo}
token: <token>
```

该接口必须携带商城 `token`。后端会校验订单属于当前登录用户、订单状态为
待付款，并核对支付记录与订单金额；订单不存在或不属于当前用户返回 `231`，
订单已取消、已支付或处于其他非待付款状态返回 `243`。

常见业务响应：

| code | message | 前端处理 |
| --- | --- | --- |
| 200 | 操作成功 | 校验表单后提交到支付宝 |
| 208 | 用户未登录 | 清理会话并跳转登录页 |
| 230 | 请求参数校验失败 | 检查订单号 |
| 231 | 订单不存在 | 停止支付并返回订单列表 |
| 243 | 当前订单状态不允许支付 | 停止支付并重新查询订单 |

成功时 `data` 是支付宝 SDK 返回的 HTML `<form>` 字符串，不是支付 URL：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "<form name=\"punchout_form\" method=\"post\" action=\"...\">...</form>"
}
```

Web/H5 端处理示例：

```javascript
const result = await request(`/api/order/alipay/submitAlipay/${orderNo}`)
const container = document.createElement('div')
container.innerHTML = result.data
document.body.appendChild(container)
container.querySelector('form').submit()
```

仅应执行本项目支付接口返回的表单 HTML。支付宝异步回调完成后，前端可重新查询订单，`orderStatus=1` 表示后端已更新为已支付/待发货。

当前代码未提供独立的支付状态查询、主动取消支付或退款接口。

## 8. 数据模型

### 8.1 BaseEntity

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键 |
| createTime | string | 创建时间，`yyyy-MM-dd HH:mm:ss` |
| updateTime | string | 更新时间，`yyyy-MM-dd HH:mm:ss` |
| isDeleted | integer | 逻辑删除标记 |

### 8.2 PageInfo<T>

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| pageNum | integer | 当前页 |
| pageSize | integer | 每页数量 |
| size | integer | 当前页实际数量 |
| total | long | 总记录数 |
| pages | integer | 总页数 |
| list | T[] | 当前页数据 |
| prePage | integer | 上一页 |
| nextPage | integer | 下一页 |
| isFirstPage | boolean | 是否第一页 |
| isLastPage | boolean | 是否最后一页 |
| hasPreviousPage | boolean | 是否有上一页 |
| hasNextPage | boolean | 是否有下一页 |
| navigatepageNums | integer[] | 导航页码 |

### 8.3 CategoryVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 分类名称 |
| imageUrl | string | 分类图片 |
| parentId | long | 父分类 ID |
| status | integer | `0` 不显示，`1` 显示 |
| orderNum | integer | 排序值 |
| hasChildren | boolean | 是否有子分类 |
| children | CategoryVo[] | 子分类 |

### 8.4 BrandVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 品牌名称 |
| logo | string | 品牌 Logo URL |

### 8.5 ProductSkuVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| skuCode | string | SKU 编码 |
| skuName | string | SKU 名称 |
| productId | long | SPU ID |
| thumbImg | string | 缩略图 URL |
| salePrice | number | 售价 |
| marketPrice | number | 市场价 |
| stockNum | integer | 库存数 |
| saleNum | integer | 销量 |
| skuSpec | string | SKU 规格 JSON 字符串 |
| weight | number | 重量，单位 kg，最多两位小数 |
| volume | number | 体积，单位 m³，最多两位小数 |
| status | integer | `1` 上架；其他值不可正常下单 |

### 8.6 ProductInfoVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 商品名称 |
| brandId | long | 品牌 ID |
| category1Id/category2Id/category3Id | long | 各级分类 ID |
| unitName | string | 计量单位 |
| status | integer | 商品上下架状态 |

### 8.7 ProductItemVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| productSku | ProductSkuVo | 当前 SKU，不包含成本价 |
| product | ProductInfoVo | SPU 商品基础信息 |
| sliderUrlList | string[] | 已拆分的轮播图 URL |
| detailsImageUrlList | string[] | 已拆分的详情图 URL |
| specValueList | object[] | 解析后的规格定义 |
| skuSpecValueMap | object | 规格 JSON 字符串到 skuId 的映射 |

### 8.8 UserAddressVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 地址 ID |
| name | string | 收货人姓名 |
| phone | string | 收货电话 |
| tagName | string | 地址标签 |
| provinceCode | string | 省编码 |
| cityCode | string | 市编码 |
| districtCode | string | 区县编码 |
| address | string | 详细地址 |
| fullAddress | string | 完整地址 |
| isDefault | integer | `0` 否，`1` 是 |

### 8.8.1 RegionVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | string | 地区编码 |
| parentCode | string | 父级地区编码，省级为 `0` |
| name | string | 地区名称 |
| level | integer | `1` 省，`2` 市，`3` 区县 |
| hasChildren | boolean | 是否还可继续查询下级地区 |

### 8.9 CartItemVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| skuId | long | SKU ID |
| cartPrice | number | 加入购物车时的价格 |
| skuNum | integer | 数量 |
| imgUrl | string | SKU 图片 |
| skuName | string | SKU 名称 |
| isChecked | integer | `0` 未选中，`1` 已选中 |

### 8.10 OrderItemVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| skuId | long | SKU ID |
| skuName | string | 下单时 SKU 名称快照 |
| thumbImg | string | 下单时图片快照 |
| skuPrice | number | 下单时价格快照 |
| skuNum | integer | 购买数量 |

### 8.11 OrderDetailVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| orderNo | string | 业务订单号 |
| orderSource | integer | `1` 购物车结算，`2` 立即购买 |
| totalAmount | number | 商品总金额 |
| couponAmount | number | 优惠金额，当前为 0 |
| originalTotalAmount | number | 原始商品金额 |
| feightFee | number | 运费，字段名按源码保留 |
| payType | integer | 当前支付宝下单使用 `2` |
| orderStatus | integer | 订单状态，见 6.5 |
| receiverName | string | 收货人 |
| receiverPhone | string | 收货电话 |
| receiverTagName | string | 地址标签 |
| receiverProvince | string | 省编码快照 |
| receiverCity | string | 市编码快照 |
| receiverDistrict | string | 区县编码快照 |
| receiverAddress | string | 完整地址快照 |
| paymentTime | string/null | 支付时间，`yyyy-MM-dd HH:mm:ss` |
| expireTime | string/null | 支付过期时间，默认下单后 30 分钟 |
| deliveryTime | string/null | 发货时间，`yyyy-MM-dd HH:mm:ss` |
| receiveTime | string/null | 收货时间，`yyyy-MM-dd HH:mm:ss` |
| remark | string/null | 备注 |
| cancelTime | string/null | 取消时间，`yyyy-MM-dd HH:mm:ss` |
| cancelReason | string/null | 取消原因 |
| createTime | string | 下单时间，`yyyy-MM-dd HH:mm:ss` |
| orderItemList | OrderItemVo[] | 订单项 |

## 9. 推荐前端调用时序

### 9.1 购物车下单

```text
登录
  -> 获取地址列表
  -> 获取购物车列表并维护勾选状态
  -> GET /orderInfo/auth/trade
  -> 生成 requestId
  -> 携带 orderSource=1 POST /orderInfo/auth/submitOrder
  -> 使用返回的 orderId 查询 OrderDetailVo
  -> 取得 orderNo
  -> GET /alipay/submitAlipay/{orderNo}
  -> 提交支付宝 HTML form
  -> 回跳后查询订单状态
```

### 9.2 立即购买

```text
GET /orderInfo/auth/buy/{skuId}
  -> 用户确认数量和地址
  -> 生成 requestId
  -> 携带 orderSource=2 POST /orderInfo/auth/submitOrder
  -> 后续支付流程同购物车下单
```

## 10. 非前端接口

以下路径存在于当前仓库，但用于 Feign 服务间调用或第三方回调，前端不得调用：

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/api/product/internal/sku/{skuId}` | Cart/Order 查询下单所需 SKU 字段 |
| POST | `/api/product/internal/inventory/reserve` | 订单服务预占库存 |
| POST | `/api/product/internal/inventory/confirm/{orderNo}` | 支付成功确认库存 |
| POST | `/api/product/internal/inventory/release/{orderNo}` | 失败或取消时释放库存 |
| GET | `/api/user/userInfo/internal/getUserInfo/{userId}` | Order Feign 查询下单所需用户昵称 |
| GET | `/api/user/userAddress/internal/getUserAddress/{userId}/{id}` | Order Feign 按用户归属查询地址 |
| GET | `/api/order/cart/internal/checked/{userId}` | Order Feign 查询指定用户已勾选购物车 DTO |
| GET | `/api/order/orderInfo/internal/getByOrderNo/{orderNo}` | Pay 服务查询订单支付信息 |
| POST | `/api/order/orderInfo/internal/markPaid/{orderNo}/{orderStatus}` | Pay 服务更新订单状态 |
| POST | `/api/product/internal/ai-guide/search` | Agent 查询脱敏商品 |
| GET | `/api/product/internal/ai-guide/sku/{skuId}` | Agent 实时校验商品快照 |
| POST | `/api/product/internal/ai-guide/knowledge/page` | Agent 全量索引商品分页 |
| GET | `/api/product/internal/ai-guide/knowledge/product/{productId}` | Agent 增量索引商品 |
| GET | `/api/user/userInfo/internal/agent/current` | Agent 解析商城 token 对应用户 |
| GET | `/api/order/cart/internal/agent/users/{userId}` | Agent 读取脱敏购物车 |
| POST | `/api/order/cart/internal/agent/users/{userId}/items` | 确认后幂等加入购物车 |
| GET | `/api/order/orderInfo/internal/agent/users/{userId}/recent` | Agent 读取脱敏近期订单 |
| GET | `/api/order/orderInfo/internal/agent/users/{userId}/cancellation-candidates/{recentPosition}` | Agent 解析待取消订单 |
| POST | `/api/order/orderInfo/internal/agent/users/{userId}/cancellations` | 确认后幂等取消待付款订单 |
| POST | `/api/agent/internal/index/products/rebuild` | Agent 商品向量全量重建 |
| GET | `/api/agent/internal/index/products/status` | Agent 商品索引状态 |
| ANY | `/api/order/alipay/callback/notify` | 支付宝异步通知，返回纯文本 `success/failure` |

所有 `/internal/**` 接口使用 `X-Internal-Token`，不得将该值放入浏览器或前端配置。
Gateway 会直接对路径中任意一段为 `internal` 的请求返回 HTTP 404，并删除外部
请求伪造的 `X-Internal-Token`。

Agent 的 MCP Server 默认路径为 8520 服务上的 `/mcp`，只面向受控 MCP 客户端；
Gateway 没有 `/mcp` 路由，商城前端不得调用或保存 `AGENT_MCP_API_KEY`。

## 11. 当前未提供的前台能力

未在当前非管理端 Controller 中发现以下接口：

- 退出登录与 token 刷新
- 订单确认收货
- 独立支付状态查询与退款
- 优惠券领取/选择、收藏、浏览历史
- Agent 会话历史、服务端多轮记忆和会话列表
- Agent SSE/WebSocket 流式输出
- Agent 直接提交订单、支付、退款、修改地址、改价或扣库存

前端重写时不要预设这些 API 已存在；对应页面应暂缓，或先补充后端接口。

## 12. 源码依据

- Gateway 路由：[application-dev.yml](../zjzx-server-gateway/src/main/resources/application-dev.yml)
- Gateway 登录鉴权：[AuthGlobalFilter.java](../zjzx-server-gateway/src/main/java/com/tzp/zjzx/gateway/filter/AuthGlobalFilter.java)
- Gateway Agent 限流：[AgentRateLimitGlobalFilter.java](../zjzx-server-gateway/src/main/java/com/tzp/zjzx/gateway/filter/AgentRateLimitGlobalFilter.java)
- Gateway 内部接口边界：[InternalApiSecurityGlobalFilter.java](../zjzx-server-gateway/src/main/java/com/tzp/zjzx/gateway/filter/InternalApiSecurityGlobalFilter.java)
- Agent 前端接口：[controller](../zjzx-agent-service/src/main/java/com/tzp/zjzx/agent/controller)
- Agent 请求、响应和确认逻辑：[service](../zjzx-agent-service/src/main/java/com/tzp/zjzx/agent/service)
- Agent 脱敏共享契约：[zjzx-ai-contract](../zjzx-ai-contract/src/main/java/com/tzp/zjzx/ai/contract)
- 商品接口：[controller](../zjzx-service/service-product/src/main/java/com/tzp/zjzx/product/controller)
- 用户接口：[controller](../zjzx-service/service-user/src/main/java/com/tzp/zjzx/user/controller)
- 购物车接口：[CartController.java](../zjzx-service/service-cart/src/main/java/com/tzp/zjzx/feign/controller/CartController.java)
- 订单接口：[OrderInfoController.java](../zjzx-service/service-order/src/main/java/com/tzp/zjzx/order/controller/OrderInfoController.java)
- 支付接口：[AlipayController.java](../zjzx-service/service-pay/src/main/java/com/tzp/zjzx/pay/controller/AlipayController.java)
- 服务内用户上下文：[UserLoginAuthInterceptor.java](../zjzx-common/common-service/src/main/java/com/tzp/zjzx/common/interceptor/UserLoginAuthInterceptor.java)
- 统一响应和业务码：[Result.java](../zjzx-model/src/main/java/com/tzp/zjzx/model/vo/common/Result.java)、[ResultCodeEnum.java](../zjzx-model/src/main/java/com/tzp/zjzx/model/vo/common/ResultCodeEnum.java)
- 请求与响应模型：[zjzx-model](../zjzx-model/src/main/java/com/tzp/zjzx/model)

## 13. 秒杀接口

### 13.1 活动列表与详情

```http
GET /api/product/seckill/activities
GET /api/product/seckill/activity/{activityId}
```

活动中的 `availableStock` 是 MySQL 对账值，高并发期间前端不能据此判断最终资格；
提交接口返回值和异步结果才是准确信息。

### 13.2 异步提交

```http
POST /api/product/seckill/auth/activity/{activityId}/sku/{skuId}/submit
token: <token>
Content-Type: application/json
```

```json
{
  "requestId": "f7f55ab34320437598f950686577f257",
  "userAddressId": 9
}
```

`requestId` 由前端为一次用户操作生成，网络重试必须复用原值，最长 64 字符。
成功受理仅代表进入队列，不代表订单已经创建：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "requestId": "f7f55ab34320437598f950686577f257",
    "orderNo": "5ff2ac33db4e4ed9bea2045690611109",
    "status": 0,
    "message": "QUEUED"
  }
}
```

网关会按 IP、用户、活动三个维度限流；HTTP `429` 且业务码 `241` 时，
不要生成新 `requestId` 盲目重试。

### 13.3 查询异步结果

```http
GET /api/product/seckill/auth/activity/{activityId}/sku/{skuId}/result/{requestId}
token: <token>
```

状态：`0=排队中`、`1=处理中`、`2=成功`、`3=失败`、`4=订单已取消`。
只有 `status=2` 时才会返回 `orderId`。建议前端以 1 秒起步并逐步放慢轮询，
在 `2/3/4` 后停止。

## 14. Agent 智能导购接口

### 14.1 前端接入边界

- 统一 BaseURL：`http://127.0.0.1:8500`
- Agent 公开接口均返回直接 JSON，不套商城 `{code,message,data}`
- `/api/agent/auth/**` 必须携带商城登录请求头 `token`
- 前端不得传 `userId`、`orderNo`、地址、支付标识或内部 Token 给 Agent
- 前端不得直连 `http://127.0.0.1:8520`
- 当前接口为普通 JSON 请求，不是 SSE 或 WebSocket 流式响应
- 当前后端未接入 ChatMemory，也没有 `conversationId`；每次聊天请求是独立一轮
- `X-Trace-Id` 仅用于排障，可展示或上报日志，不需要持久化为业务数据

### 14.2 商品导购检索

```http
POST /api/agent/guide/search
Content-Type: application/json
```

该接口不需要登录，也不调用 DeepSeek。它组合 MySQL 关键词结果和 PGVector
向量候选，并在返回前通过 Product Service 校验当前商品数据。向量检索失败时会
降级为关键词结果。

请求体：

```json
{
  "keyword": "适合编程的小型电脑",
  "limit": 6
}
```

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| keyword | string/null | 否 | 最长 50 字符；空值表示浏览当前可选商品 |
| limit | integer/null | 否 | `1-20`，不传默认 `10` |

成功响应：

```json
{
  "keyword": "适合编程的小型电脑",
  "message": "已找到 2 个相关商品。",
  "count": 2,
  "products": [
    {
      "skuId": 15,
      "productName": "Mac mini",
      "skuName": "Mac mini 16G",
      "thumbImg": "http://localhost:9000/zjzx-bucket/example.png",
      "salePrice": 4999.00,
      "marketPrice": 5499.00,
      "skuSpec": "{\"内存\":\"16G\"}",
      "unitName": "台",
      "inStock": true
    }
  ]
}
```

无结果仍为 HTTP 200，`count=0` 且 `products=[]`。参数错误返回 HTTP 400：

```json
{
  "code": "INVALID_REQUEST",
  "message": "返回商品数量必须在 1 到 20 之间"
}
```

商品服务暂不可用且降级也无法完成时，返回 HTTP 503、
`PRODUCT_CATALOG_UNAVAILABLE`。

### 14.3 智能导购聊天

```http
POST /api/agent/auth/guide/chat
token: <mall-session-token>
Content-Type: application/json
```

请求体：

```json
{
  "message": "预算 5000 元，推荐一台适合开发的电脑",
  "limit": 5
}
```

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| message | string | 是 | 非空，最长 500 字符；后端会压缩空白和过滤控制字符 |
| limit | integer/null | 否 | `1-20`；当前默认配置为 `5` |

成功响应：

```json
{
  "answer": "推荐 Mac mini 16G，价格 4999 元，当前有库存。",
  "mode": "AI",
  "model": "deepseek-v4-flash",
  "products": [
    {
      "skuId": 15,
      "productName": "Mac mini",
      "skuName": "Mac mini 16G",
      "thumbImg": "http://localhost:9000/zjzx-bucket/example.png",
      "salePrice": 4999.00,
      "marketPrice": 5499.00,
      "skuSpec": "{\"内存\":\"16G\"}",
      "unitName": "台",
      "inStock": true
    }
  ],
  "pendingActions": []
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| answer | string | 导购回答，只用于展示，不代表写操作已经完成 |
| mode | string | `AI` 或 `DETERMINISTIC_FALLBACK` |
| model | string/null | AI 模式为模型名；本地降级时为 `null` |
| products | ProductGuideVo[] | 本轮工具检索到的脱敏商品；无结果为空数组 |
| pendingActions | AgentActionPreparationVo[] | 等待用户确认的动作；没有时固定为空数组 |

DeepSeek 超时、熔断、并发隔离拒绝或输出安全检查失败时，后端会尽量返回：

```json
{
  "answer": "智能导购暂时不可用，已切换为商品检索结果。...",
  "mode": "DETERMINISTIC_FALLBACK",
  "model": null,
  "products": [],
  "pendingActions": []
}
```

前端应正常渲染降级结果，不要把 `DETERMINISTIC_FALLBACK` 当作接口失败。

### 14.4 待确认动作

当前只允许以下两种动作：

| actionType | 准备阶段 | 用户确认后的真实效果 | 成功后刷新 |
| --- | --- | --- | --- |
| `ADD_TO_CART` | 校验 SKU 并生成待确认记录 | 幂等增加当前用户购物车数量 | 购物车列表/角标 |
| `CANCEL_RECENT_ORDER` | 定位当前用户近期待付款订单 | 幂等取消订单并异步释放库存 | 订单列表和详情 |

加入购物车示例：

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
      "expiresAt": "2026-07-30T09:30:00Z",
      "requiresConfirmation": true
    }
  ]
}
```

取消订单示例：

```json
{
  "confirmationId": "6e77e7da-ec59-4d5d-88ea-8db1a88f9947",
  "actionType": "CANCEL_RECENT_ORDER",
  "summary": "取消第 1 个近期待付款订单：Mac mini 16G，金额 ¥1999.00",
  "expiresAt": "2026-07-30T10:30:00Z",
  "requiresConfirmation": true
}
```

前端必须遵守：

1. `confirmationId` 是服务端生成的不透明字符串，只能原样保存和回传。
2. 只展示后端返回的 `summary`，不要自行拼接订单号、地址或用户信息。
3. 准备动作不会修改购物车或订单，不能仅根据 `answer` 展示“操作成功”。
4. `expiresAt` 是 ISO-8601 UTC 时间；默认有效期 5 分钟，以返回值为准。
5. 一个响应可能包含多个 `pendingActions`，应按 `confirmationId` 分别维护状态。

### 14.5 确认或拒绝动作

```http
POST /api/agent/auth/actions/{confirmationId}/confirm
token: <mall-session-token>
Content-Type: application/json
```

确认执行：

```json
{
  "confirmed": true
}
```

拒绝执行：

```json
{
  "confirmed": false
}
```

请求体只发送 `confirmed`，不得再次发送 SKU、数量、订单号、动作类型或用户 ID。

确认成功：

```json
{
  "confirmationId": "d0b2abec-b950-4a6f-94f6-8f54647d2db6",
  "status": "SUCCEEDED",
  "summary": "将 Mac mini 16G x1 加入购物车",
  "message": "商品已加入购物车",
  "replayed": false
}
```

用户拒绝：

```json
{
  "confirmationId": "d0b2abec-b950-4a6f-94f6-8f54647d2db6",
  "status": "REJECTED",
  "summary": "将 Mac mini 16G x1 加入购物车",
  "message": "操作已取消",
  "replayed": false
}
```

同一个确认请求成功后重复提交，返回 HTTP 200、`status=SUCCEEDED`、
`replayed=true`，不会重复增加购物车数量或重复释放库存。请求超时或响应丢失时，
必须使用原 `confirmationId` 重试，不得重新发起聊天来制造另一个动作。

Agent 动作错误：

| HTTP | code | 前端处理 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 请求格式错误，不自动重试 |
| 401 | `AUTHENTICATION_REQUIRED` | 清理会话并重新登录 |
| 404 | `ACTION_NOT_FOUND` | 移除该动作；它不存在或不属于当前用户 |
| 409 | `ACTION_CONFLICT` | 停止重试并刷新购物车或订单状态 |
| 410 | `ACTION_EXPIRED` | 移除动作，引导用户重新发起 |
| 503 | `ACTION_UNAVAILABLE` | 保留原确认号，稍后使用原请求重试 |
| 503 | `PERSONAL_DATA_UNAVAILABLE` | 提示个人数据暂不可用 |
| 503 | `PRODUCT_CATALOG_UNAVAILABLE` | 提示商品目录暂不可用 |

注意：经 Gateway 请求且 token 无效时，实际返回通常是 HTTP 200、商城业务码
`208`，而不是上表的 HTTP 401。

### 14.6 限流

Gateway 仅对聊天接口 `/api/agent/auth/guide/chat` 执行 Redis Lua 限流，默认值：

| 维度 | 默认限制 |
| --- | ---: |
| 当前用户 | 10 次/分钟 |
| 客户端 IP | 30 次/分钟 |
| 当前 token 会话 | 15 次/分钟 |

触发后返回：

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
Content-Type: application/json
```

```json
{
  "code": 242,
  "message": "请求过于频繁，请稍后重试",
  "data": null
}
```

前端应在等待期禁用发送按钮并显示倒计时。不要自动高速重试；商品检索接口和
动作确认接口不受这条聊天限流规则影响。

### 14.7 推荐前端状态机

```text
发送聊天
  -> 处理 208：清理登录态
  -> 处理 429/242：按 Retry-After 等待
  -> 渲染 answer、products
  -> pendingActions 为空：本轮结束
  -> pendingActions 非空：显示确认/取消按钮
       -> confirmed=false：状态 REJECTED，移除动作
       -> confirmed=true：按钮进入 loading
            -> SUCCEEDED：按 actionType 刷新购物车或订单
            -> 404/409/410：移除动作并刷新相关业务数据
            -> 503/网络未知：保留原 confirmationId，允许原样重试
```

建议以前端本地消息 ID 防止一次点击重复发送聊天；写操作的最终幂等性由后端
`confirmationId`、PostgreSQL 条件更新、Redis Lua 或 MySQL 条件更新保证。

### 14.8 前端类型参考

```typescript
export type AgentMode = 'AI' | 'DETERMINISTIC_FALLBACK'
export type AgentActionType = 'ADD_TO_CART' | 'CANCEL_RECENT_ORDER'

export interface ProductGuide {
  skuId: number | string
  productName: string
  skuName: string
  thumbImg: string | null
  salePrice: number
  marketPrice: number | null
  skuSpec: string | null
  unitName: string | null
  inStock: boolean
}

export interface AgentPendingAction {
  confirmationId: string
  actionType: AgentActionType
  summary: string
  expiresAt: string
  requiresConfirmation: true
}

export interface AgentChatResponse {
  answer: string
  mode: AgentMode
  model: string | null
  products: ProductGuide[]
  pendingActions: AgentPendingAction[]
}

export interface AgentActionResult {
  confirmationId: string
  status: 'SUCCEEDED' | 'REJECTED'
  summary: string
  message: string
  replayed: boolean
}
```

### 14.9 存活检查

```http
GET /api/agent/health
```

```json
{
  "status": "UP",
  "service": "zjzx-agent-service"
}
```

该接口只表示 Agent Web 进程可响应，不代表 DeepSeek、PGVector、RabbitMQ 和所有
商城依赖均可用。前端可以用于开发环境连通性提示，不能据此判断聊天一定成功。

## 15. Agent 开发后的前端接口变更

| 变更 | 类型 | 对现有前端的影响 |
| --- | --- | --- |
| Gateway 新增 `/api/agent/**` 静态路由 | 新增 | Agent 页面统一使用原 8500 BaseURL |
| `GET /api/agent/health` | 新增 | 可选的开发环境连通性检查 |
| `POST /api/agent/guide/search` | 新增 | 可实现不登录的语义商品搜索 |
| `POST /api/agent/auth/guide/chat` | 新增 | 需要 token，兼容直接 JSON 和降级模式 |
| `POST /api/agent/auth/actions/{confirmationId}/confirm` | 新增 | 实现明确确认、拒绝、原确认号重试 |
| Gateway 统一保护 `/api/**/auth/**` | 行为收紧 | 全局处理商城业务码 `208` |
| Agent 聊天按用户/IP/会话限流 | 新增 | 处理 HTTP 429、业务码 `242`、`Retry-After` |
| 商品新增、修改、上下架触发 MQ 增量索引 | 后端内部 | 原商品接口无需改；Agent 结果会做实时商品校验 |
| Agent 确认加入购物车 | 新增入口 | 成功后复用现有购物车列表接口刷新 |
| Agent 确认取消待付款订单 | 新增入口 | 成功后复用现有订单列表/详情接口刷新 |
| 购物车来源订单提交后由 MQ 精确扣减 | 行为变化 | 下单成功后前端不得再调用 `deleteChecked` |
| 订单取消通过 Outbox/MQ 异步释放库存 | 行为变化 | 前端只刷新订单，不得调用库存内部接口 |
| `/internal/**` 在 Gateway 统一返回 404 | 安全收紧 | 前端不得依赖任何服务间接口 |
| MCP `/mcp` 不经 Gateway 暴露 | 安全边界 | 浏览器端无需也不得适配 MCP |

现有首页、分类、品牌、商品详情、登录、地址列表、购物车 CRUD、订单提交、
订单查询、用户直接取消/删除订单、支付和秒杀接口的公开路径未因 Agent 接入而
改变。Agent 的个人读取工具和写操作最终都复用这些业务数据，但不会向浏览器
暴露 `userId`、真实订单号、地址、电话、支付信息或内部服务 Token。
