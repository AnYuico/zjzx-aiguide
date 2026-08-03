# Shopping guide agent iteration 2: DeepSeek V4

## Runtime

The isolated agent uses:

- Spring Boot 3.5.15
- Spring AI 1.1.8
- DeepSeek OpenAI-compatible API
- Default model `deepseek-v4-flash`
- Non-thinking mode for lower latency and simpler tool-call compatibility

The API key is never stored in this repository. Configure it through the Agent process environment.

## Agent environment

Required to enable DeepSeek:

```text
SPRING_AI_MODEL_CHAT=openai
DEEPSEEK_API_KEY=<rotated DeepSeek API key>
ZJZX_INTERNAL_API_TOKEN=<same internal token used by service-product>
```

Optional:

```text
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_MAX_TOKENS=800
DEEPSEEK_RESPONSE_TIMEOUT=30s
DEEPSEEK_TOOL_TIMEOUT=5s
DEEPSEEK_FALLBACK_LIMIT=5
PRODUCT_GUIDE_BASE_URL=http://127.0.0.1:8511
```

When `SPRING_AI_MODEL_CHAT` is absent or set to `none`, the Agent starts without an AI key and uses deterministic catalog search.

Embedding, image, moderation, speech and transcription models are explicitly disabled. Prompt and completion logging are also disabled.

## Public API

The paid model endpoint is protected by the existing gateway authentication filter:

```text
POST /api/agent/auth/guide/chat
token: <mall user login token>
Content-Type: application/json
```

Request:

```json
{
  "message": "预算 3000 元，推荐一台适合办公的电脑",
  "limit": 5
}
```

AI response:

```json
{
  "answer": "根据当前商品信息，建议优先考虑……",
  "mode": "AI",
  "model": "deepseek-v4-flash",
  "products": []
}
```

`products` contains only products actually returned by the read-only catalog tool.

When DeepSeek is disabled, unavailable, times out or returns an invalid response, the request falls back to the existing catalog query:

```json
{
  "answer": "智能模型暂时不可用，已按你的问题查询商品。……",
  "mode": "DETERMINISTIC_FALLBACK",
  "model": null,
  "products": []
}
```

If both DeepSeek and the product catalog are unavailable, the existing stable `503 PRODUCT_CATALOG_UNAVAILABLE` response is returned.

## Tool boundary

DeepSeek receives one tool only:

```text
searchProducts(keyword, limit)
```

The tool:

- Calls the internal product guide endpoint with a server-injected `X-Internal-Token`.
- Caps `limit` by the request-level maximum.
- Returns only `ProductGuideVo`.
- Cannot accept `userId`, `orderNo`, address, payment, cart or inventory operation arguments.
- Cannot place orders, modify carts, change prices or reserve stock.

Catalog fields are declared as untrusted data in the system prompt and must not be followed as instructions.

## Cost controls

The gateway applies Redis Lua fixed-window limits to the chat endpoint:

```text
AGENT_USER_RATE_LIMIT=10
AGENT_IP_RATE_LIMIT=30
AGENT_SESSION_RATE_LIMIT=15
```

The defaults are requests per minute. The framework retry count is reduced
from 10 to 1, so one user request produces at most one paid model request.
HTTP 4xx responses are not retried.

DeepSeek HTTP calls use a 3-second connection timeout and a 25-second
per-request read timeout. The full tool-calling turn has a 60-second outer
timeout. Framework retries are disabled (`max-attempts=1`) so a slow provider
cannot consume the complete outer timeout with a duplicate paid request.

## Current exclusions

This iteration intentionally has no:

- Chat memory or conversation persistence
- Vector database or RAG
- MCP client/server
- Order, cart, address, payment or inventory tools
- Streaming frontend protocol

Those capabilities require separate data-retention, authorization and observability decisions.
