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
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenAI-compat TTS surface for transform-driven providers:
 * POST /v1/{provider}/audio/speech {model?, input, voice?} -> audio bytes
 * (audio/mpeg). Any provider whose config carries an `audioSpeech` transform
 * (ElevenLabs today; Cartesia/Deepgram/MiniMax are config-only additions).
 */
@Serializable
data class TtsInput(val model: String? = null, val input: String, val voice: String? = null)

fun Route.audioRoute(registry: ProviderRegistry) {
    val json = Json { ignoreUnknownKeys = true }
    route("/v1/{provider}/audio/speech") {
        post {
            val providerId = call.parameters["provider"] ?: return@post call.respondText(
                "missing provider",
                ContentType.Text.Plain,
                HttpStatusCode.BadRequest,
            )
            val cfg = registry.load().firstOrNull { it.id == providerId || it.name == providerId }
                ?: return@post call.respondText(
                    "unknown provider: $providerId",
                    ContentType.Text.Plain,
                    HttpStatusCode.NotFound,
                )
            val tts = registry.build(cfg) as? TtsApi
                ?: return@post call.respondText(
                    "provider $providerId has no audioSpeech transform",
                    ContentType.Text.Plain,
                    HttpStatusCode.BadRequest,
                )
            val body = json.parseToJsonElement(call.receiveText()).jsonObject
            val input = body["input"]?.jsonPrimitive?.contentOrNull
                ?: return@post call.respondText(
                    """{"error":{"message":"input is required"}}""",
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
            val audio =
                tts.synthesize(
                    TtsRequest(
                        text = input,
                        model = body["model"]?.jsonPrimitive?.content,
                        voice = body["voice"]?.jsonPrimitive?.content,
                    ),
                )
            call.respondBytes(audio, ContentType.Audio.MPEG)
        }
    }
}