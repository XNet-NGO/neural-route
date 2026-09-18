package com.neuralroute.api

import com.tddworks.openai.gateway.config.Capabilities
import com.tddworks.openai.gateway.config.Dialect
import com.tddworks.openai.gateway.config.ProviderConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class SurfaceInfo(
    val surface: String,
    val dialect: String,
    val chat: Int,
    val tts: Int,
    val providers: List<String>,
)

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val dialect: Dialect,
    val baseUrl: String,
    val capabilities: Capabilities,
)

fun Route.providersRoute(providers: List<ProviderConfig>) {
    route("/api/v1/surfaces") {
        get {
            val surfaces =
                com.neuralroute.DialectSurfaces.segment.map { (dialect, segment) ->
                    val eligible = providers.filter { it.dialect == dialect }
                    SurfaceInfo(
                        surface = segment,
                        dialect = dialect.name,
                        chat = eligible.count { it.capabilities.chat },
                        tts = eligible.count { it.capabilities.tts },
                        providers = eligible.map { it.id },
                    )
                }
            call.respond(surfaces)
        }
    }
    route("/api/v1/providers") {
        get {
            call.respond(
                providers.map {
                    ProviderInfo(
                        id = it.id,
                        name = it.name,
                        enabled = it.enabled,
                        dialect = it.dialect,
                        baseUrl = it.baseUrl,
                        capabilities = it.capabilities,
                    )
                },
            )
        }
    }
}