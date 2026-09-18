# AGENTS.md — neural-route

neural-route is the headless gateway half of the two-repo architecture; **llm-core**
(`../llm-core`, composite includeBuild) owns every routing primitive.

## External contracts (read from llm-core, never redefine here)

- `ProviderConfig` schema: `llm-core/research/providers-100/00-index.md` §0 — the
  exact JSON shape (dialects, auth, endpoints, capabilities, catalog, transforms).
  Config blocks in `src/main/resources/provider-configs/` must stay copies of that
  truth.
- Provider semantics: `llm-core/HANDOFF.md` (two agents work in that repo — Kilo +
  kiro-cli; `[handoff]` commit prefix, paths-only partial commits). If a routing
  behavior is missing here, the fix belongs there, not in a route workaround.
- Top-100 config database: `llm-core/research/providers-100/01..05` + verification
  log `06-verification-log.md` (live-proven configs are marked; use those first).

## Conventions

- Never hardcode a provider; always go through `ProviderRegistry` + config JSON.
- API keys: read from env/host secrets at boot (`NEURAL_ROUTE_*`), never commit,
  never log. Empty `auth.apiKey` is a first-class state (local/unauthenticated).
- New admin endpoints: `@Serializable` DTOs only — Ktor's kotlinx converter rejects
  heterogeneous collections (see commit history for the pitfalls).
- Keep the seed configs (`ollama.json`, `freeinference.json`) as smoke fixtures.