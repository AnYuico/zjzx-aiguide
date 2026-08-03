# Agent Iteration 5: Observability, Resilience, Rate Limiting, and Security Evaluation

## Scope

Iteration 5 adds operational safeguards around the shopping guide Agent:

- OpenTelemetry traces exported through an OTel Collector to Tempo.
- Spring Boot Actuator and Micrometer metrics scraped by Prometheus.
- A provisioned Grafana dashboard for Agent, Gateway, resilience, and security metrics.
- Redis-backed Gateway rate limiting by user, client IP, and session.
- Resilience4j retry, circuit breaker, and concurrent-call isolation.
- Runtime output safety checks plus deterministic and optional live security evaluations.

The iteration does not grant the model any write tool. The only model-visible
tool remains the read-only product search tool.

## Pinned local components

| Component | Image |
| --- | --- |
| OpenTelemetry Collector | `otel/opentelemetry-collector-contrib:0.157.0` |
| Tempo | `grafana/tempo:2.10.5` |
| Prometheus | `prom/prometheus:v3.13.0` |
| Grafana | `grafana/grafana:13.1.0` |

The stack joins the existing external Docker network `zjzx-net`.

## Deployment

Run from the repository root:

```powershell
.\scripts\docker\deploy-observability.ps1 -Pull
```

The script prompts for the Grafana administrator password instead of storing
it in source control. After deployment:

- Grafana: `http://127.0.0.1:3000`
- Prometheus: `http://127.0.0.1:9090`
- Tempo: `http://127.0.0.1:3200`
- OTel Collector health: `http://127.0.0.1:13133`
- OTLP HTTP traces: `http://127.0.0.1:4318/v1/traces`

The Grafana instance is provisioned with Prometheus and Tempo data sources and
the `ZJZX Agent Overview` dashboard.

## Application configuration

Agent and Gateway default to the local collector:

```text
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://127.0.0.1:4318/v1/traces
OTEL_TRACES_SAMPLER_PROBABILITY=1.0
```

The sampling probability should be reduced in a sustained production
environment. Prompts, model answers, tokens, addresses, and other user data
are intentionally excluded from metric tags and evaluation reports.

Actuator endpoints:

- Agent: `http://127.0.0.1:8520/actuator/prometheus`
- Gateway: `http://127.0.0.1:8500/actuator/prometheus`

## Rate limiting

The Gateway applies one atomic Redis Lua decision to three independent
dimensions:

| Dimension | Default limit per minute | Environment variable |
| --- | ---: | --- |
| Authenticated user | 10 | `AGENT_USER_RATE_LIMIT` |
| Client IP | 30 | `AGENT_IP_RATE_LIMIT` |
| Agent session | 15 | `AGENT_SESSION_RATE_LIMIT` |

Session keys use a truncated SHA-256 digest. Raw session tokens are never
written to Redis. A rejected request returns HTTP `429`, business code `242`,
and `Retry-After: 60`.

## Resilience policy

Product catalogue calls use a short two-attempt retry around transport and
timeout failures and a circuit breaker outside the retry. Each logical request
therefore contributes one final outcome to the circuit breaker.

DeepSeek calls use:

- a 3-second HTTP connection timeout and 25-second read timeout;
- a 60-second full tool-calling turn timeout;
- a circuit breaker for consecutive provider failures;
- a semaphore bulkhead, defaulting to four concurrent model calls;
- deterministic local fallback when the provider is unavailable or unsafe.

Bulkhead rejection is intentionally outside the model circuit breaker, so
local saturation cannot falsely mark DeepSeek as unhealthy.

Relevant environment variable:

```text
AGENT_MODEL_MAX_CONCURRENT_CALLS=4
```

The database health contributor follows `AGENT_VECTOR_ENABLED`; the RabbitMQ
health contributor follows `AGENT_PRODUCT_INDEX_MQ_ENABLED`. Disabled optional
features therefore cannot incorrectly make the Agent health endpoint report
`DOWN`.

## Metrics and traces

Key custom metrics:

- `zjzx_agent_chat_requests_total{outcome=...}`
- `zjzx_agent_chat_duration_seconds{outcome=...}`
- `zjzx_agent_security_output_rejections_total`
- `zjzx_gateway_agent_rate_limit_rejections_total{reason=...}`

Resilience4j and Spring HTTP metrics are exported by their Micrometer
integrations. Agent responses include `X-Trace-Id` when a sampled trace is
active, allowing a request to be located in Tempo without logging its prompt.

## Security evaluation

Offline deterministic regression:

```powershell
mvn -pl zjzx-agent-service -am test
```

The test dataset is:

```text
zjzx-agent-service/src/test/resources/evaluation/agent-security-eval.jsonl
```

It covers direct and indirect prompt injection, arbitrary order access,
attempted order, inventory and payment writes, PII and secret disclosure,
control characters, and a benign recommendation.

Optional live evaluation against a running Agent:

```powershell
.\scripts\evaluation\run-agent-security-eval.ps1
```

The live report records case ID, category, pass status, failure reason,
response mode, and duration. It does not persist prompts or complete model
answers.

## Acceptance checklist

1. Agent and Gateway health endpoints report `UP`.
2. Prometheus shows both application targets as `UP`.
3. A chat response contains `X-Trace-Id` and its trace is searchable in Tempo.
4. Grafana dashboard panels receive HTTP, chat, resilience, and rate-limit metrics.
5. The fourth concurrent model call behavior follows the configured bulkhead limit.
6. Repeated requests exceed each configured rate dimension and receive `429`.
7. Opening a circuit produces deterministic fallback instead of an API `500`.
8. Offline security evaluation passes all cases.
9. Live evaluation stores no prompt, answer, token, address, or phone number.

## Frontend contract

Chat request and success response schemas are unchanged. Frontends only need
to handle HTTP `429` by honoring `Retry-After`; `X-Trace-Id` is diagnostic and
does not need to be persisted.
