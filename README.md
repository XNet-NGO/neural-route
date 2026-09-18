# neural-route

Headless, config-driven LLM router on the JVM, built on llm-core.

- Consumes the **top-100 provider config database** from
  `../llm-core/research/providers-100/` — every config there is a valid
  `ProviderConfig` JSON block that this router can load.
- Builds runtime providers via llm-core's `OpenAIProvider.from(config)` — all 8
  dialects (D1/D2/D3/D4/D5/D6/D7/D8), all auth schemes (Bearer/X-API-Key/Query/
  SIGV4/OAuth2), SSE/EVENTSTREAM/NDJSON streaming, and the D7 transforms engine
  (TTS/STT/async jobs) come from llm-core, not from this repo.
- Headless by design: no UI. The admin portal is a separate project (Vue 3 + Vite).

## Endpoints

| method | path | purpose |
|---|---|---|
| GET | `/health` | liveness + provider count |
| GET | `/api/v1/providers` | admin REST: provider inventory (id/dialect/baseUrl/capabilities) |
| POST | `/v1/{provider}/chat/completions` | OpenAI-compat D1 proxy through any dialect |
| POST | `/v1/{provider}/chat/completions` (stream) | SSE passthrough (follow-up) |

## Run

```bash
./gradlew build -PSONATYPE_USERNAME=ci -PSONATYPE_PASSWORD=ci   # build + test
./gradlew run    -PSONATYPE_USERNAME=ci -PSONATYPE_PASSWORD=ci   # dev server :8080
```

The `-P...` flags are local-dev placeholders: llm-core (the composite build) ships a
Maven Central publish plugin that validates credentials at configuration time.
Replace with real credentials in CI; see `gradle.properties`.

Environment:
- `NEURAL_ROUTE_PORT` — listen port (default `8080`)
- `NEURAL_ROUTE_CONFIG_DIR` — provider config dir (default
  `src/main/resources/provider-configs`)
- `NEURAL_ROUTE_API_KEY` — optional API-key gate: when set, all routes except
  `/health` require `X-API-Key: <key>` or `Authorization: Bearer <key>` (401 otherwise).
- Provider keys: `QWEN_KEY`, `GOOGLE_AI_STUDIO_KEY`, `CF_TOKEN`/`CF_ACCOUNT`,
  `ELEVENLABS_KEY` (referenced as `${VAR}` inside provider-configs/*.json).

## Adding a provider

1. Drop the `ProviderConfig` JSON from `llm-core/research/providers-100/*` into
   `src/main/resources/provider-configs/` (fill `auth.apiKey` via env/secrets at the
   host layer — never commit keys; empty key = unauthenticated).
2. Restart; `GET /api/v1/providers` lists it, `POST /v1/{id}/chat/completions` routes.

## Architecture

```
neural-route (this repo)            llm-core (composite includeBuild, ../llm-core)
┌─────────────────────┐             ┌──────────────────────────────┐
│ Ktor (Netty)        │  OpenAIGateway.from(config)                │
│  admin REST         ├────────────►│  dialects D1–D8              │
│  chat proxy         │             │  auth schemes + signers      │
│  ProviderRegistry   │             │  transforms (TTS/STT/jobs)   │
└─────────────────────┘             │  provider-100 config DB docs │
                                    └──────────────────────────────┘
```