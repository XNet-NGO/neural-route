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
 */
class ProviderRegistry(private val configDir: Path) {

    fun load(): List<ProviderConfig> {
        if (!Files.isDirectory(configDir)) return emptyList()
        return Files.list(configDir).use { stream ->
            stream
                .filter { it.toString().endsWith(".json") }
                .sorted()
                .map { path -> ProviderConfig.fromJson(Files.readString(path)) }
                .toList()
        }
    }

    /** Builds the runtime provider (any dialect) for the given config. */
    fun build(cfg: ProviderConfig): OpenAIProvider = OpenAIProvider.from(cfg)
}