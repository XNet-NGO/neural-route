package com.neuralroute.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val providers: Int)
