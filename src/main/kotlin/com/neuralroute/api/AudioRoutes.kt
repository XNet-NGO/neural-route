package com.neuralroute.api

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
 * OpenAI-compat TTS surface for transform-driven providers.
 *
 *   base_url = http://host/{provider}/v1  ->  POST /v1/audio/speech
 *
 *   POST /{provider}/v1/audio/speech   (canonical)
 *   POST /v1/{provider}/audio/speech   (deprecated alias)
 *
 * Body: {model?, input, voice?} -> audio/mpeg bytes.
 */
@Serializable
data class TtsInput(val model: String? = null, val input: String, val voice: String? = null)

fun Route.audioRoute(registry: ProviderRegistry) {
    route("/{provider}/v1/audio/speech") { post { handleAudio(registry) } }
    route("/v1/{provider}/audio/speech") { post { handleAudio(registry) } }
}

private suspend fun RoutingContext.handleAudio(registry: ProviderRegistry) {
    val json = Json { ignoreUnknownKeys = true }
    val providerId = call.parameters["provider"] ?: return call.respondText(
        "missing provider",
        ContentType.Text.Plain,
        HttpStatusCode.BadRequest,
    )
    val cfg = registry.load().firstOrNull { it.id == providerId || it.name == providerId }
        ?: return call.respondText(
            "unknown provider: $providerId",
            ContentType.Text.Plain,
            HttpStatusCode.NotFound,
        )
    val tts = registry.build(cfg) as? TtsApi
        ?: return call.respondText(
            "provider $providerId has no audioSpeech transform",
            ContentType.Text.Plain,
            HttpStatusCode.BadRequest,
        )
    val body = json.parseToJsonElement(call.receiveText()).jsonObject
    val input = body["input"]?.jsonPrimitive?.contentOrNull
        ?: return call.respondText(
            """{"error":{"message":"input is required"}}""",
            ContentType.Application.Json,
            HttpStatusCode.BadRequest,
        )
    val audio =
        tts.synthesize(
            TtsRequest(
                text = input,
                model = body["model"]?.jsonPrimitive?.content,
                voice = body["voice"]?.jsonPrimitive?.content ?: cfg.aliases["voice"],
            ),
        )
    call.respondBytes(audio, ContentType.Audio.MPEG)
}