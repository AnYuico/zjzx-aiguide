# JMeter 测试用户批量生成

> 仅用于本机或隔离测试环境。不得在生产环境启用测试数据接口。

## 1. 安全边界

批量账号接口：

```http
POST http://127.0.0.1:8512/api/user/internal/test-data/users/batch
X-Internal-Token: <内部服务 Token>
X-Test-Data-Key: <独立测试数据密钥>
Content-Type: application/json
```

该接口同时受以下限制：

- 只有 `test-data` Spring Profile 激活时才注册 Bean。
- `ZJZX_TEST_DATA_ENABLED` 必须显式设置为 `true`。
- 同时校验内部服务 Token 和独立测试数据密钥。
- URL 包含 `/internal/`，Gateway 不提供该路由并会拒绝外部请求。
- `service-user` 默认只监听 `127.0.0.1`。
- 后端单批最多 100 个账号；生成脚本会将更大的总数拆成多个批次。
- 只允许重置带相同 `load-test:{tag}` 标记的测试账号。
- 响应不返回密码、密码 Hash 或 Token。

接口只创建或重置测试账号。Token 必须通过正式登录接口签发，地址必须通过
正式地址查询或新增接口获得。

## 2. 启用测试接口

仅为 `service-user` 增加以下环境变量并重启：

```text
SPRING_PROFILES_ACTIVE=dev,test-data
ZJZX_TEST_DATA_ENABLED=true
ZJZX_TEST_DATA_API_KEY=<生成一个仅本机使用的随机密钥>
ZJZX_INTERNAL_API_TOKEN=<当前内部服务 Token>
```

不要把密钥写入 YML、脚本参数或 Git。

## 3. 批量请求契约

```json
{
  "count": 20,
  "phonePrefix": "199",
  "sequenceStart": 10000000,
  "defaultPassword": "LoadTest@123456",
  "nickNamePrefix": "压测用户",
  "tag": "jmeter-load-test"
}
```

生成的用户名为手机号。例如上述请求会生成
`19910000000` 至 `19910000019`。

重复执行相同批次时，接口只会重置带相同测试标记的账号密码和状态；
如果手机号已经属于普通用户，整批回滚并返回业务码 `247`。

## 4. 生成 JMeter CSV

仓库提供 PowerShell 脚本：

```powershell
.\scripts\jmeter\tools\New-ZjzxTestUsers.ps1 `
  -Count 1000 `
  -BatchSize 100 `
  -SkuId 15 `
  -ActivityId 8
```

脚本未取得密钥或测试密码时会使用隐藏输入。执行过程为：

```text
内部接口幂等创建测试账号
→ 正式登录接口签发 Token
→ 当前用户接口验证 Token
→ 正式地址接口查询或新增地址
→ 原子写入本地 CSV
```

输出：

```text
scripts/jmeter/data/users.csv
scripts/jmeter/data/test-accounts.csv
```

两个文件均被 `.gitignore` 排除。脚本不会在终端打印 Token 或密码。

## 5. 测试后关闭

生成数据后立即恢复：

```text
SPRING_PROFILES_ACTIVE=dev
ZJZX_TEST_DATA_ENABLED=false
```

随后重启 `service-user`。关闭后测试接口 Bean 不存在，即使持有两项密钥也不能调用。
