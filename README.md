# 紫金甄选商城

紫金甄选商城是一个基于 Java 17 和 Spring Cloud 的前后端分离微服务商城，包含商品、用户、购物车、订单、支付、秒杀、后台管理和智能导购 Agent。

商城服务使用 Spring Boot 3.0.5，智能导购 Agent 使用独立的 Spring Boot 3.5.15 与 Spring AI 1.1.8，二者通过 HTTP、RabbitMQ 和脱敏 DTO 交互。

## 1. 核心能力

- Spring Cloud Gateway、Nacos 和 OpenFeign 微服务治理
- MySQL 条件扣库存、库存预占和订单幂等
- RabbitMQ、Outbox、消费日志和补偿任务实现最终一致性
- Redis Hash 购物车与 Lua 原子清理
- Redis Lua、RabbitMQ 和 MySQL 唯一约束实现秒杀
- DeepSeek Tool Calling、Ollama Embedding 和 PGVector 混合检索
- 只读 MCP Server、Agent 写操作确认和幂等执行
- OpenTelemetry、Prometheus、Tempo 和 Grafana 可观测性

## 2. 模块与端口

| 模块 | 端口 | 说明 |
| --- | ---: | --- |
| `zjzx-server-gateway` | 8500 | 商城统一 API 网关 |
| `zjzx-manager` | 8501 | 后台管理接口 |
| `service-product` | 8511 | 商品、库存与秒杀活动 |
| `service-user` | 8512 | 用户、地址和地区数据 |
| `service-cart` | 8513 | Redis 购物车 |
| `service-order` | 8514 | 订单、超时关单与秒杀订单 |
| `service-pay` | 8515 | 支付回调与支付事件 |
| `zjzx-agent-service` | 8520 | 智能导购、RAG、MCP 与个人工具 |

业务服务默认绑定 `127.0.0.1`。请只对外开放网关端口；`8511-8515` 和 Agent 内部接口不应直接暴露到公网。

## 3. 环境要求

- JDK 17
- Maven 3.8+
- Docker Desktop 或等价的容器运行时
- MySQL 8.0.30
- Redis 7.0.10
- Nacos 2.2.2
- RabbitMQ 4.3.2 Management
- MinIO，只有文件上传功能需要
- PostgreSQL 16 + PGVector 0.8.2，只有 Agent 向量检索需要
- Ollama 0.32.3 + `bge-m3`，只有 Agent 向量检索需要

JMeter 5.6.3、Prometheus、Tempo 和 Grafana 仅用于性能测试和可观测性，不是商城最小启动依赖。

## 4. 配置环境变量

仓库不包含真实密码、API Key、支付密钥或服务器地址。以下示例适用于 PowerShell，请将尖括号内容替换为自己的本地配置。

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

# 服务间认证，所有后端服务必须使用同一个高强度随机值
$env:ZJZX_INTERNAL_API_TOKEN = "<at-least-32-random-characters>"

# MinIO
$env:MINIO_ENDPOINT = "http://127.0.0.1:9000"
$env:MINIO_ACCESS_KEY = "<minio-user>"
$env:MINIO_SECRET_KEY = "<minio-password>"
$env:MINIO_BUCKET = "zjzx-bucket"
$env:DEFAULT_AVATAR_URL = "http://127.0.0.1:9000/zjzx-bucket/defaultIcon.png"
```

环境变量只对当前 PowerShell 窗口有效。使用 IDEA 启动时，应把同一组变量加入每个 Spring Boot 运行配置，或者写入不提交到 Git 的本地配置文件。

## 5. 启动基础设施

先创建项目网络：

```powershell
docker network create zjzx-net
```

### 5.1 MySQL

```powershell
docker run -d --name zjzx-mysql --network zjzx-net `
  -p 127.0.0.1:3306:3306 `
  -e MYSQL_ROOT_PASSWORD=$env:MYSQL_PASSWORD `
  -e MYSQL_DATABASE=$env:MYSQL_DATABASE `
  -e TZ=Asia/Shanghai `
  -v zjzx-mysql-data:/var/lib/mysql `
  --restart unless-stopped mysql:8.0.30
```

### 5.2 Redis

```powershell
docker run -d --name zjzx-redis --network zjzx-net `
  -p 127.0.0.1:6379:6379 `
  -v zjzx-redis-data:/data `
  --restart unless-stopped redis:7.0.10 `
  redis-server --appendonly yes --requirepass $env:REDIS_PASSWORD
```

### 5.3 Nacos

```powershell
docker run -d --name zjzx-nacos --network zjzx-net `
  -p 127.0.0.1:8848:8848 `
  -p 127.0.0.1:9848:9848 `
  -e MODE=standalone `
  --restart unless-stopped nacos/nacos-server:v2.2.2
```

项目的 Nacos 配置导入是可选的，本地数据库、Redis 和 RabbitMQ 配置直接从环境变量读取。Nacos 主要用于服务注册与发现。

### 5.4 RabbitMQ

先拉取镜像，再运行仓库脚本。脚本会读取环境变量，缺失时安全提示输入账号和密码。

```powershell
docker pull rabbitmq:4.3.2-management
.\scripts\docker\deploy-rabbitmq.ps1
```

管理页面为 `http://127.0.0.1:15672`。

### 5.5 MinIO（可选）

后台上传头像和商品图片时才需要 MinIO。请创建 `zjzx-bucket`，并仅为图片对象配置公开读取权限，不要公开写入、删除或列举权限。

```powershell
docker run -d --name zjzx-minio --network zjzx-net `
  -p 127.0.0.1:9000:9000 `
  -p 127.0.0.1:9001:9001 `
  -e MINIO_ROOT_USER=$env:MINIO_ACCESS_KEY `
  -e MINIO_ROOT_PASSWORD=$env:MINIO_SECRET_KEY `
  -v zjzx-minio-data:/data `
  --restart unless-stopped minio/minio server /data --console-address ":9001"
```

## 6. 初始化数据库

仓库中的 `docs/sql` 主要是项目升级过程的增量脚本，不包含原始演示商品、地区和后台菜单数据。首次运行前需要先准备基础 `db_zjzx` 结构和数据，再按实际数据库状态执行增量脚本。

全新升级环境推荐检查以下脚本：

1. `docs/sql/20260721_bcrypt_password.sql`
2. `docs/sql/20260711_inventory_reservation.sql`
3. `docs/sql/20260722_rabbitmq_outbox.sql`
4. `docs/sql/20260723_cart_async_cleanup.sql`
5. `docs/sql/20260723_seckill_v1.sql`
6. `docs/sql/20260722_schema_integrity_check.sql`

`20260722_*_repair.sql` 用于修复已经部分升级的数据库，不应在不了解现有结构时全部重复执行。完整性检查结果中的 `FAIL` 和 `SKIPPED` 必须处理后再启动订单与秒杀服务。

## 7. 编译项目

在仓库根目录执行：

```powershell
mvn -DskipTests package
```

执行全部单元测试：

```powershell
mvn test
```

## 8. 启动商城服务

推荐使用 IDEA 分别启动以下主类：

1. `ProductApplication8511`
2. `UserApplication8512`
3. `CartApplication8513`
4. `OrderApplication8514`
5. `PayApplication8515`
6. `GatewayApplication8500`
7. `ManagerApplication8501`

也可以在编译后使用对应模块的 Spring Boot Jar 启动。所有进程必须继承第 4 节配置的环境变量。

启动后检查：

```text
http://127.0.0.1:8511/actuator/health
http://127.0.0.1:8512/actuator/health
http://127.0.0.1:8513/actuator/health
http://127.0.0.1:8514/actuator/health
http://127.0.0.1:8515/actuator/health
http://127.0.0.1:8500/actuator/health
```

前端商城 API BaseURL 指向 `http://127.0.0.1:8500`，后台管理前端指向 `http://127.0.0.1:8501`。

## 9. 启动智能导购 Agent（可选）

### 9.1 部署 PGVector 和 Ollama

```powershell
$env:AGENT_PGVECTOR_USERNAME = "zjzx_agent"
$env:AGENT_PGVECTOR_PASSWORD = "<postgres-password>"

docker pull pgvector/pgvector:0.8.2-pg16-bookworm
.\scripts\docker\deploy-pgvector.ps1

docker pull ollama/ollama:0.32.3
.\scripts\docker\deploy-ollama-embedding.ps1
```

对 `zjzx_agent` PostgreSQL 数据库执行：

1. `docs/sql/20260728_agent_product_index_mq.sql`
2. `docs/sql/20260729_agent_action_request.sql`
3. `docs/sql/20260729_agent_action_cancel_order.sql`

### 9.2 配置 Agent

```powershell
$env:AGENT_PGVECTOR_URL = "jdbc:postgresql://127.0.0.1:5432/zjzx_agent"
$env:SPRING_AI_MODEL_EMBEDDING = "ollama"
$env:SPRING_AI_VECTORSTORE_TYPE = "pgvector"
$env:AGENT_VECTOR_ENABLED = "true"
$env:OLLAMA_BASE_URL = "http://127.0.0.1:11434"
$env:OLLAMA_EMBEDDING_MODEL = "bge-m3"

# DeepSeek 对话，可不配置；不配置时 Agent 使用确定性检索模式
$env:SPRING_AI_MODEL_CHAT = "openai"
$env:DEEPSEEK_API_KEY = "<your-deepseek-api-key>"
$env:DEEPSEEK_BASE_URL = "https://api.deepseek.com"
$env:DEEPSEEK_MODEL = "deepseek-v4-flash"

# 增量索引和个人工具
$env:AGENT_PRODUCT_INDEX_MQ_ENABLED = "true"
$env:AGENT_PRODUCT_INDEX_RECONCILIATION_ENABLED = "true"
$env:AGENT_PERSONAL_TOOLS_ENABLED = "true"
$env:AGENT_PERSONAL_ACTIONS_ENABLED = "true"
```

首次启动向量库时设置：

```powershell
$env:AGENT_PGVECTOR_INITIALIZE_SCHEMA = "true"
$env:AGENT_PGVECTOR_SCHEMA_VALIDATION = "false"
```

表创建成功后，后续启动改为：

```powershell
$env:AGENT_PGVECTOR_INITIALIZE_SCHEMA = "false"
$env:AGENT_PGVECTOR_SCHEMA_VALIDATION = "true"
```

启动 `GuideAgentApplication8520`，然后检查 `http://127.0.0.1:8520/actuator/health`。

全量构建商品向量索引：

```http
POST http://127.0.0.1:8520/api/agent/internal/index/products/rebuild
X-Internal-Token: <与后端服务一致的内部 Token>
```

### 9.3 MCP Server（可选）

```powershell
$env:AGENT_MCP_ENABLED = "true"
$env:AGENT_MCP_API_KEY = "<at-least-32-random-characters>"
```

MCP 端点为 `http://127.0.0.1:8520/mcp`，不会通过网关暴露。验证脚本：

```powershell
.\scripts\mcp\mcp-smoke.ps1
.\scripts\mcp\mcp-negativeTest.ps1 -Suite All
```

## 10. 可观测性（可选）

```powershell
$env:GRAFANA_ADMIN_PASSWORD = "<grafana-password>"
.\scripts\docker\deploy-observability.ps1 -Pull
```

- Grafana：`http://127.0.0.1:3000`
- Prometheus：`http://127.0.0.1:9090`
- Tempo：`http://127.0.0.1:3200`

## 11. 支付与短信配置（可选）

支付服务源码不再包含支付宝密钥。启用支付前配置：

```powershell
$env:ALIPAY_APP_ID = "<alipay-app-id>"
$env:ALIPAY_APP_PRIVATE_KEY = "<alipay-private-key>"
$env:ALIPAY_PUBLIC_KEY = "<alipay-public-key>"
$env:ALIPAY_RETURN_URL = "<frontend-payment-result-url>"
$env:ALIPAY_NOTIFY_URL = "<public-alipay-callback-url>"
```

短信验证码服务不再包含 AppCode、签名或模板标识。启用短信前配置：

```powershell
$env:SMS_APP_CODE = "<sms-app-code>"
$env:SMS_SIGN_ID = "<sms-sign-id>"
$env:SMS_TEMPLATE_ID = "<sms-template-id>"
```

## 12. 安全说明

- 不要提交 `.env`、IDE 运行配置、JMeter 用户 CSV、日志或数据库备份。
- 不要把 `ZJZX_INTERNAL_API_TOKEN`、`AGENT_MCP_API_KEY` 或用户 Token 放入前端。
- MinIO Bucket 最多开放匿名读对象权限，禁止匿名上传和删除。
- 公开仓库前必须轮换曾经写入 Git 的支付宝、短信、数据库、Redis、MinIO 和模型服务凭据。
- 仅删除当前文件中的秘密不会清除 Git 历史。建议从脱敏后的工作树创建全新的公开仓库，或在确认备份后使用专用工具清理历史，再将仓库改为 Public。

## 13. 相关文档

- 商城前端接口：`docs/frontend-api.md`
- 管理端接口：`docs/manager-api.md`
- RabbitMQ 一致性设计：`docs/rabbitmq-v1.md`
- 秒杀测试：`docs/seckill-test-guide.md`
- Agent 迭代文档：`docs/agent-iteration-0.md` 至 `docs/agent-iteration-7-3-order-cancellation.md`
- JMeter 压测方案：`docs/jmeter-performance-test.md`
- 部署安全边界：`docs/deployment-security.md`
