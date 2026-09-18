package com.neuralroute

import com.tddworks.openai.gateway.api.OpenAIProvider
import com.tddworks.openai.gateway.api.internal.from
import com.tddworks.openai.gateway.config.ProviderConfig
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads provider configs from the config directory (default
 * src/main/resources/provider-configs). Every file is a ProviderConfig JSON block as
 * pinned in llm-core's research/providers-100/00-index.md — the same blocks the
 * portal and the test suites use.
 *
 * Secrets: never commit keys. Config files may reference environment variables with
 * ${NAME} placeholders in [ProviderConfig.baseUrl] and [ProviderConfig.auth.apiKey];
 * they are resolved at load time (e.g. "${GOOGLE_AI_STUDIO_KEY}", "${CF_ACCOUNT}").
 */
class ProviderRegistry(private val configDir: Path) {

    fun load(): List<ProviderConfig> {
        if (!Files.isDirectory(configDir)) return emptyList()
        return Files.list(configDir).use { stream ->
            stream
                .filter { it.toString().endsWith(".json") }
                .sorted()
                .map { path -> resolveEnv(ProviderConfig.fromJson(Files.readString(path))) }
                .toList()
        }
    }

    /** Builds the runtime provider (any dialect) for the given config. */
    fun build(cfg: ProviderConfig): OpenAIProvider = OpenAIProvider.from(cfg)

    private fun resolveEnv(cfg: ProviderConfig): ProviderConfig =
        cfg.copy(
            baseUrl = expand(cfg.baseUrl),
            auth = cfg.auth.copy(apiKey = expand(cfg.auth.apiKey)),
            endpoints =
                cfg.endpoints.copy(
                    chat = cfg.endpoints.chat?.let { expand(it) },
                    completions = cfg.endpoints.completions?.let { expand(it) },
                    embeddings = cfg.endpoints.embeddings?.let { expand(it) },
                    models = cfg.endpoints.models?.let { expand(it) },
                    batches = cfg.endpoints.batches?.let { expand(it) },
                    files = cfg.endpoints.files?.let { expand(it) },
                    interactions = cfg.endpoints.interactions?.let { expand(it) },
                    responses = cfg.endpoints.responses?.let { expand(it) },
                    imagesGenerations = cfg.endpoints.imagesGenerations?.let { expand(it) },
                    imagesEdits = cfg.endpoints.imagesEdits?.let { expand(it) },
                    audioSpeech = cfg.endpoints.audioSpeech?.let { expand(it) },
                    audioTranscriptions = cfg.endpoints.audioTranscriptions?.let { expand(it) },
                    rerank = cfg.endpoints.rerank?.let { expand(it) },
                    moderation = cfg.endpoints.moderation?.let { expand(it) },
                    videos = cfg.endpoints.videos?.let { expand(it) },
                    tasks = cfg.endpoints.tasks?.let { expand(it) },
                ),
        )

    private fun expand(value: String): String {
        if (!value.contains("\${")) return value
        var out = value
        Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""").findAll(value).forEach { m ->
            out = out.replace(m.value, System.getenv(m.groupValues[1]) ?: "")
        }
        return out
    }
}