# Shopping guide agent iteration 1

## Scope

Iteration 1 provides a usable, model-independent shopping guide query path:

```text
Frontend
  -> Gateway /api/agent/**
  -> zjzx-agent-service
  -> ProductGuideHttpClient
  -> service-product /api/product/internal/ai-guide/**
  -> ProductGuideVo
```

The agent still has no model provider, chat memory, order tool, user tool, cart tool, or write capability.

## Public API

```text
POST /api/agent/guide/search
Content-Type: application/json
```

Request:

```json
{
  "keyword": "Mac",
  "limit": 5
}
```

Rules:

- `keyword` is optional and has a maximum length of 50 characters.
- `limit` is optional, defaults to 10, and must be between 1 and 20.

Successful response:

```json
{
  "keyword": "Mac",
  "message": "已找到 1 个相关商品。",
  "count": 1,
  "products": [
    {
      "skuId": 14,
      "productName": "Mac mini",
      "skuName": "16G",
      "thumbImg": "http://example/image.png",
      "salePrice": 1999.00,
      "marketPrice": 4999.00,
      "skuSpec": "16GB",
      "unitName": "台",
      "inStock": true
    }
  ]
}
```

The response does not contain cost price, exact stock, sales volume, persistence metadata, user information, orders, or addresses.

When the product service is unavailable, times out, rejects the internal credential, or the credential is missing:

```text
HTTP 503
```

```json
{
  "code": "PRODUCT_CATALOG_UNAVAILABLE",
  "message": "商品目录暂时不可用，请稍后重试。"
}
```

The agent does not invent products during degradation.

## Runtime configuration

```text
PRODUCT_GUIDE_BASE_URL=http://127.0.0.1:8511
ZJZX_INTERNAL_API_TOKEN=<same value used by commerce services>
PRODUCT_GUIDE_REQUEST_TIMEOUT=2s
ZJZX_AGENT_SERVICE_URL=http://127.0.0.1:8520
```

`ZJZX_INTERNAL_API_TOKEN` has no public default. It is injected by the agent HTTP adapter and is never accepted from a frontend request or model argument.

## Verification

The tests cover:

- Spring Boot 3.5 runtime isolation.
- Absence of commerce entities from the agent classpath.
- Internal Token injection by the HTTP adapter.
- Deserialization into the allowlisted `ProductGuideVo`.
- Request validation and deterministic response text.
- Empty search results without fabricated products.
- Stable HTTP 503 degradation.

## Next iteration

Iteration 2 introduces the Spring AI chat model behind a feature flag. The deterministic catalog query remains the fallback. Only the read-only product guide function may be registered as a tool; model provider selection and API credentials must be decided before enabling it.

Iteration 2 is now implemented with DeepSeek V4. See `docs/agent-iteration-2-deepseek.md`.
