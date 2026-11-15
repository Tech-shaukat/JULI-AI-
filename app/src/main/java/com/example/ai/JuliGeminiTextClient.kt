package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class GeminiTextReply(
    val text: String,
    val executedBadges: List<String>,
    val isHindi: Boolean
)

class JuliGeminiTextClient(
    private val context: Context,
    private val toolExecutor: JuliToolExecutor
) {
    companion object {
        private const val TAG = "JuliGeminiTextClient"
        private const val TEXT_MODEL_NAME = "gemini-3.8-flash"
    }

    private val aiClient by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }

    private fun buildModel(languageCode: String) = aiClient.generativeModel(
        modelName = TEXT_MODEL_NAME,
        tools = listOf(JuliTools.tool),
        systemInstruction = JuliTools.buildSystemInstruction(languageCode)
    )

    suspend fun processQuery(
        query: String,
        languageCode: String,
        recentMessages: List<ChatMessage> = emptyList()
    ): GeminiTextReply {
        if (!ensureFirebaseInitialized(context)) {
            throw IllegalStateException(
                "Firebase config (google-services.json) इस APK में मौजूद नहीं है। कृपया GitHub repo में app/google-services.json जोड़कर APK फिर से बिल्ड करें।"
            )
        }

        var lastError: Exception? = null
        for (attempt in 0..1) {
            try {
                return withTimeout(60_000L) {
                    val model = buildModel(languageCode)
                    val history: List<Content> = recentMessages
                        .takeLast(8)
                        .filter { it.text.isNotBlank() }
                        .map { msg ->
                            content(role = if (msg.isUser) "user" else "model") {
                                text(msg.text)
                            }
                        }

                    val chat = model.startChat(history = history)
                    var response = chat.sendMessage(query)
                    val badges = mutableListOf<String>()
                    var hops = 0

                    while (response.functionCalls.isNotEmpty() && hops < 4) {
                        hops++
                        val responses = response.functionCalls.map { call ->
                            val executed = toolExecutor.execute(call)
                            badges.add(executed.badge)
                            executed.responsePart
                        }
                        val toolResponseContent = content(role = "function") {
                            responses.forEach { part(it) }
                        }
                        response = chat.sendMessage(toolResponseContent)
                    }

                    val replyText = response.text?.trim().orEmpty().ifEmpty {
                        if (badges.isNotEmpty()) {
                            "हो गया! (${badges.joinToString(", ")})"
                        } else {
                            throw IllegalStateException("Gemini returned an empty response.")
                        }
                    }

                    val hasDevanagari = replyText.any { it.code in 0x0900..0x097F }
                    val isHindiReply = hasDevanagari || languageCode == "hi" || languageCode == "bho"

                    GeminiTextReply(
                        text = replyText,
                        executedBadges = badges,
                        isHindi = isHindiReply
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini request timed out", e)
                throw e
            } catch (e: CancellationException) {
                if (isChannelClosedError(e) && attempt == 0) {
                    Log.w(TAG, "Transient channel closure on Gemini text call, retrying once", e)
                    lastError = IllegalStateException(e)
                    kotlinx.coroutines.delay(250)
                    continue
                }
                if (isChannelClosedError(e)) {
                    throw IllegalStateException(
                        "नेटवर्क कनेक्शन बीच में बंद हो गया (Channel closed)। कृपया इंटरनेट जाँचकर दोबारा कोशिश करें।",
                        e
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini text inference failed (attempt=${attempt + 1})", e)
                lastError = e
                val raw = e.localizedMessage ?: "Gemini AI connection failed."
                if (isChannelClosedError(e) && attempt == 0) {
                    kotlinx.coroutines.delay(250)
                    continue
                }
                if (raw.contains("App Check", ignoreCase = true) || raw.contains("403")) {
                    val diag = AppCheckDiagnostics.status.value
                    throw IllegalStateException(
                        "$raw [${diag.providerName}: ${diag.diagnosticMessage}]",
                        e
                    )
                }
                if (isChannelClosedError(e)) {
                    throw IllegalStateException(
                        "नेटवर्क कनेक्शन बीच में बंद हो गया (Channel closed)। कृपया इंटरनेट जाँचकर दोबारा कोशिश करें।",
                        e
                    )
                }
                throw e
            }
        }
        throw (lastError ?: IllegalStateException("Gemini AI connection failed."))
    }

    private fun isChannelClosedError(t: Throwable?): Boolean {
        return JuliLiveManager.isChannelClosedThrowable(t)
    }
}
