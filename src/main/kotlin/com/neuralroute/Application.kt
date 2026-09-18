package com.neuralroute

import com.neuralroute.api.chatApiRoute
import com.neuralroute.api.providersRoute
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * neural-route — headless config-driven LLM router over llm-core.
 *
 * Consumes the top-100 ProviderConfig database from llm-core
 * (research/providers-100 directory), builds providers via OpenAIProvider.from(config),
 * and exposes:
 *   GET  /health
 *   GET  /api/v1/providers              admin REST (provider inventory)
 *   POST /v1/{provider}/chat/completions OpenAI-compat D1 proxy (any dialect)
 */
fun main(args: Array<String>) {
    val port = System.getenv("NEURAL_ROUTE_PORT")?.toIntOrNull() ?: 8080
    val configDir = System.getenv("NEURAL_ROUTE_CONFIG_DIR")?.let { Path.of(it) }
        ?: Path.of("src/main/resources/provider-configs")
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(configDir)
    }.start(wait = true)
}

fun Application.module(configDir: Path = Path.of("src/main/resources/provider-configs")) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(CORS) {
        anyHost()
    }
    val registry = ProviderRegistry(configDir)
    val providers = registry.load()
    routing {
        get("/health") { call.respond(com.neuralroute.api.HealthResponse("ok", providers.size)) }
        providersRoute(providers)
        chatApiRoute(registry)
    }
}