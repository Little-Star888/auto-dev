package cc.unitmesh.llm.provider

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.clients.CustomOpenAILLMClient
import cc.unitmesh.llm.provider.model.CopilotModel
import cc.unitmesh.llm.provider.model.CopilotModelsResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * API token with expiration for GitHub Copilot
 */
private data class CopilotApiToken(
    val token: String,
    val expiresAt: Instant
) {
    fun isExpired(): Boolean {
        val now = Clock.System.now()
        val remainingSeconds = expiresAt.epochSeconds - now.epochSeconds
        return remainingSeconds < 5 * 60 // Refresh if less than 5 minutes remaining
    }
}

/**
 * JVM-only GitHub Copilot LLM Client Provider.
 * 
 * This provider integrates with GitHub Copilot by:
 * 1. Reading OAuth token from local GitHub Copilot config (~/.config/github-copilot/apps.json)
 * 2. Exchanging OAuth token for API token
 * 3. Fetching available models from /models API
 * 4. Using API token to call the OpenAI-compatible chat completions endpoint
 * 
 * Usage:
 * ```kotlin
 * // Register at app initialization
 * LLMClientRegistry.register(GithubCopilotClientProvider())
 * 
 * // Fetch available models (async)
 * val models = provider.fetchAvailableModelsAsync()
 * 
 * // Then use through ExecutorFactory
 * val config = ModelConfig(
 *     provider = LLMProviderType.GITHUB_COPILOT,
 *     modelName = "gpt-4o"
 * )
 * val executor = ExecutorFactory.createAsync(config)
 * ```
 */
@OptIn(ExperimentalTime::class)
class GithubCopilotClientProvider : LLMClientProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO)
    private val tokenMutex = Mutex()
    private val modelsMutex = Mutex()
    
    private var cachedApiToken: CopilotApiToken? = null
    
    // Model cache
    private var cachedModels: List<CopilotModel>? = null
    private var modelsLastUpdated: Long = 0
    
    override val providerType: LLMProviderType = LLMProviderType.GITHUB_COPILOT
    
    override fun isAvailable(): Boolean {
        return extractOauthToken() != null
    }
    
    override suspend fun createExecutor(config: ModelConfig): SingleLLMPromptExecutor? {
        val apiToken = getApiToken()
        if (apiToken == null) {
            logger.warn { "Failed to get GitHub Copilot API token" }
            return null
        }
        
        // Create CustomOpenAILLMClient with Copilot-specific headers
        val client = CustomOpenAILLMClient(
            apiKey = apiToken.token,
            baseUrl = COPILOT_API_BASE_URL,
            customHeaders = mapOf(
                "Editor-Version" to "Zed/Unknown",
                "Copilot-Integration-Id" to "vscode-chat"
            )
        )
        
        return SingleLLMPromptExecutor(client)
    }
    
    /**
     * Get cached available models (synchronous)
     * 
     * Returns cached model IDs if available, otherwise returns fallback list.
     * Use [fetchAvailableModelsAsync] to fetch fresh models from API.
     */
    override fun getAvailableModels(): List<String> {
        return cachedModels?.filter { it.isEnabled && !it.isEmbedding }?.map { it.id } ?: FALLBACK_MODELS
    }
    
    /**
     * Get cached CopilotModel objects with full metadata
     */
    fun getCachedCopilotModels(): List<CopilotModel>? = cachedModels
    
    /**
     * Fetch available models from GitHub Copilot API
     * 
     * @param forceRefresh Force refresh even if cache is valid (default: false)
     * @return List of model IDs
     */
    override suspend fun fetchAvailableModelsAsync(forceRefresh: Boolean): List<String> {
        val models = fetchModelsFromApi(forceRefresh)
        return models?.filter { it.isEnabled && !it.isEmbedding }?.map { it.id } ?: FALLBACK_MODELS
    }
    
    /**
     * Fetch full CopilotModel objects from API
     * 
     * @param forceRefresh Force refresh even if cache is valid
     * @return List of CopilotModel or null if failed
     */
    suspend fun fetchCopilotModelsAsync(forceRefresh: Boolean = false): List<CopilotModel>? {
        return fetchModelsFromApi(forceRefresh)
    }
    
    override fun getDefaultBaseUrl(): String = COPILOT_API_BASE_URL
    
    // ============= Private Implementation =============
    
    /**
     * Fetch models from /models API endpoint
     */
    private suspend fun fetchModelsFromApi(forceRefresh: Boolean): List<CopilotModel>? {
        return modelsMutex.withLock {
            val currentTime = System.currentTimeMillis()
            
            // Return cached if not expired (1 hour cache)
            if (!forceRefresh && cachedModels != null && (currentTime - modelsLastUpdated < CACHE_DURATION_MS)) {
                return@withLock cachedModels
            }
            
            val apiToken = getApiToken()
            if (apiToken == null) {
                logger.warn { "Cannot fetch models: API token not available" }
                return@withLock cachedModels // Return stale cache if available
            }
            
            try {
                val response = httpClient.get(MODELS_ENDPOINT) {
                    header("Authorization", "Bearer ${apiToken.token}")
                    header("Editor-Version", "Zed/Unknown")
                    header("Content-Type", "application/json")
                    header("Copilot-Integration-Id", "vscode-chat")
                }
                
                if (response.status.isSuccess()) {
                    val responseBody = response.bodyAsText()
                    logger.debug { "Models API response: $responseBody" }
                    
                    val modelsResponse = json.decodeFromString<CopilotModelsResponse>(responseBody)
                    
                    // Filter to only enabled models
                    val enabledModels = modelsResponse.data.filter { it.isEnabled }
                    
                    val originalCount = modelsResponse.data.size
                    val filteredCount = enabledModels.size
                    if (originalCount != filteredCount) {
                        logger.info { "Filtered GitHub Copilot models: $originalCount -> $filteredCount (removed ${originalCount - filteredCount} disabled models)" }
                    }
                    
                    // Update cache
                    cachedModels = enabledModels
                    modelsLastUpdated = currentTime
                    
                    logger.info { "Fetched ${enabledModels.size} GitHub Copilot models" }
                    enabledModels
                } else {
                    logger.warn { "Failed to fetch models: ${response.status}" }
                    cachedModels // Return stale cache if available
                }
            } catch (e: Exception) {
                logger.warn(e) { "Exception while fetching GitHub Copilot models" }
                cachedModels // Return stale cache if available
            }
        }
    }
    
    private fun extractOauthToken(): String? {
        val configDir = getConfigDir() ?: return null
        val appsFile = File(configDir, "apps.json")
        
        if (!appsFile.exists()) {
            logger.debug { "GitHub Copilot apps.json not found at: ${appsFile.absolutePath}" }
            return null
        }
        
        return try {
            val content = appsFile.readText()
            val jsonElement = json.parseToJsonElement(content)
            findOauthToken(jsonElement)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract OAuth token from GitHub Copilot config" }
            null
        }
    }
    
    private fun findOauthToken(element: JsonElement): String? {
        return when (element) {
            is JsonObject -> {
                element["oauth_token"]?.jsonPrimitive?.content?.let { return it }
                for ((_, value) in element) {
                    findOauthToken(value)?.let { return it }
                }
                null
            }
            is JsonArray -> {
                for (item in element) {
                    findOauthToken(item)?.let { return it }
                }
                null
            }
            else -> null
        }
    }
    
    private suspend fun getApiToken(): CopilotApiToken? {
        return tokenMutex.withLock {
            // Return cached token if still valid
            cachedApiToken?.let { token ->
                if (!token.isExpired()) {
                    return@withLock token
                }
            }
            
            val oauthToken = extractOauthToken()
            if (oauthToken == null) {
                logger.warn { "Cannot get API token: OAuth token not found" }
                return@withLock null
            }
            
            try {
                val response = httpClient.get(TOKEN_ENDPOINT) {
                    header("Authorization", "token $oauthToken")
                    header("Accept", "application/json")
                }
                
                if (response.status.isSuccess()) {
                    val responseBody = response.bodyAsText()
                    val responseJson = json.parseToJsonElement(responseBody).jsonObject
                    
                    val token = responseJson["token"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Failed to parse token from response")
                    val expiresAt = responseJson["expires_at"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: throw IllegalStateException("Failed to parse expires_at from response")
                    
                    CopilotApiToken(
                        token = token,
                        expiresAt = Instant.fromEpochSeconds(expiresAt)
                    ).also {
                        cachedApiToken = it
                        logger.info { "GitHub Copilot API token refreshed successfully" }
                    }
                } else {
                    logger.warn { "Failed to get API token: ${response.status}" }
                    null
                }
            } catch (e: Exception) {
                logger.warn(e) { "Exception while getting GitHub Copilot API token" }
                null
            }
        }
    }
    
    private fun getConfigDir(): String? {
        val homeDir = System.getProperty("user.home") ?: return null
        val osName = System.getProperty("os.name", "").lowercase()
        
        return when {
            osName.contains("windows") -> {
                val appData = System.getenv("APPDATA") ?: System.getenv("LOCALAPPDATA")
                if (appData != null) "$appData/github-copilot" else "$homeDir/AppData/Local/github-copilot"
            }
            else -> "$homeDir/.config/github-copilot"
        }
    }
    
    companion object {
        private const val COPILOT_API_BASE_URL = "https://api.githubcopilot.com/"
        private const val TOKEN_ENDPOINT = "https://api.github.com/copilot_internal/v2/token"
        private const val MODELS_ENDPOINT = "https://api.githubcopilot.com/models"
        private const val CACHE_DURATION_MS = 3600000L // 1 hour
        
        // Fallback models if API fetch fails
        private val FALLBACK_MODELS = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4",
            "claude-3.5-sonnet",
            "o1-preview",
            "o1-mini"
        )
    }
}
