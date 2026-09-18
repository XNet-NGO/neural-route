package com.neuralroute.api

import com.neuralroute.DialectSurfaces
import com.neuralroute.ProviderRegistry
import com.tddworks.openai.gateway.api.internal.TtsApi
import com.tddworks.openai.gateway.api.internal.TtsRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * TTS surfaces for transform-driven providers.
 *
 *   POST /{dialect}/v1/audio/speech      (canonical: dialect-resolved, /template/...)
 *   POST /{provider}/v1/audio/speech     (provider-pinned)
 *   POST /v1/{provider}/audio/speech     (deprecated alias)
 *
 * Body: {model?, input, voice?} -> audio/mpeg bytes.
 */
@Serializable
data class TtsInput(val model: String? = null, val input: String, val voice: String? = null)

fun Route.audioRoute(registry: ProviderRegistry) {
    val providers = registry.load()
    route("/{provider}/v1/audio/speech") { post { handleAudioFor(registry, providers, null) } }
    route("/v1/{provider}/audio/speech") { post { handleAudioFor(registry, providers, null) } }
}

/** Shared audio handler: provider-pinned when [surfaceDialect] is null, else dialect-resolved. */
suspend fun RoutingContext.handleAudioFor(
    registry: ProviderRegistry,
    providers: List<com.tddworks.openai.gateway.config.ProviderConfig>,
    surfaceDialect: com.tddworks.openai.gateway.config.Dialect?,
) {
    val json = Json { ignoreUnknownKeys = true }
    val bodyText = call.receiveText()
    val body = json.parseToJsonElement(bodyText).jsonObject
    val input = body["input"]?.jsonPrimitive?.contentOrNull
        ?: return call.respondText(
            """{"error":{"message":"input is required"}}""",
            ContentType.Application.Json,
            HttpStatusCode.BadRequest,
        )
    val modelFromBody = body["model"]?.jsonPrimitive?.contentOrNull
    val cfg: com.tddworks.openai.gateway.config.ProviderConfig
    val upstreamModel: String?
    if (surfaceDialect != null) {
        val resolved =
            runCatching { DialectSurfaces.resolveAudio(providers, surfaceDialect, modelFromBody) }
                .getOrElse { e ->
                    return call.respondText(
                        """{"error":{"message":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive(e.message ?: "no tts providers"))}}}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                }
        cfg = resolved.provider
        upstreamModel = resolved.model
    } else {
        val providerId = call.parameters["provider"] ?: return call.respondText(
            "missing provider",
            ContentType.Text.Plain,
            HttpStatusCode.BadRequest,
        )
        cfg = providers.firstOrNull { it.id == providerId || it.name == providerId }
            ?: return call.respondText(
                "unknown provider: $providerId",
                ContentType.Text.Plain,
                HttpStatusCode.NotFound,
            )
        upstreamModel = body["model"]?.jsonPrimitive?.content
    }
    val tts = registry.build(cfg) as? TtsApi
        ?: return call.respondText(
            "provider ${cfg.id} has no audioSpeech transform",
            ContentType.Text.Plain,
            HttpStatusCode.BadRequest,
        )
    val audio =
        tts.synthesize(
            TtsRequest(
                text = input,
                model = upstreamModel,
                voice = body["voice"]?.jsonPrimitive?.content ?: cfg.aliases["voice"],
            ),
        )
    call.respondBytes(audio, ContentType.Audio.MPEG)
}