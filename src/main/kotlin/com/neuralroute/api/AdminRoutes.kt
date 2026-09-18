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
data class ProviderInfo(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val dialect: Dialect,
    val baseUrl: String,
    val capabilities: Capabilities,
)

fun Route.providersRoute(providers: List<ProviderConfig>) {
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