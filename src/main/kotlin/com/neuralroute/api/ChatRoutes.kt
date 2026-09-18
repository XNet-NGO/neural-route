package com.neuralroute.api

import com.neuralroute.ProviderRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json

/**
 * OpenAI-compatible chat proxy. Canonical shape keeps the provider BEFORE the
 * version segment so unmodified OpenAI SDK clients work:
 *
 *   base_url = http://host/{provider}/v1  ->  POST /v1/chat/completions
 *
 *   POST /{provider}/v1/chat/completions   (canonical)
 *   POST /v1/{provider}/chat/completions   (deprecated alias)
 *
 * The body is standard D1 and routes through any dialect via llm-core.
 */
fun Route.chatApiRoute(registry: ProviderRegistry) {
    route("/{provider}/v1/chat/completions") { post { handleChat(registry) } }
    route("/v1/{provider}/chat/completions") { post { handleChat(registry) } }
}

private suspend fun RoutingContext.handleChat(registry: ProviderRegistry) {
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
    val provider = registry.build(cfg)
    val body = call.receiveText()
    val request = json.decodeFromString(com.tddworks.openai.api.chat.api.ChatCompletionRequest.serializer(), body)
    val response =
        runCatching { provider.chatCompletions(request) }
            .getOrElse { e ->
                val detail = e.message ?: "proxy error"
                val status =
                    when {
                        detail.contains("404") -> HttpStatusCode.NotFound
                        detail.contains("401") || detail.contains("403") -> HttpStatusCode.Unauthorized
                        detail.contains("429") || detail.contains("rate") -> HttpStatusCode.TooManyRequests
                        else -> HttpStatusCode.BadGateway
                    }
                return call.respondText(
                    """{"error":{"type":"upstream","status":${status.value},"message":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive(detail))}}}""",
                    ContentType.Application.Json,
                    status,
                )
            }
    call.respondText(json.encodeToString(com.tddworks.openai.api.chat.api.ChatCompletion.serializer(), response), ContentType.Application.Json)
}
