package com.neuralroute

import com.tddworks.openai.gateway.config.Capabilities
import com.tddworks.openai.gateway.config.Dialect
import com.tddworks.openai.gateway.config.ProviderConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DialectSurfacesTest {

    private fun cfg(id: String, dialect: Dialect = Dialect.OPENAI_COMPAT, aliases: Map<String, String> = emptyMap(), tts: Boolean = false) =
        ProviderConfig(
            id = id,
            dialect = dialect,
            baseUrl = "https://$id.test",
            aliases = aliases,
            capabilities = if (tts) Capabilities(tts = true) else Capabilities(),
        )

    @Test
    fun `resolveChat maps alias slug to owning provider`() {
        val providers = listOf(cfg("a", aliases = mapOf("gemma-4" to "gemma")), cfg("b", aliases = mapOf("qwen3-flash" to "qwen3.8-flash")))
        val r1 = DialectSurfaces.resolveChat(providers, Dialect.OPENAI_COMPAT, "qwen3-flash")
        assertEquals("b", r1.provider.id)
        assertEquals("qwen3.8-flash", r1.model)
        val r2 = DialectSurfaces.resolveChat(providers, Dialect.OPENAI_COMPAT, "gemma-4")
        assertEquals("a", r2.provider.id)
    }

    @Test
    fun `resolveChat falls back to first dialect provider on unknown model`() {
        val providers = listOf(cfg("a"), cfg("b"))
        val r = DialectSurfaces.resolveChat(providers, Dialect.OPENAI_COMPAT, "no-such-model")
        assertEquals("a", r.provider.id)
        assertEquals("no-such-model", r.model)
    }

    @Test
    fun `resolveChat matches upstream ids directly`() {
        val providers = listOf(cfg("x", aliases = mapOf("s" to "upstream-1")), cfg("y", aliases = mapOf("k" to "upstream-1")))
        assertEquals("x", DialectSurfaces.resolveChat(providers, Dialect.OPENAI_COMPAT, "upstream-1").provider.id)
    }

    @Test
    fun `resolveChat throws when dialect has no providers`() {
        assertThrows(NoSuchElementException::class.java) {
            DialectSurfaces.resolveChat(emptyList(), Dialect.BEDROCK, "m")
        }
    }

    @Test
    fun `resolveAudio picks tts provider with defaults`() {
        val providers = listOf(cfg("t", dialect = Dialect.TEMPLATE, aliases = mapOf("tts-model" to "eleven_v3", "voice" to "sarah"), tts = true))
        val r = DialectSurfaces.resolveAudio(providers, Dialect.TEMPLATE, null)
        assertEquals("t", r.provider.id)
        assertEquals("eleven_v3", r.model)
        assertEquals("sarah", r.voice)
        val withModel = DialectSurfaces.resolveAudio(providers, Dialect.TEMPLATE, "tts-model")
        assertEquals("eleven_v3", withModel.model)
    }

    @Test
    fun `rewriteModel replaces model preserving rest of body`() {
        val out = DialectSurfaces.rewriteModel("""{"model":"slug","messages":[],"max_tokens":16}""", "upstream")
        assertEquals("""{"model":"upstream","messages":[],"max_tokens":16}""", out)
    }
}
