package com.neuralroute.api

import com.neuralroute.ProviderRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json

/**
 * OpenAI-compatible chat proxy: POST /v1/{provider}/chat/completions routes the D1
 * body through the config-built provider (any dialect — D2/D3/D5/D7/D8 all speak
 * this surface via llm-core). Streaming passthrough comes with Accept:
 * text/event-stream in a follow-up.
 */
fun Route.chatApiRoute(registry: ProviderRegistry) {
    val json = Json { ignoreUnknownKeys = true }
    route("/v1/{provider}/chat/completions") {
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
            val provider = registry.build(cfg)
            val body = call.receiveText()
            val request = json.decodeFromString(com.tddworks.openai.api.chat.api.ChatCompletionRequest.serializer(), body)
            val response = provider.chatCompletions(request)
            call.respondText(json.encodeToString(com.tddworks.openai.api.chat.api.ChatCompletion.serializer(), response), ContentType.Application.Json)
        }
    }
}