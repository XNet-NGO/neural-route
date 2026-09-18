package com.neuralroute.api

import com.neuralroute.DialectSurfaces
import com.neuralroute.ProviderRegistry
import com.neuralroute.api.handleChatFor
import com.neuralroute.api.handleAudioFor
import com.tddworks.openai.gateway.config.Dialect
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * The eight dialect API surfaces: /{dialect}/v1/chat/completions and
 * /{dialect}/v1/audio/speech resolve the requested model to a compatible provider
 * config (neural-route owns routing; clients only pick a dialect + model slug).
 *
 *   openai, anthropic, gemini, bedrock, responses, voice, azure, template
 */
fun Route.dialectRoutes(registry: ProviderRegistry) {
    val providers = registry.load()
    DialectSurfaces.segment.forEach { (dialect, segment) ->
        route("/$segment/v1/chat/completions") {
            post {
                handleChatFor(registry, providers, dialect)
            }
        }
        route("/$segment/v1/models") {
            get {
                call.respond(
                    com.neuralroute.api.ModelsResponse(
                        data = DialectSurfaces.modelsFor(providers, dialect).map { com.neuralroute.api.ModelEntry(it) },
                    ),
                )
            }
        }
        if (dialect == Dialect.TEMPLATE) {
            route("/$segment/v1/audio/speech") {
                post {
                    handleAudioFor(registry, providers, dialect)
                }
            }
        }
    }
}