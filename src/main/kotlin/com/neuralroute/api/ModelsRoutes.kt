package com.neuralroute.api

import com.neuralroute.DialectSurfaces
import com.neuralroute.ProviderRegistry
import com.tddworks.openai.gateway.config.Dialect
import com.tddworks.openai.gateway.config.ProviderConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * OpenAI-compat model listings so unmodified SDKs pass their startup /models probe:
 *
 *   GET /{dialect}/v1/models     — models addressable on that dialect surface
 *   GET /{provider}/v1/models    — models addressable via that provider
 */
@Serializable
data class ModelEntry(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val `object`: String = "model",
)

@Serializable
data class ModelsResponse(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val `object`: String = "list",
    val data: List<ModelEntry>,
)

fun Route.modelsRoute(registry: ProviderRegistry, providers: List<ProviderConfig>) {
    DialectSurfaces.segment.forEach { (dialect, segment) ->
        route("/$segment/v1/models") {
            get {
                call.respond(
                    ModelsResponse(
                        data = DialectSurfaces.modelsFor(providers, dialect).map { ModelEntry(it) },
                    ),
                )
            }
        }
    }
    route("/{provider}/v1/models") {
        get {
            val providerId = call.parameters["provider"]
            val cfg = providers.firstOrNull { it.id == providerId || it.name == providerId }
            if (cfg == null) {
                call.respond(
                    ModelsResponse(data = emptyList()),
                )
            } else {
                call.respond(
                    ModelsResponse(
                        data =
                            (cfg.aliases.keys + cfg.aliases.values + cfg.catalog.models.map { it.id })
                                .distinct()
                                .sorted()
                                .map { ModelEntry(it) },
                    ),
                )
            }
        }
    }
}
