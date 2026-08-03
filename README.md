# 紫金甄选商城

紫金甄选商城是一个基于 Java 17 和 Spring Cloud 构建的前后端分离微服务商城。

项目包含商品、用户、购物车、订单、支付、秒杀和后台管理等常见电商模块，并提供独立的智能导购服务，支持商品检索、个人订单工具、MCP 接入和写操作确认。

商城业务服务基于 Spring Boot 3.0.5，智能导购服务基于 Spring Boot 3.5.15 和 Spring AI 1.1.8。两套服务通过 HTTP、RabbitMQ 以及脱敏 DTO 进行交互。

## 功能概览

* 使用 Spring Cloud Gateway、Nacos 和 OpenFeign 完成服务治理
* 使用 MySQL 条件更新、库存预占和幂等控制处理库存与订单
* 使用 RabbitMQ、Outbox、消费日志和补偿任务保证最终一致性
* 使用 Redis Hash 存储购物车，并通过 Lua 脚本原子清理已下单商品
* 使用 Redis Lua、RabbitMQ 和 MySQL 唯一约束实现秒杀链路
* 支持 DeepSeek Tool Calling、Ollama Embedding 和 PGVector 混合检索
* 提供只读 MCP Server，以及需要用户确认的 Agent 写操作
* 接入 OpenTelemetry、Prometheus、Tempo 和 Grafana

## 服务说明

| 模块                    |   端口 | 说明                 |
| --------------------- | ---: | ------------------ |
| `zjzx-server-gateway` | 8500 | 商城统一 API 网关        |
| `zjzx-manager`        | 8501 | 后台管理接口             |
| `service-product`     | 8511 | 商品、库存和秒杀活动         |
| `service-user`        | 8512 | 用户、地址和地区数据         |
| `service-cart`        | 8513 | Redis 购物车          |
| `service-order`       | 8514 | 普通订单、超时关单和秒杀订单     |
| `service-pay`         | 8515 | 支付回调和支付事件          |
| `zjzx-agent-service`  | 8520 | 智能导购、RAG、MCP 和个人工具 |

业务服务默认绑定到 `127.0.0.1`。

部署到服务器时，只应对外开放网关端口。`8511` 至 `8515` 以及 Agent 内部接口不应直接暴露到公网。

## 环境要求

### 商城基础环境

* JDK 17
* Maven 3.8+
* Docker Desktop 或其他兼容的容器运行时
* MySQL 8.0.30
* Redis 7.0.10
* Nacos 2.2.2
* RabbitMQ 4.3.2 Management

### 可选组件

以下组件不是商城最小启动依赖：

* MinIO：用于头像和商品图片上传
* PostgreSQL 16 与 PGVector 0.8.2：用于 Agent 向量检索
* Ollama 0.32.3 与 `bge-m3`：用于生成商品向量
* JMeter 5.6.3：用于性能测试
* Prometheus、Tempo 和 Grafana：用于监控和链路追踪

## 快速启动顺序

首次启动建议按以下顺序操作：

1. 配置环境变量
2. 启动 MySQL、Redis、Nacos 和 RabbitMQ
3. 准备基础数据库并执行增量脚本
4. 编译项目
5. 启动商品、用户、购物车、订单和支付服务
6. 启动网关和后台管理服务
7. 按需启动 MinIO、Agent 和可观测性组件

## 配置环境变量

仓库不包含真实密码、API Key、支付密钥或服务器地址。

以下示例适用于 PowerShell。请将尖括号中的内容替换为本地配置。

```powershell
# MySQL
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_DATABASE = "db_zjzx"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "<mysql-password>"

# Redis
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = "<redis-password>"

# Nacos
$env:NACOS_SERVER_ADDR = "127.0.0.1:8848"

# RabbitMQ
$env:RABBITMQ_HOST = "127.0.0.1"
$env:RABBITMQ_PORT = "5672"
$env:RABBITMQ_USERNAME = "<rabbitmq-user>"
$env:RABBITMQ_PASSWORD = "<rabbitmq-password>"
$env:RABBITMQ_VHOST = "/"

# 服务间认证
# 所有后端服务必须使用同一个值，建议至少包含 32 个随机字符
$env:ZJZX_INTERNAL_API_TOKEN = "<at-least-32-random-characters>"

# MinIO
$env:MINIO_ENDPOINT = "http://127.0.0.1:9000"
$env:MINIO_ACCESS_KEY = "<minio-user>"
$env:MINIO_SECRET_KEY = "<minio-password>"
$env:MINIO_BUCKET = "zjzx-bucket"
$env:DEFAULT_AVATAR_URL = "http://127.0.0.1:9000/zjzx-bucket/defaultIcon.png"
```

PowerShell 中设置的环境变量只对当前窗口有效。

使用 IDEA 启动服务时，需要将同一组环境变量加入各个 Spring Boot 运行配置。也可以写入本地配置文件，但该文件不能提交到 Git。

## 启动基础设施

### 创建 Docker 网络

```powershell
docker network create zjzx-net
```

如果网络已经存在，Docker 会提示冲突，可以忽略该步骤。

### MySQL

```powershell
docker run -d --name zjzx-mysql --network zjzx-net `
  -p 127.0.0.1:3306:3306 `
  -e MYSQL_ROOT_PASSWORD=$env:MYSQL_PASSWORD `
  -e MYSQL_DATABASE=$env:MYSQL_DATABASE `
  -e TZ=Asia/Shanghai `
  -v zjzx-mysql-data:/var/lib/mysql `
  --restart unless-stopped mysql:8.0.30
```

### Redis

```powershell
docker run -d --name zjzx-redis --network zjzx-net `
  -p 127.0.0.1:6379:6379 `
  -v zjzx-redis-data:/data `
  --restart unless-stopped redis:7.0.10 `
  redis-server --appendonly yes --requirepass $env:REDIS_PASSWORD
```

### Nacos

```powershell
docker run -d --name zjzx-nacos --network zjzx-net `
  -p 127.0.0.1:8848:8848 `
  -p 127.0.0.1:9848:9848 `
  -e MODE=standalone `
  --restart unless-stopped nacos/nacos-server:v2.2.2
```

本地环境通常不需要导入额外的 Nacos 配置。

数据库、Redis 和 RabbitMQ 配置直接从环境变量读取，Nacos 主要用于服务注册和发现。

### RabbitMQ

先拉取镜像：

```powershell
docker pull rabbitmq:4.3.2-management
```

再运行仓库中的部署脚本：

```powershell
.\scripts\docker\deploy-rabbitmq.ps1
```

脚本会优先读取环境变量。缺少账号或密码时，会提示在终端中输入。

RabbitMQ 管理页面：

```text
http://127.0.0.1:15672
```

### MinIO

MinIO 仅在使用头像或商品图片上传功能时需要。

```powershell
docker run -d --name zjzx-minio --network zjzx-net `
  -p 127.0.0.1:9000:9000 `
  -p 127.0.0.1:9001:9001 `
  -e MINIO_ROOT_USER=$env:MINIO_ACCESS_KEY `
  -e MINIO_ROOT_PASSWORD=$env:MINIO_SECRET_KEY `
  -v zjzx-minio-data:/data `
  --restart unless-stopped minio/minio server /data --console-address ":9001"
```

启动后创建名为 `zjzx-bucket` 的 Bucket。

如需在前端直接访问图片，只开放图片对象的匿名读取权限。不要开放匿名上传、删除或对象列表权限。

## 初始化数据库

`docs/sql` 目录保存的是项目升级过程中使用的增量脚本，不包含完整的初始数据库。

首次运行前，需要自行准备以下基础数据：

* `db_zjzx` 数据库结构
* 演示商品数据
* 地区数据
* 后台菜单和权限数据

准备好基础数据库后，再根据当前数据库状态执行增量脚本。

全新升级环境建议依次检查：

1. `docs/sql/20260721_bcrypt_password.sql`
2. `docs/sql/20260711_inventory_reservation.sql`
3. `docs/sql/20260722_rabbitmq_outbox.sql`
4. `docs/sql/20260723_cart_async_cleanup.sql`
5. `docs/sql/20260723_seckill_v1.sql`
6. `docs/sql/20260722_schema_integrity_check.sql`

名称中包含 `20260722_*_repair.sql` 的脚本用于修复已经部分升级的数据库。

不要在不了解现有表结构的情况下批量执行所有修复脚本，否则可能造成重复字段、重复索引或数据覆盖。

执行完整性检查后，结果中的 `FAIL` 和 `SKIPPED` 必须处理。数据库结构不完整时，不应启动订单和秒杀服务。

## 编译与测试

在仓库根目录执行：

```powershell
mvn -DskipTests package
```

执行全部单元测试：

```powershell
mvn test
```

## 启动商城服务

推荐在 IDEA 中分别启动以下主类：

1. `ProductApplication8511`
2. `UserApplication8512`
3. `CartApplication8513`
4. `OrderApplication8514`
5. `PayApplication8515`
6. `GatewayApplication8500`
7. `ManagerApplication8501`

也可以先执行 Maven 打包，再使用各模块生成的 Spring Boot Jar 启动。

所有进程都必须继承前面配置的环境变量，特别是数据库、Redis、RabbitMQ 和 `ZJZX_INTERNAL_API_TOKEN`。

### 健康检查

服务启动后，访问以下地址检查运行状态：

```text
http://127.0.0.1:8511/actuator/health
http://127.0.0.1:8512/actuator/health
http://127.0.0.1:8513/actuator/health
http://127.0.0.1:8514/actuator/health
http://127.0.0.1:8515/actuator/health
http://127.0.0.1:8500/actuator/health
```

### 前端接口地址

商城前端 API BaseURL：

```text
http://127.0.0.1:8500
```

后台管理前端 API BaseURL：

```text
http://127.0.0.1:8501
```

## 启动智能导购服务

智能导购服务是可选模块。只运行商城基础功能时，可以跳过本节。

### 部署 PGVector 和 Ollama

配置 PostgreSQL 账号：

```powershell
$env:AGENT_PGVECTOR_USERNAME = "zjzx_agent"
$env:AGENT_PGVECTOR_PASSWORD = "<postgres-password>"
```

部署 PGVector：

```powershell
docker pull pgvector/pgvector:0.8.2-pg16-bookworm
.\scripts\docker\deploy-pgvector.ps1
```

部署 Ollama Embedding：

```powershell
docker pull ollama/ollama:0.32.3
.\scripts\docker\deploy-ollama-embedding.ps1
```

在 `zjzx_agent` PostgreSQL 数据库中执行：

1. `docs/sql/20260728_agent_product_index_mq.sql`
2. `docs/sql/20260729_agent_action_request.sql`
3. `docs/sql/20260729_agent_action_cancel_order.sql`

### 配置 Agent

```powershell
$env:AGENT_PGVECTOR_URL = "jdbc:postgresql://127.0.0.1:5432/zjzx_agent"

$env:SPRING_AI_MODEL_EMBEDDING = "ollama"
$env:SPRING_AI_VECTORSTORE_TYPE = "pgvector"
$env:AGENT_VECTOR_ENABLED = "true"

$env:OLLAMA_BASE_URL = "http://127.0.0.1:11434"
$env:OLLAMA_EMBEDDING_MODEL = "bge-m3"
```

配置 DeepSeek 对话模型：

```powershell
$env:SPRING_AI_MODEL_CHAT = "openai"
$env:DEEPSEEK_API_KEY = "<your-deepseek-api-key>"
$env:DEEPSEEK_BASE_URL = "https://api.deepseek.com"
$env:DEEPSEEK_MODEL = "deepseek-v4-flash"
```

DeepSeek 配置不是必需项。未配置对话模型时，Agent 会使用确定性检索模式。

启用商品增量索引和个人工具：

```powershell
$env:AGENT_PRODUCT_INDEX_MQ_ENABLED = "true"
$env:AGENT_PRODUCT_INDEX_RECONCILIATION_ENABLED = "true"
$env:AGENT_PERSONAL_TOOLS_ENABLED = "true"
$env:AGENT_PERSONAL_ACTIONS_ENABLED = "true"
```

### 初始化向量库

首次启动时设置：

```powershell
$env:AGENT_PGVECTOR_INITIALIZE_SCHEMA = "true"
$env:AGENT_PGVECTOR_SCHEMA_VALIDATION = "false"
```

确认表结构创建成功后，后续启动改为：

```powershell
$env:AGENT_PGVECTOR_INITIALIZE_SCHEMA = "false"
$env:AGENT_PGVECTOR_SCHEMA_VALIDATION = "true"
```

启动主类：

```text
GuideAgentApplication8520
```

健康检查地址：

```text
http://127.0.0.1:8520/actuator/health
```

### 构建商品向量索引

首次启用向量检索，或需要重新生成全部商品索引时，调用内部接口：

```http
POST http://127.0.0.1:8520/api/agent/internal/index/products/rebuild
X-Internal-Token: <与后端服务相同的内部 Token>
```

该接口属于内部管理接口，不应直接暴露到公网。

## MCP Server

MCP Server 是 Agent 的可选功能，默认不通过商城网关暴露。

配置环境变量：

```powershell
$env:AGENT_MCP_ENABLED = "true"
$env:AGENT_MCP_API_KEY = "<at-least-32-random-characters>"
```

本地端点：

```text
http://127.0.0.1:8520/mcp
```

执行冒烟测试：

```powershell
.\scripts\mcp\mcp-smoke.ps1
```

执行负向测试：

```powershell
.\scripts\mcp\mcp-negativeTest.ps1 -Suite All
```

## 可观测性

配置 Grafana 管理员密码：

```powershell
$env:GRAFANA_ADMIN_PASSWORD = "<grafana-password>"
```

启动可观测性组件：

```powershell
.\scripts\docker\deploy-observability.ps1 -Pull
```

访问地址：

| 组件         | 地址                      |
| ---------- | ----------------------- |
| Grafana    | `http://127.0.0.1:3000` |
| Prometheus | `http://127.0.0.1:9090` |
| Tempo      | `http://127.0.0.1:3200` |

## 支付配置

支付服务源码不包含支付宝密钥。

启用支付宝支付前，需要配置：

```powershell
$env:ALIPAY_APP_ID = "<alipay-app-id>"
$env:ALIPAY_APP_PRIVATE_KEY = "<alipay-private-key>"
$env:ALIPAY_PUBLIC_KEY = "<alipay-public-key>"
$env:ALIPAY_RETURN_URL = "<frontend-payment-result-url>"
$env:ALIPAY_NOTIFY_URL = "<public-alipay-callback-url>"
```

`ALIPAY_NOTIFY_URL` 必须是支付宝服务器能够访问的公网 HTTPS 地址。

支付回调需要校验签名，并结合业务订单状态进行幂等处理。不能仅根据前端跳转结果修改订单支付状态。

## 短信配置

短信验证码服务不包含 AppCode、签名或模板标识。

启用短信功能前，需要配置：

```powershell
$env:SMS_APP_CODE = "<sms-app-code>"
$env:SMS_SIGN_ID = "<sms-sign-id>"
$env:SMS_TEMPLATE_ID = "<sms-template-id>"
```

## 部署与安全注意事项

* 不要提交 `.env`、IDE 运行配置、JMeter 用户 CSV、运行日志或数据库备份。
* 不要将 `ZJZX_INTERNAL_API_TOKEN`、`AGENT_MCP_API_KEY` 或用户 Token 写入前端代码。
* 不要将支付密钥、短信凭据、数据库密码或模型 API Key 写入仓库。
* MinIO Bucket 最多开放匿名读取对象权限，禁止匿名上传、删除和列举。
* 生产环境只对外开放网关、管理端入口以及必要的回调地址。
* MySQL、Redis、Nacos、RabbitMQ、MinIO、PGVector 和 Agent 内部端口不应直接暴露到公网。
* 对外发布仓库前，应轮换所有曾经写入 Git 的凭据。
* 删除当前文件中的密钥不会清除 Git 历史。
* 如仓库中曾经提交过真实凭据，建议从脱敏后的工作树创建新的公开仓库。
* 需要保留原仓库历史时，应先完成备份，再使用专用工具清理历史记录，并重新轮换全部凭据。

更完整的部署边界说明见：

```text
docs/deployment-security.md
```

## 相关文档

| 文档                                                                             | 说明               |
| ------------------------------------------------------------------------------ | ---------------- |
| `docs/frontend-api.md`                                                         | 商城前端接口           |
| `docs/manager-api.md`                                                          | 后台管理接口           |
| `docs/rabbitmq-v1.md`                                                          | RabbitMQ 最终一致性设计 |
| `docs/seckill-test-guide.md`                                                   | 秒杀测试说明           |
| `docs/agent-iteration-0.md` 至 `docs/agent-iteration-7-3-order-cancellation.md` | Agent 迭代记录       |
| `docs/jmeter-performance-test.md`                                              | JMeter 压测方案      |
| `docs/deployment-security.md`                                                  | 部署安全边界           |
