package cc.unitmesh.llm.provider

import ai.koog.prompt.dsl.prompt
import cc.unitmesh.llm.ExecutorFactory
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.ModelRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Integration tests for GitHub Copilot Provider.
 * 
 * These tests require a valid GitHub Copilot configuration on the local machine.
 * Tests will be skipped if GitHub Copilot is not configured.
 */
@OptIn(ExperimentalTime::class)
class GithubCopilotClientProviderTest {

    private lateinit var provider: GithubCopilotClientProvider
    
    @BeforeTest
    fun setup() {
        provider = GithubCopilotClientProvider()
        // Register the provider
        LLMClientRegistry.register(provider)
    }

    @Test
    fun `should detect GitHub Copilot configuration status`() {
        val isAvailable = provider.isAvailable()
        println("GitHub Copilot configured: $isAvailable")
        
        if (isAvailable) {
            println("✓ GitHub Copilot is configured and available")
        } else {
            println("⚠ GitHub Copilot is not configured (test will be limited)")
        }
    }

    @Test
    fun `should get cached models from provider`() {
        // Without API call, should return fallback models
        val models = provider.getAvailableModels()
        
        assertTrue(models.isNotEmpty(), "Should have available models (fallback)")
        println("✓ Cached/fallback models: ${models.joinToString()}")
    }

    @Test
    fun `should fetch models from API dynamically`() = runBlocking {
        if (!provider.isAvailable()) {
            println("⚠ Skipping: GitHub Copilot not configured")
            return@runBlocking
        }
        
        println("Fetching models from GitHub Copilot API...")
        
        // Fetch models from API
        val models = provider.fetchAvailableModelsAsync(forceRefresh = true)
        
        assertTrue(models.isNotEmpty(), "Should have available models from API")
        println("✓ Fetched ${models.size} models from API:")
        models.forEach { println("  - $it") }
        
        // Also test fetching full model objects
        val copilotModels = provider.fetchCopilotModelsAsync()
        assertNotNull(copilotModels, "Should get CopilotModel objects")
        
        println("\n✓ Model details:")
        copilotModels.take(5).forEach { model ->
            println("  - ${model.id}: ${model.vendor}/${model.name} (context: ${model.getContextLength()})")
        }
    }
    
    @Test
    fun `should fetch models through LLMClientRegistry`() = runBlocking {
        if (!provider.isAvailable()) {
            println("⚠ Skipping: GitHub Copilot not configured")
            return@runBlocking
        }
        
        val models = LLMClientRegistry.fetchAvailableModelsAsync(LLMProviderType.GITHUB_COPILOT, forceRefresh = true)
        
        assertTrue(models.isNotEmpty(), "Should have available models from registry")
        println("✓ Fetched ${models.size} models through LLMClientRegistry:")
        models.take(10).forEach { println("  - $it") }
    }

    @Test
    fun `should get models from ModelRegistry`() {
        val models = ModelRegistry.getAvailableModels(LLMProviderType.GITHUB_COPILOT)
        
        assertTrue(models.isNotEmpty(), "ModelRegistry should have GitHub Copilot models")
        println("✓ Models from ModelRegistry (static): ${models.joinToString()}")
    }

    @Test
    fun `should check provider availability through ExecutorFactory`() {
        val isAvailable = ExecutorFactory.isProviderAvailable(LLMProviderType.GITHUB_COPILOT)
        
        // Should be true if provider is registered and available
        println("ExecutorFactory.isProviderAvailable: $isAvailable")
        println("Provider.isAvailable: ${provider.isAvailable()}")
    }

    @Test
    fun `should create executor when configured`() = runBlocking {
        if (!provider.isAvailable()) {
            println("⚠ Skipping: GitHub Copilot not configured")
            return@runBlocking
        }
        
        val config = ModelConfig(
            provider = LLMProviderType.GITHUB_COPILOT,
            modelName = "gpt-4o-mini"
        )
        
        val executor = ExecutorFactory.createAsync(config)
        
        assertNotNull(executor, "Should create executor successfully")
        println("✓ Created executor for gpt-4o-mini")
    }

    @Test
    fun `should make actual API call with simple prompt`() = runBlocking {
        if (!provider.isAvailable()) {
            println("⚠ Skipping: GitHub Copilot not configured")
            return@runBlocking
        }
        
        val config = ModelConfig(
            provider = LLMProviderType.GITHUB_COPILOT,
            modelName = "gpt-4o-mini"
        )
        
        val executor = ExecutorFactory.createAsync(config)
        assertNotNull(executor)
        
        // Create a simple prompt
        val model = ModelRegistry.createGenericModel(LLMProviderType.GITHUB_COPILOT, "gpt-4o-mini")
        
        println("Making API call to GitHub Copilot...")
        
        // Use non-streaming execute for simplicity
        val response = executor.execute(
            prompt = prompt("test") {
                system("You are a helpful assistant. Respond in one short sentence.")
                user("Say hello!")
            },
            model = model
        )
        
        assertNotNull(response, "Should get a response")
        println("✓ Got response from GitHub Copilot:")
        println(response)
    }
}
