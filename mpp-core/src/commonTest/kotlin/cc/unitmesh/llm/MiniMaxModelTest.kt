package cc.unitmesh.llm

import ai.koog.prompt.llm.LLMCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for MiniMax model configuration and registry.
 */
class MiniMaxModelTest {

    @Test
    fun `test minimax provider type exists`() {
        val providerType = LLMProviderType.valueOf("MINIMAX")
        assertEquals("MiniMax", providerType.displayName)
    }

    @Test
    fun `test minimax available models list`() {
        val models = ModelRegistry.getAvailableModels(LLMProviderType.MINIMAX)
        assertTrue(models.isNotEmpty(), "MiniMax should have available models")
        assertTrue(models.contains("MiniMax-M2.1"), "MiniMax-M2.1 should be available")
        assertTrue(models.contains("MiniMax-M2.0"), "MiniMax-M2.0 should be available")
    }

    @Test
    fun `test minimax default base url`() {
        val baseUrl = ModelRegistry.getDefaultBaseUrl(LLMProviderType.MINIMAX)
        assertEquals("https://api.minimaxi.com/v1/", baseUrl)
    }

    @Test
    fun `test minimax M2_1 model creation`() {
        val model = ModelRegistry.createModel(LLMProviderType.MINIMAX, "MiniMax-M2.1")
        assertNotNull(model, "MiniMax-M2.1 model should be created")
        assertEquals("MiniMax-M2.1", model.id)
        assertEquals(1_000_000L, model.contextLength)
        assertEquals(128_000, model.maxOutputTokens)
        val capabilities = model.capabilities.orEmpty()
        assertTrue(capabilities.contains(LLMCapability.Completion))
        assertTrue(capabilities.contains(LLMCapability.Tools))
    }

    @Test
    fun `test minimax M2_0 model creation`() {
        val model = ModelRegistry.createModel(LLMProviderType.MINIMAX, "MiniMax-M2.0")
        assertNotNull(model, "MiniMax-M2.0 model should be created")
        assertEquals("MiniMax-M2.0", model.id)
        assertEquals(1_000_000L, model.contextLength)
        assertEquals(64_000, model.maxOutputTokens)
    }

    @Test
    fun `test minimax text model creation`() {
        val model = ModelRegistry.createModel(LLMProviderType.MINIMAX, "MiniMax-Text-01")
        assertNotNull(model, "MiniMax-Text-01 model should be created")
        assertEquals("MiniMax-Text-01", model.id)
        assertEquals(1_000_000L, model.contextLength)
        assertEquals(32_000, model.maxOutputTokens)
    }

    @Test
    fun `test minimax vision model capabilities`() {
        val model = ModelRegistry.createModel(LLMProviderType.MINIMAX, "MiniMax-Text-01V")
        assertNotNull(model, "MiniMax-Text-01V model should be created")
        val capabilities = model.capabilities.orEmpty()
        assertTrue(capabilities.contains(LLMCapability.Vision.Image))
        assertTrue(capabilities.contains(LLMCapability.Document))
    }

    @Test
    fun `test minimax model config validation`() {
        val validConfig = ModelConfig(
            provider = LLMProviderType.MINIMAX,
            modelName = "MiniMax-M2.1",
            apiKey = "test-api-key"
        )
        assertTrue(validConfig.isValid(), "Valid MiniMax config should pass validation")

        val invalidConfigNoApiKey = ModelConfig(
            provider = LLMProviderType.MINIMAX,
            modelName = "MiniMax-M2.1",
            apiKey = ""
        )
        assertTrue(!invalidConfigNoApiKey.isValid(), "Config without API key should fail validation")

        val invalidConfigNoModelName = ModelConfig(
            provider = LLMProviderType.MINIMAX,
            modelName = "",
            apiKey = "test-api-key"
        )
        assertTrue(!invalidConfigNoModelName.isValid(), "Config without model name should fail validation")
    }

    @Test
    fun `test minimax generic model creation`() {
        val model = ModelRegistry.createGenericModel(LLMProviderType.MINIMAX, "custom-minimax-model")
        assertNotNull(model, "Generic MiniMax model should be created")
        assertEquals("custom-minimax-model", model.id)
        assertEquals(128_000L, model.contextLength)
    }
}
