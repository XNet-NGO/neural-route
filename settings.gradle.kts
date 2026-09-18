rootProject.name = "neural-route"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// llm-core provides the config-driven provider engine (D1-D8 dialects, transforms,
// provider-100 config DB). Composite build so config/schema changes stay live.
includeBuild("../llm-core") {
    dependencySubstitution {
        substitute(module("com.tddworks:openai-gateway-core")).using(project(":openai-gateway:openai-gateway-core"))
        substitute(module("com.tddworks:common")).using(project(":common"))
        substitute(module("com.tddworks:responses-client-core")).using(project(":responses-client:responses-client-core"))
        substitute(module("com.tddworks:voice-client-core")).using(project(":voice-client:voice-client-core"))
    }
}