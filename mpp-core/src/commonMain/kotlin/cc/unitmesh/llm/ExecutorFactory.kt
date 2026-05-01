package cc.unitmesh.llm

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.*
import cc.unitmesh.llm.clients.CustomOpenAILLMClient
import cc.unitmesh.llm.provider.LLMClientProvider
import cc.unitmesh.llm.provider.LLMClientRegistry
import kotlin.time.ExperimentalTime

/**
 * Try to auto-register GitHub Copilot provider.
 * Returns the provider if successfully registered, null otherwise.
 *
 * Implementation is platform-specific:
 * - JVM: Creates and registers GithubCopilotClientProvider
 * - Other platforms: Returns null
 */
internal expect fun tryAutoRegisterGithubCopilot(): LLMClientProvider?

/**
 * Platform-specific blocking executor creation for registered providers.
 *
 * Implementation is platform-specific:
 * - JVM: Uses runBlocking to create executor synchronously
 * - JS/WASM: Returns null (registered providers not supported in sync mode)
 */
internal expect fun createExecutorBlocking(
    provider: LLMClientProvider,
    config: ModelConfig
): SingleLLMPromptExecutor?

/**
 * Executor 工厂 - 负责根据配置创建合适的 LLM Executor
 * 职责：
 * 1. 根据 Provider 类型创建对应的 Executor
 * 2. 处理不同 Provider 的初始化逻辑
 * 3. 统一 Executor 创建接口
 * 4. 支持通过 LLMClientRegistry 注册的扩展 Provider
 * 
 * For extensible providers (like GitHub Copilot on JVM), register them via:
 * ```kotlin
 * LLMClientRegistry.register(GithubCopilotClientProvider())
 * ```
 */
@OptIn(ExperimentalTime::class)
object ExecutorFactory {
    
    /**
     * 根据模型配置创建 Executor
     * 
     * Note: For providers registered via LLMClientRegistry (like GitHub Copilot on JVM),
     * this method will use blocking initialization. For non-blocking initialization,
     * use [createAsync] instead.
     */
    fun create(config: ModelConfig): SingleLLMPromptExecutor {
        // First check if provider is registered in the registry (for extensible providers)
        var registryProvider = LLMClientRegistry.getProvider(config.provider)
        
        // Auto-register GitHub Copilot provider on JVM if not already registered
        if (registryProvider == null && config.provider == LLMProviderType.GITHUB_COPILOT) {
            registryProvider = tryAutoRegisterGithubCopilot()
        }
        
        if (registryProvider != null) {
            // Use platform-specific blocking call for registered providers
            // This works because on JVM, the provider caches the API token
            return createExecutorBlocking(registryProvider, config)
                ?: throw IllegalStateException(
                    "Failed to create executor for ${config.provider.displayName}. " +
                    "Provider is registered but returned null. " +
                    "On non-JVM platforms, use createAsync() instead."
                )
        }
        
        return when (config.provider) {
            LLMProviderType.OPENAI -> createOpenAI(config)
            LLMProviderType.ANTHROPIC -> createAnthropic(config)
            LLMProviderType.GOOGLE -> createGoogle(config)
            LLMProviderType.DEEPSEEK -> createDeepSeek(config)
            LLMProviderType.OLLAMA -> createOllama(config)
            LLMProviderType.OPENROUTER -> createOpenRouter(config)
            LLMProviderType.GLM -> createGLM(config)
            LLMProviderType.QWEN -> createQwen(config)
            LLMProviderType.KIMI -> createKimi(config)
            LLMProviderType.MINIMAX -> createMinimax(config)
            LLMProviderType.GITHUB_COPILOT -> throw IllegalStateException(
                "GitHub Copilot is not available. Make sure GitHub Copilot is configured on your system " +
                "(OAuth token in ~/.config/github-copilot/apps.json)"
            )
            LLMProviderType.CUSTOM_OPENAI_BASE -> createCustomOpenAI(config)
        }
    }
    
    
    /**
     * 根据模型配置异步创建 Executor
     * 
     * This method supports providers that require async initialization,
     * such as GitHub Copilot which needs to fetch API tokens.
     * 
     * @param config Model configuration
     * @return SingleLLMPromptExecutor or throws if creation fails
     */
    suspend fun createAsync(config: ModelConfig): SingleLLMPromptExecutor {
        // First, check if there's a registered provider in the registry
        val registryExecutor = LLMClientRegistry.createExecutor(config)
        if (registryExecutor != null) {
            return registryExecutor
        }
        
        // Fall back to built-in providers
        return when (config.provider) {
            LLMProviderType.GITHUB_COPILOT -> throw IllegalStateException(
                "GitHub Copilot provider is not available. " +
                "Make sure to register GithubCopilotClientProvider on JVM platform: " +
                "LLMClientRegistry.register(GithubCopilotClientProvider())"
            )
            else -> create(config)
        }
    }
    
    /**
     * Check if a provider is available (either built-in or registered)
     */
    fun isProviderAvailable(providerType: LLMProviderType): Boolean {
        // Check registry first for extensible providers
        if (LLMClientRegistry.isProviderAvailable(providerType)) {
            return true
        }
        
        // Built-in providers are always available (except GITHUB_COPILOT which requires registration)
        return providerType != LLMProviderType.GITHUB_COPILOT
    }

    private fun createOpenAI(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleOpenAIExecutor(config.apiKey)
    }

    private fun createAnthropic(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleAnthropicExecutor(config.apiKey)
    }

    private fun createGoogle(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleGoogleAIExecutor(config.apiKey)
    }

    private fun createDeepSeek(config: ModelConfig): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(DeepSeekLLMClient(config.apiKey))
    }

    private fun createOllama(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { "http://localhost:11434" }
        return simpleOllamaAIExecutor(baseUrl = baseUrl)
    }

    private fun createOpenRouter(config: ModelConfig): SingleLLMPromptExecutor {
        return simpleOpenRouterExecutor(config.apiKey)
    }

    private fun createGLM(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { ModelRegistry.getDefaultBaseUrl(LLMProviderType.GLM) }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }

    private fun createQwen(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { ModelRegistry.getDefaultBaseUrl(LLMProviderType.QWEN) }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }

    private fun createKimi(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { ModelRegistry.getDefaultBaseUrl(LLMProviderType.KIMI) }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }

    private fun createCustomOpenAI(config: ModelConfig): SingleLLMPromptExecutor {
        require(config.baseUrl.isNotEmpty()) { "baseUrl is required for custom OpenAI provider" }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }

    private fun createMinimax(config: ModelConfig): SingleLLMPromptExecutor {
        val baseUrl = config.baseUrl.ifEmpty { ModelRegistry.getDefaultBaseUrl(LLMProviderType.MINIMAX) }
        return SingleLLMPromptExecutor(
            CustomOpenAILLMClient(
                apiKey = config.apiKey,
                baseUrl = baseUrl,
                customHeaders = config.customHeaders
            )
        )
    }
}
