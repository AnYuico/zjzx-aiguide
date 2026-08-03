# Shopping guide agent iteration 0

## Decision

The commerce system and shopping guide agent use separate Spring Boot runtimes in the same repository:

| Runtime | Spring Boot | Discovery | Port |
| --- | --- | --- | --- |
| Commerce services | 3.0.5 | Nacos 2.2.2 | Existing ports |
| Shopping guide agent | 3.5.15 | Explicit private URL | 8520 |

The agent imports the Spring AI 1.1.8 BOM but does not enable a model provider during iteration 0.

## Module boundary

`zjzx-ai-contract` is a pure Java module. It contains only allowlisted DTO and VO types shared by the product guide endpoint and the agent.

`zjzx-agent-service` has its own Spring Boot parent and depends only on `zjzx-ai-contract`. It must not depend on `zjzx-model`, commerce services, commerce mappers, or general-purpose Feign clients.

The Maven boundary test verifies that `ProductSku`, `OrderInfo`, and `UserAddress` are absent from the agent classpath.

## Local routing

The gateway route is:

```text
/api/agent/** -> ${ZJZX_AGENT_SERVICE_URL:http://127.0.0.1:8520}
```

The iteration 0 health endpoint is:

```text
GET /api/agent/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "zjzx-agent-service"
}
```

## Build commands

Commerce product guide boundary:

```text
mvn -pl zjzx-service/service-product -am test
```

Isolated agent:

```text
mvn -pl zjzx-agent-service -am test
```

## Next iteration

Iteration 1 adds a WebClient-based product guide adapter, input validation, a deterministic keyword-search response, and model-independent fallback behavior. No order, user, address, cart, payment, or inventory capability will be introduced.

Iteration 1 is now implemented. See `docs/agent-iteration-1.md`.
