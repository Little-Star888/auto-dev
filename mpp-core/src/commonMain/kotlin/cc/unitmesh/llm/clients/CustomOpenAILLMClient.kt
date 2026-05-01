package cc.unitmesh.llm.clients

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAIBaseSettings
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Configuration settings for custom OpenAI-compatible APIs (like GLM, custom endpoints, etc.)
 *
 * @property baseUrl The base URL of the custom OpenAI-compatible API (without trailing slash)
 * @property chatCompletionsPath The path for chat completions endpoint (default: "chat/completions", NO leading slash)
 * @property timeoutConfig Configuration for connection timeouts
 */
class CustomOpenAIClientSettings(
    baseUrl: String,
    chatCompletionsPath: String = "chat/completions",
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig()
) : OpenAIBaseSettings(baseUrl, chatCompletionsPath, timeoutConfig)

/**
 * Request model for custom OpenAI-compatible chat completion
 */
@Serializable
data class CustomOpenAIChatCompletionRequest(
    val messages: List<OpenAIMessage>,
    val model: String,
    val frequencyPenalty: Double? = null,
    val logprobs: Boolean? = null,
    val maxTokens: Int? = null,
    val presencePenalty: Double? = null,
    val responseFormat: ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat? = null,
    val stop: List<String>? = null,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val toolChoice: OpenAIToolChoice? = null,
    val tools: List<OpenAITool>? = null,
    val topLogprobs: Int? = null,
    val topP: Double? = null
)

/**
 * Response model for custom OpenAI-compatible chat completion
 */
@Serializable
data class CustomOpenAIChatCompletionResponse(
    override val id: String,
    val `object`: String? = null, // Optional: Some OpenAI-compatible APIs (like GLM) may not include this
    override val created: Long = 0L, // Optional: GitHub Copilot API may not include this
    override val model: String = "", // Optional: GitHub Copilot API may not include this
    val choices: List<Choice>,
    val usage: ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage? = null
) : ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse {
    @Serializable
    data class Choice(
        val index: Int,
        val message: OpenAIMessage.Assistant,
        val finishReason: String? = null
    )
}

/**
 * Streaming response model for custom OpenAI-compatible chat completion
 */
@Serializable
data class CustomOpenAIChatCompletionStreamResponse(
    override val id: String,
    val `object`: String? = null, // Optional: Some OpenAI-compatible APIs (like GLM) may not include this
    override val created: Long = 0L, // Optional: GitHub Copilot API may not include this
    override val model: String = "", // Optional: GitHub Copilot API may not include this
    val choices: List<StreamChoice>,
    val usage: ai.koog.prompt.executor.clients.openai.base.models.OpenAIUsage? = null
) : ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMStreamResponse {
    @Serializable
    data class StreamChoice(
        val index: Int,
        val delta: Delta,
        val finishReason: String? = null
    )

    @Serializable
    data class Delta(
        val role: String? = null,
        val content: String? = null,
        val toolCalls: List<ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall>? = null
    )
}

/**
 * Implementation of [LLMClient] for custom OpenAI-compatible APIs.
 * This client can be used with any OpenAI-compatible API like GLM, custom endpoints, etc.
 *
 * **IMPORTANT URL Construction in Ktor**:
 * - When using `defaultRequest { url(baseUrl) }` and then `post(path)`:
 *   - If `path` starts with `/`, Ktor treats it as absolute and DISCARDS the baseUrl path
 *   - If `path` does NOT start with `/`, Ktor appends it to baseUrl
 * - Example:
 *   - baseUrl = "https://api.example.com/v1", path = "/chat/completions"
 *   - Result: https://api.example.com/chat/completions (WRONG - lost /v1)
 *   - baseUrl = "https://api.example.com/v1", path = "chat/completions"
 *   - Result: https://api.example.com/v1/chat/completions (CORRECT)
 *
 * @param apiKey The API key for the custom API
 * @param baseUrl The base URL of the custom API (e.g., "https://open.bigmodel.cn/api/paas/v4", without trailing slash)
 * @param chatCompletionsPath The path for chat completions (default: "chat/completions", NO leading slash)
 * @param customHeaders Custom HTTP headers to include in requests
 * @param timeoutConfig Configuration for connection timeouts
 * @param baseClient Optional custom HTTP client
 * @param clock Clock instance for tracking timestamps
 */
@OptIn(ExperimentalTime::class)
class CustomOpenAILLMClient(
    apiKey: String,
    baseUrl: String,
    chatCompletionsPath: String = "chat/completions",
    private val customHeaders: Map<String, String> = emptyMap(),
    timeoutConfig: ConnectionTimeoutConfig = ConnectionTimeoutConfig(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System
) : AbstractOpenAILLMClient<CustomOpenAIChatCompletionResponse, CustomOpenAIChatCompletionStreamResponse>(
    apiKey,
    CustomOpenAIClientSettings(baseUrl, chatCompletionsPath, timeoutConfig),
    baseClient.config {
        // Add custom headers to all requests
        if (customHeaders.isNotEmpty()) {
            install(io.ktor.client.plugins.DefaultRequest) {
                customHeaders.forEach { (key, value) ->
                    headers.append(key, value)
                }
            }
        }
    },
    "custom-openai",
    clock,
    staticLogger,
    OpenAICompatibleToolDescriptorSchemaGenerator()
) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

    }

    override fun llmProvider(): LLMProvider = LLMProvider.OpenAI // OpenAI-compatible provider

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val responseFormat = createResponseFormat(params.schema, model)
        
        // Only include toolChoice when tools are actually provided
        // GitHub Copilot API requires tools when tool_choice is specified
        val effectiveTools = tools?.takeIf { it.isNotEmpty() }
        val effectiveToolChoice = if (effectiveTools != null) toolChoice else null

        val request = CustomOpenAIChatCompletionRequest(
            messages = messages,
            model = model.id,
            frequencyPenalty = null,
            logprobs = null,
            maxTokens = null,
            presencePenalty = null,
            responseFormat = responseFormat,
            stop = null,
            stream = stream,
            temperature = params.temperature,
            toolChoice = effectiveToolChoice,
            tools = effectiveTools,
            topLogprobs = null,
            topP = null
        )

        return json.encodeToString(request)
    }

    override fun processProviderChatResponse(response: CustomOpenAIChatCompletionResponse): List<List<Message.Response>> {
        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): CustomOpenAIChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): CustomOpenAIChatCompletionResponse =
        json.decodeFromString(data)

    override fun processStreamingResponse(
        response: Flow<CustomOpenAIChatCompletionStreamResponse>
    ): Flow<StreamFrame> = flow {
        response.collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.content?.let { emit(StreamFrame.TextDelta(it)) }
                choice.delta.toolCalls?.forEach { toolCall ->
                    emit(
                        StreamFrame.ToolCallDelta(
                            id = toolCall.id,
                            name = toolCall.function.name,
                            content = toolCall.function.arguments
                        )
                    )
                }
                choice.finishReason?.let { emit(StreamFrame.End(it, createMetaInfo(chunk.usage))) }
            }
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by custom OpenAI-compatible APIs" }
        throw UnsupportedOperationException("Moderation is not supported by custom OpenAI-compatible APIs.")
    }
}
