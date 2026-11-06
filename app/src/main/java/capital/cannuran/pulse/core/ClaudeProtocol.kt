package capital.cannuran.pulse.core

/**
 * Pure request/response handling for the Anthropic Messages API with the server-side
 * web search tool. The transport (HTTP) lives in the Android layer; everything here is testable.
 */
object ClaudeProtocol {

    const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    const val API_VERSION = "2023-06-01"
    const val WEB_SEARCH_TOOL = "web_search_20250305"
    const val MAX_TOKENS = 3000
    /** Server tools may stop with pause_turn on long searches; we resume at most this many times. */
    const val MAX_CONTINUATIONS = 3

    fun headers(apiKey: String): Map<String, String> = linkedMapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to API_VERSION,
        "content-type" to "application/json",
    )

    /**
     * @param messages the conversation so far: the user context, plus any assistant
     *   content echoed back verbatim after a pause_turn.
     */
    fun requestBody(model: ClaudeModel, system: String, messages: List<Map<String, Any?>>, maxSearches: Int): String =
        Json.stringify(linkedMapOf(
            "model" to model.id,
            "max_tokens" to MAX_TOKENS,
            // The system prompt is stable between refreshes, so cache it (cheap reads on 5.5 models).
            "system" to listOf(linkedMapOf(
                "type" to "text", "text" to system,
                "cache_control" to linkedMapOf("type" to "ephemeral"),
            )),
            "messages" to messages,
            "tools" to listOf(linkedMapOf(
                "type" to WEB_SEARCH_TOOL,
                "name" to "web_search",
                "max_uses" to maxSearches,
                "user_location" to linkedMapOf(
                    "type" to "approximate", "country" to "US", "timezone" to "America/New_York",
                ),
            )),
        ))

    fun userMessage(text: String): Map<String, Any?> = linkedMapOf("role" to "user", "content" to text)

    data class Response(
        val stopReason: String,
        /** Raw content blocks, echoed back as the assistant turn when resuming after pause_turn. */
        val content: List<Any?>,
        /** Concatenated text of all text blocks after the last web search result. */
        val finalText: String,
        /** URLs Claude cited, in order, for cross-checking headline links. */
        val citedUrls: List<String>,
        val usage: Usage,
        val searchErrors: List<String>,
    )

    fun parseResponse(body: String): Response {
        val m = Json.parseObject(body)
        if (m.str("type") == "error") throw ApiException(0, errorMessage(m))
        val content = m.arr("content")
        val blocks = content.objects()
        val lastResultIdx = blocks.indexOfLast { it.str("type") == "web_search_tool_result" }
        val finalText = blocks.drop(lastResultIdx + 1)
            .filter { it.str("type") == "text" }
            .joinToString("") { it.str("text").orEmpty() }
        val cited = blocks.filter { it.str("type") == "text" }
            .flatMap { it.arr("citations").objects() }
            .mapNotNull { it.str("url") }
            .distinct()
        val searchErrors = blocks.filter { it.str("type") == "web_search_tool_result" }
            .mapNotNull { b -> (b["content"] as? Map<*, *>)?.let { (it["error_code"] ?: "unknown").toString() } }
        val u = m.obj("usage")
        val usage = Usage(
            inputTokens = u?.long("input_tokens") ?: 0,
            outputTokens = u?.long("output_tokens") ?: 0,
            cacheReadTokens = u?.long("cache_read_input_tokens") ?: 0,
            cacheWriteTokens = u?.long("cache_creation_input_tokens") ?: 0,
            webSearches = u?.obj("server_tool_use")?.long("web_search_requests") ?: 0,
        )
        return Response(m.str("stop_reason").orEmpty(), content, finalText, cited, usage, searchErrors)
    }

    class ApiException(val status: Int, message: String) : RuntimeException(message)

    fun errorMessage(m: Map<String, Any?>): String {
        val err = m.obj("error")
        val type = err?.str("type").orEmpty()
        val msg = err?.str("message").orEmpty()
        return listOf(type, msg).filter { it.isNotEmpty() }.joinToString(": ").ifEmpty { "Unknown API error" }
    }

    /** Human-readable explanation for an HTTP failure, shown on the widget footer and in settings. */
    fun describeHttpError(status: Int, body: String?): String {
        val detail = try { body?.let { errorMessage(Json.parseObject(it)) } } catch (e: RuntimeException) { null }
        if (detail?.contains("credit balance", true) == true) {
            // Billing, not a bug: the key's organization has no prepaid API credit.
            return "Out of API credits — add credits in Claude Console → Billing, then tap Refresh"
        }
        val hint = when (status) {
            400 -> if (detail?.contains("web search", true) == true || detail?.contains("web_search", true) == true)
                "Enable web search for your org in the Claude Console" else "Bad request"
            401 -> "API key rejected — check it in Settings"
            403 -> "API key lacks permission for this model or tool"
            404 -> "Model not available to this key"
            413 -> "Request too large"
            429 -> "Rate limited — will retry next cycle"
            500, 502, 503 -> "Anthropic API error — will retry"
            529 -> "Anthropic API overloaded — will retry"
            else -> "HTTP $status"
        }
        return if (detail.isNullOrBlank()) hint else "$hint ($detail)".take(240)
    }

    fun isRetryable(status: Int): Boolean = status == 429 || status >= 500
}
