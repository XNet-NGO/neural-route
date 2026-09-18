package com.neuralroute.api

import com.neuralroute.DialectSurfaces
import com.neuralroute.ProviderRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Chat proxy routes. Two addressing modes:
 *
 * 1. Dialect surfaces (canonical): POST /{dialect}/v1/chat/completions —
 *    the router resolves model slug -> compatible provider config.
 * 2. Provider-pinned: POST /{provider}/v1/chat/completions (+ /v1/{provider}/... alias).
 *
 * Bodies are standard D1 and route through any dialect via llm-core.
 */
fun Route.chatApiRoute(registry: ProviderRegistry) {
    val providers = registry.load()
    route("/{provider}/v1/chat/completions") { post { handleChatFor(registry, providers, null) } }
    route("/v1/{provider}/chat/completions") { post { handleChatFor(registry, providers, null) } }
    route("/{provider}/v1/models") {
        get {
            val providerId = call.parameters["provider"]
            val cfg = providers.firstOrNull { it.id == providerId || it.name == providerId }
            call.respond(
                com.neuralroute.api.ModelsResponse(
                    data =
                        cfg
                            ?.let { (it.aliases.keys + it.aliases.values + it.catalog.models.map { m -> m.id }).distinct().sorted() }
                            .orEmpty()
                            .map { com.neuralroute.api.ModelEntry(it) },
                ),
            )
        }
    }
}

/** Shared chat handler: provider-pinned when [surfaceDialect] is null, else dialect-resolved. */
suspend fun RoutingContext.handleChatFor(
    registry: ProviderRegistry,
    providers: List<com.tddworks.openai.gateway.config.ProviderConfig>,
    surfaceDialect: com.tddworks.openai.gateway.config.Dialect?,
) {
    val json = Json { ignoreUnknownKeys = true }
    val body = call.receiveText()
    val modelFromBody = json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.contentOrNull
    val cfg: com.tddworks.openai.gateway.config.ProviderConfig
    val upstreamModel: String?
    if (surfaceDialect != null) {
        val resolved =
            runCatching { DialectSurfaces.resolveChat(providers, surfaceDialect, modelFromBody) }
                .getOrElse { e ->
                    return call.respondText(
                        """{"error":{"message":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive(e.message ?: "no providers"))}}}""",
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
        upstreamModel = null
    }
    val provider = registry.build(cfg)
    val routedBody = if (upstreamModel != null) DialectSurfaces.rewriteModel(body, upstreamModel) else body
    val request = json.decodeFromString(com.tddworks.openai.api.chat.api.ChatCompletionRequest.serializer(), routedBody)
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
