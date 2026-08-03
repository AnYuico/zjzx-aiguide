# Agent Iteration 6: Read-only MCP Server

## Scope

Iteration 6 exposes the existing product guide capabilities through a
Model Context Protocol server.

- Runtime: `zjzx-agent-service`, port `8520`
- Spring Boot: `3.5.15`
- Spring AI: `1.1.8`
- Transport: Stateless Streamable HTTP
- Endpoint: `POST /mcp`
- Authentication: `X-MCP-API-Key`
- Capability: tools only

The existing DeepSeek chat path still invokes its in-process read-only tool.
It does not call the local MCP endpoint, so this iteration does not add a
network hop to the normal shopping guide request.

## Enable the server

Configure these environment variables for `GuideAgentApplication8520`:

```text
AGENT_MCP_ENABLED=true
AGENT_MCP_API_KEY=<a-random-secret-with-at-least-32-characters>
```

Optional variables:

```text
AGENT_MCP_ENDPOINT=/mcp
AGENT_MCP_REQUEST_TIMEOUT=10s
```

The application fails fast when MCP is enabled without
`AGENT_MCP_API_KEY`. MCP is disabled by default.

The endpoint is intentionally not added to Gateway routes. Local clients use:

```text
http://127.0.0.1:8520/mcp
```

Every request must include:

```text
X-MCP-API-Key: <AGENT_MCP_API_KEY>
Content-Type: application/json
Accept: application/json, text/event-stream
```

## Published tools

### `searchProducts`

Searches the live MySQL-backed product guide endpoint.

Arguments:

```json
{
  "keyword": "Mac",
  "limit": 5
}
```

`keyword` is optional. `limit` must be between 1 and 20.

### `getProductSnapshot`

Reads the current public snapshot of one SKU.

Arguments:

```json
{
  "skuId": 14
}
```

### `retrieveProductKnowledge`

Runs the existing hybrid retrieval path. PGVector provides semantic
candidates and Product Service validates every returned SKU against the live
catalog before the tool returns it.

Arguments:

```json
{
  "query": "small desktop computer",
  "limit": 5
}
```

## Output boundary

All tools return only `ProductGuideVo` fields:

```text
skuId
productName
skuName
thumbImg
salePrice
marketPrice
skuSpec
unitName
inStock
```

The MCP server does not return or accept:

```text
costPrice
stockNum
userId
orderNo
addressId
paymentId
token
```

It has no order, cart, payment, address, inventory mutation, resource, prompt,
completion, sampling or elicitation capability.

Tool arguments use an exact whitelist. Unknown fields are rejected as tool
errors instead of being silently ignored. Calls to non-tool capabilities
return HTTP `200` with JSON-RPC error code `-32601` (`Method not found`).

## Postman smoke test

List tools:

```json
{
  "jsonrpc": "2.0",
  "id": "tools-list",
  "method": "tools/list",
  "params": {}
}
```

Call product search:

```json
{
  "jsonrpc": "2.0",
  "id": "search-products",
  "method": "tools/call",
  "params": {
    "name": "searchProducts",
    "arguments": {
      "keyword": "Mac",
      "limit": 5
    }
  }
}
```

Expected checks:

1. A request without `X-MCP-API-Key` returns HTTP `401`.
2. `tools/list` returns exactly three tools.
3. `tools/call` returns `isError=false`.
4. Product data contains no cost price, raw stock count or user data.
5. A non-positive `skuId` or a `limit` outside 1 to 20 returns a tool error.

The same checks can be run with:

```powershell
.\scripts\mcp\mcp-smoke.ps1
.\scripts\mcp\mcp-negativeTest.ps1 -Suite All
```

Both scripts read the key from `AGENT_MCP_API_KEY` and prompt securely when
the environment variable is absent. The negative suite supports Windows
PowerShell 5.1 and requires Gateway `/mcp` to return HTTP `404`; a `401` or
`403` is not treated as proof that the route is absent.

## Security decisions

- The API key has no default value and is compared in constant time.
- MCP is disabled by default.
- Only three explicit async tool specifications are registered.
- Annotation auto-scanning and generic ToolCallback conversion are disabled.
- Adding a future `@Tool` bean cannot automatically publish it through MCP.
- Every published tool enforces an exact argument-name whitelist.
- Disabled MCP capabilities return a controlled JSON-RPC method error.
- The endpoint remains outside the public Gateway route.
- Catalog strings are untrusted data and are never treated as instructions.

Spring AI 1.1.8 documents Stateless Streamable HTTP through
`spring-ai-starter-mcp-server-webflux` and
`spring.ai.mcp.server.protocol=STATELESS`:

<https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-stateless-server-boot-starter-docs.html>

## Verification

Run the Agent test suite:

```powershell
mvn -pl zjzx-agent-service -am test
```

Protocol coverage includes:

- API key rejection
- exact tool discovery
- JSON-RPC tool invocation
- argument validation
- forbidden identity and transaction arguments
- MCP-disabled application startup
