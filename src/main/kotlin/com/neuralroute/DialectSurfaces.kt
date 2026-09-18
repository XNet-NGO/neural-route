package com.neuralroute

import com.tddworks.openai.gateway.config.Dialect
import com.tddworks.openai.gateway.config.ProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dialect-surface routing: the router exposes one API per dialect
 * (/{dialect}/v1/chat/completions etc.) and resolves the requested model slug to a
 * compatible provider config — clients never address providers directly.
 *
 * Resolution order for a model slug within a dialect surface:
 *  1. providers whose aliases map the slug (slug -> upstream id) — first in config
 *     order wins;
 *  2. providers whose aliases/catalog already use the slug as the upstream id;
 *  3. fallback: the first provider of the dialect with the requested capability
 *     (upstream surfaces unknown-model errors verbatim).
 */
object DialectSurfaces {

    /** URL segment for each dialect. */
    val segment: Map<Dialect, String> =
        mapOf(
            Dialect.OPENAI_COMPAT to "openai",
            Dialect.ANTHROPIC to "anthropic",
            Dialect.GEMINI to "gemini",
            Dialect.BEDROCK to "bedrock",
            Dialect.RESPONSES to "responses",
            Dialect.VOICE_REALTIME to "voice",
            Dialect.AZURE_OPENAI to "azure",
            Dialect.TEMPLATE to "template",
        )

    private val json = Json { ignoreUnknownKeys = true }

    data class Resolved(val provider: ProviderConfig, val model: String?, val voice: String?)

    /** Resolves a chat model slug on a dialect surface. */
    fun resolveChat(providers: List<ProviderConfig>, dialect: Dialect, model: String?, capability: Boolean = true): Resolved {
        val eligible = providers.filter { it.dialect == dialect && (capability || it.capabilities.chat) }
        if (eligible.isEmpty()) throw NoSuchElementException("no providers for dialect $dialect")
        model?.let { slug ->
            eligible.firstOrNull { it.aliases.containsKey(slug) }?.let { p ->
                return Resolved(p, p.aliases[slug], null)
            }
            eligible.firstOrNull { it.aliases.values.contains(slug) || (it.catalog.models.any { m -> m.id == slug }) }?.let { p ->
                return Resolved(p, slug, null)
            }
        }
        return Resolved(eligible.first(), model, null)
    }

    /** Resolves an audio (TTS) request on a dialect surface (defaults from aliases). */
    fun resolveAudio(providers: List<ProviderConfig>, dialect: Dialect, model: String?): Resolved {
        val eligible = providers.filter { it.dialect == dialect && it.capabilities.tts }
        if (eligible.isEmpty()) throw NoSuchElementException("no tts providers for dialect $dialect")
        model?.let { slug ->
            eligible.firstOrNull { it.aliases.containsKey(slug) }?.let { p ->
                return Resolved(p, p.aliases[slug], p.aliases["voice"])
            }
        }
        val first = eligible.first()
        val resolvedModel =
            model
                ?: first.aliases["tts-model"]
                ?: first.aliases.values.firstOrNull()
        return Resolved(first, resolvedModel, first.aliases["voice"])
    }

    /**
     * Model slugs addressable on a dialect surface: alias keys + upstream ids +
     * static catalog ids, deduplicated, across all providers of the dialect.
     */
    fun modelsFor(providers: List<ProviderConfig>, dialect: Dialect): List<String> =
        providers
            .filter { it.dialect == dialect }
            .flatMap { p ->
                p.aliases.keys + p.aliases.values + p.catalog.models.map { it.id }
            }
            .distinct()
            .sorted()

    /** Rewrites the "model" field of a chat body to the resolved upstream id (JSON-preserving). */
    fun rewriteModel(body: String, upstreamModel: String?): String {
        if (upstreamModel == null) return body
        val root = json.parseToJsonElement(body).jsonObject
        val modified = root.toMutableMap()
        modified["model"] = kotlinx.serialization.json.JsonPrimitive(upstreamModel)
        return json.encodeToString(kotlinx.serialization.json.JsonObject(modified))
    }
}