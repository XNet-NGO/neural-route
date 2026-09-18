package com.neuralroute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ProviderRegistryTest {

    @Test
    fun `loads all json configs and parses dialect`() {
        val dir = Files.createTempDirectory("cfg")
        Files.writeString(dir.resolve("a.json"), """{"id":"x","baseUrl":"https://a.test"}""")
        Files.writeString(dir.resolve("b.json"), """{"id":"y","dialect":"TEMPLATE","baseUrl":"https://b.test"}""")
        Files.writeString(dir.resolve("ignored.txt"), "not json")
        val providers = ProviderRegistry(dir).load()
        assertEquals(2, providers.size)
        assertEquals("x", providers[0].id)
        assertEquals(com.tddworks.openai.gateway.config.Dialect.TEMPLATE, providers[1].dialect)
    }

    @Test
    fun `empty or missing dir yields empty registry`() {
        assertTrue(ProviderRegistry(Path.of("/nonexistent")).load().isEmpty())
    }

    @Test
    fun `build constructs provider for any dialect`() {
        val dir = Files.createTempDirectory("cfg")
        Files.writeString(dir.resolve("o.json"), """{"id":"o","baseUrl":"http://localhost:11434"}""")
        val registry = ProviderRegistry(dir)
        val provider = registry.build(registry.load().first())
        assertEquals("o", provider.id)
    }

    @Test
    fun `env placeholders expand from environment`() {
        val dir = Files.createTempDirectory("cfg")
        Files.writeString(dir.resolve("k.json"), """{"id":"k","baseUrl":"https://h.test","auth":{"scheme":"X_API_KEY","apiKey":"${'$'}{CP_TEST_KEY}","keyHeader":"x-goog-api-key"}}""")
        // placeholder not in env -> empty key, first-class unauthenticated state
        val emptyKey = ProviderRegistry(dir).load().first()
        assertEquals("", emptyKey.auth.apiKey)
    }

    @Test
    fun `build throws for unsupported dialect at load time`() {
        val dir = Files.createTempDirectory("cfg")
        Files.writeString(
            dir.resolve("v.json"),
            """{"id":"v","dialect":"VOICE_REALTIME","baseUrl":"wss://v"}""",
        )
        val registry = ProviderRegistry(dir)
        val e = runCatching { registry.build(registry.load().first()) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }
}