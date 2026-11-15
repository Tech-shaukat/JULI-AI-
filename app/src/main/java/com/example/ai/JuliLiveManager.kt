package com.example.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.repository.JuliRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Transcription
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class LivePhase {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

data class LiveVoiceOption(
    val id: String,
    val label: String,
    val style: String
)

data class LiveUiState(
    val phase: LivePhase = LivePhase.DISCONNECTED,
    val userCaption: String = "",
    val modelCaption: String = "",
    val lastActionBadge: String? = null,
    val errorMessage: String? = null,
    val selectedVoice: String = "Sulafat"
) {
    val isConnected: Boolean
        get() = phase == LivePhase.LISTENING || phase == LivePhase.THINKING || phase == LivePhase.SPEAKING
}

internal data class LiveServerCloseDiagnostics(
    val httpCode: Int? = null,
    val closeCode: Int? = null,
    val closeReason: String? = null,
    val serverMessage: String? = null,
    val appCheckIssue: String? = null,
    val networkError: String? = null
) {
    fun compactSummary(): String {
        val parts = mutableListOf<String>()
        if (httpCode != null) parts.add("HTTP $httpCode")
        if (closeCode != null) parts.add("WS $closeCode")
        val detailText = closeReason?.takeIf { it.isNotBlank() }
            ?: serverMessage?.takeIf { it.isNotBlank() }
            ?: appCheckIssue?.takeIf { it.isNotBlank() }
            ?: networkError?.takeIf { it.isNotBlank() }
        if (detailText != null) parts.add(detailText.take(160))
        return parts.joinToString(": ")
    }

    fun combinedSearchText(): String = listOfNotNull(
        closeReason,
        serverMessage,
        appCheckIssue,
        networkError
    ).joinToString(" ")
}

@OptIn(PublicPreviewAPI::class)
class JuliLiveManager(
    private val context: Context,
    private val repository: JuliRepository,
    private val toolExecutor: JuliToolExecutor
) {
    private data class LiveAttemptConfig(
        val modelName: String,
        val includeTools: Boolean,
        val includeTranscription: Boolean,
        val includeSpeechConfig: Boolean,
        val includeSystemInstruction: Boolean = true
    )

    companion object {
        private const val TAG = "JuliLiveManager"
        private const val PRIMARY_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val SECONDARY_LIVE_MODEL = "gemini-3.1-flash-live-preview"
        private const val TERTIARY_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-09-2025"

        val femaleVoices = listOf(
            LiveVoiceOption("Sulafat", "Sulafat", "Warm & Sweet Female"),
            LiveVoiceOption("Leda", "Leda", "Youthful Female"),
            LiveVoiceOption("Aoede", "Aoede", "Breezy Female"),
            LiveVoiceOption("Despina", "Despina", "Smooth Female"),
            LiveVoiceOption("Vindemiatrix", "Vindemiatrix", "Gentle Female"),
            LiveVoiceOption("Kore", "Kore", "Clear & Firm Female")
        )

        internal fun isChannelClosedThrowable(t: Throwable?): Boolean {
            if (t == null) return false
            val visited = HashSet<Throwable>()
            val queue = ArrayDeque<Pair<Throwable, Int>>()
            queue.add(t to 0)
            while (queue.isNotEmpty()) {
                val (cur, depth) = queue.removeFirst()
                if (depth > 10 || !visited.add(cur)) continue
                val msg = cur.message.orEmpty()
                if (cur is ClosedReceiveChannelException ||
                    cur is ClosedSendChannelException ||
                    cur is ServiceConnectionHandshakeFailedException ||
                    msg.contains("Channel was closed", ignoreCase = true) ||
                    msg.contains("closed by the server", ignoreCase = true) ||
                    msg.contains("closed by the user", ignoreCase = true) ||
                    (msg.contains("WebSocket", ignoreCase = true) && msg.contains("closed", ignoreCase = true))
                ) {
                    return true
                }
                cur.cause?.let { queue.add(it to depth + 1) }
                cur.suppressedExceptions.forEach { sup ->
                    queue.add(sup to depth + 1)
                }
            }
            return false
        }

        internal fun formatLiveErrorMessage(
            e: Throwable,
            diagnostics: LiveServerCloseDiagnostics? = null
        ): String {
            val raw = e.localizedMessage ?: e.message ?: "Failed to connect to Gemini Live."
            val appCheckDiag = AppCheckDiagnostics.status.value
            val diagSummary = diagnostics?.compactSummary().orEmpty()
            val combinedDiag = "${diagnostics?.combinedSearchText().orEmpty()} $raw"

            if (raw.contains("App Check", ignoreCase = true) ||
                combinedDiag.contains("app check", ignoreCase = true) ||
                combinedDiag.contains("appcheck", ignoreCase = true) ||
                combinedDiag.contains("attestation", ignoreCase = true) ||
                (diagnostics?.appCheckIssue != null &&
                    (diagnostics.httpCode == 403 || diagnostics.closeCode == 1008 || diagnostics.closeCode == null))
            ) {
                val reasonDetail = diagSummary.ifBlank { "${appCheckDiag.providerName}: ${appCheckDiag.diagnosticMessage}" }
                return "Gemini Live App Check त्रुटि के कारण बंद हुआ ($reasonDetail)। Settings -> App Check में Refresh Token करें, या Quick Mic का उपयोग करें।"
            }

            if (raw.contains("403") ||
                diagnostics?.httpCode == 403 ||
                combinedDiag.contains("API_KEY", ignoreCase = true) ||
                combinedDiag.contains("PERMISSION_DENIED", ignoreCase = true) ||
                combinedDiag.contains("not authorized", ignoreCase = true) ||
                combinedDiag.contains("blocked", ignoreCase = true)
            ) {
                val reasonDetail = diagSummary.ifBlank { "${appCheckDiag.providerName}: ${appCheckDiag.diagnosticMessage}" }
                return "Gemini Live सर्वर अनुमति त्रुटि ($reasonDetail)। कृपया API Key / Firebase AI Logic और App Check सेटअप जाँचें, या Quick Mic का उपयोग करें।"
            }

            if (diagnostics?.httpCode == 429 ||
                combinedDiag.contains("quota", ignoreCase = true) ||
                combinedDiag.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                combinedDiag.contains("rate limit", ignoreCase = true) ||
                combinedDiag.contains("billing", ignoreCase = true)
            ) {
                val reasonDetail = diagSummary.ifBlank { "Quota / Billing limit" }
                return "Gemini Live की कोटा/बिलिंग सीमा पूरी हो गई है ($reasonDetail)। कुछ देर बाद प्रयास करें या Quick Mic का उपयोग करें।"
            }

            if (combinedDiag.contains("UnknownHostException", ignoreCase = true) ||
                combinedDiag.contains("Unable to resolve host", ignoreCase = true) ||
                combinedDiag.contains("SSLHandshakeException", ignoreCase = true) ||
                combinedDiag.contains("ConnectException", ignoreCase = true) ||
                combinedDiag.contains("SocketTimeoutException", ignoreCase = true)
            ) {
                val reasonDetail = diagSummary.ifBlank { raw.take(120) }
                return "इंटरनेट / DNS कनेक्शन में समस्या ($reasonDetail)। नेटवर्क जाँचकर दोबारा Live दबाएँ, या Quick Mic का उपयोग करें।"
            }

            if (isChannelClosedThrowable(e)) {
                val detailSuffix = when {
                    diagSummary.isNotBlank() -> " [$diagSummary]"
                    !appCheckDiag.tokenReady -> " [${appCheckDiag.providerName}: ${appCheckDiag.diagnosticMessage}]"
                    else -> ""
                }
                return "Gemini Live सर्वर कनेक्शन बंद हो गया (Channel closed)$detailSuffix। कृपया इंटरनेट जाँचकर दोबारा Live दबाएँ, या Quick Mic का उपयोग करें।"
            }
            return if (diagSummary.isNotBlank()) "$raw [$diagSummary]" else raw
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val aiClient by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }
    private val sessionMutex = Mutex()

    @Volatile
    private var liveSession: LiveSession? = null
    @Volatile
    private var activeSessionScope: CoroutineScope? = null
    @Volatile
    private var asyncSessionError: Throwable? = null
    @Volatile
    private var isStoppingManually = false
    private var flushModelJob: Job? = null

    private val userTranscriptBuffer = StringBuilder()
    private val modelTranscriptBuffer = StringBuilder()
    private var currentLanguage = "hi"

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private fun buildLiveModel(
        attempt: LiveAttemptConfig,
        voiceName: String,
        languageCode: String
    ): LiveGenerativeModel {
        return aiClient.liveModel(
            modelName = attempt.modelName,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                if (attempt.includeSpeechConfig) {
                    speechConfig = SpeechConfig(voice = Voice(voiceName))
                }
                if (attempt.includeTranscription) {
                    inputAudioTranscription = AudioTranscriptionConfig()
                    outputAudioTranscription = AudioTranscriptionConfig()
                }
            },
            tools = if (attempt.includeTools) listOf(JuliTools.tool) else emptyList(),
            systemInstruction = if (attempt.includeSystemInstruction) {
                JuliTools.buildSystemInstruction(languageCode)
            } else {
                null
            }
        )
    }

    fun setSelectedVoice(voiceName: String) {
        _state.value = _state.value.copy(selectedVoice = voiceName)
    }

    suspend fun startSession(languageCode: String) {
        sessionMutex.withLock {
            if (_state.value.phase == LivePhase.CONNECTING || _state.value.isConnected) {
                return
            }

            isStoppingManually = false
            asyncSessionError = null
            currentLanguage = languageCode
            userTranscriptBuffer.clear()
            modelTranscriptBuffer.clear()

            _state.value = _state.value.copy(
                phase = LivePhase.CONNECTING,
                userCaption = "",
                modelCaption = "",
                errorMessage = null
            )

            try {
                if (!ensureFirebaseInitialized(context)) {
                    throw IllegalStateException(
                        "Firebase config (google-services.json) इस APK में मौजूद नहीं है। कृपया GitHub repo में app/google-services.json जोड़कर APK फिर से बिल्ड करें।"
                    )
                }

                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasMic) {
                    throw IllegalStateException("माइक्रोफ़ोन की अनुमति (RECORD_AUDIO) आवश्यक है।")
                }

                cleanupSession()
                val appCheckPreflightIssue = awaitAppCheckTokenIfNeeded()

                val attempts = listOf(
                    LiveAttemptConfig(
                        PRIMARY_LIVE_MODEL,
                        includeTools = true,
                        includeTranscription = true,
                        includeSpeechConfig = true,
                        includeSystemInstruction = true
                    ),
                    LiveAttemptConfig(
                        SECONDARY_LIVE_MODEL,
                        includeTools = true,
                        includeTranscription = true,
                        includeSpeechConfig = true,
                        includeSystemInstruction = true
                    ),
                    LiveAttemptConfig(
                        TERTIARY_LIVE_MODEL,
                        includeTools = true,
                        includeTranscription = true,
                        includeSpeechConfig = true,
                        includeSystemInstruction = true
                    ),
                    LiveAttemptConfig(
                        PRIMARY_LIVE_MODEL,
                        includeTools = true,
                        includeTranscription = false,
                        includeSpeechConfig = true,
                        includeSystemInstruction = false
                    ),
                    LiveAttemptConfig(
                        PRIMARY_LIVE_MODEL,
                        includeTools = false,
                        includeTranscription = false,
                        includeSpeechConfig = false,
                        includeSystemInstruction = false
                    ),
                    LiveAttemptConfig(
                        SECONDARY_LIVE_MODEL,
                        includeTools = false,
                        includeTranscription = false,
                        includeSpeechConfig = false,
                        includeSystemInstruction = false
                    )
                )

                var lastError: Throwable? = null
                var capturedCloseDiag: LiveServerCloseDiagnostics? = null
                var connected = false
                var connectedAttempt: LiveAttemptConfig? = null

                for ((index, attempt) in attempts.withIndex()) {
                    if (isStoppingManually) break
                    try {
                        connectAndStartAudio(
                            attempt = attempt,
                            voiceName = _state.value.selectedVoice,
                            languageCode = languageCode
                        )
                        connected = true
                        connectedAttempt = attempt
                        Log.i(
                            TAG,
                            "Gemini Live started with model=${attempt.modelName}, tools=${attempt.includeTools}, sysInst=${attempt.includeSystemInstruction}, voice=${_state.value.selectedVoice}"
                        )
                        break
                    } catch (e: CancellationException) {
                        if (isStoppingManually || isChannelClosedThrowable(e)) {
                            if (!isStoppingManually && capturedCloseDiag == null) {
                                capturedCloseDiag = extractSessionCloseDiagnostics(liveSession)
                            }
                            cleanupSession()
                            if (isStoppingManually) break
                            lastError = e
                        } else {
                            throw e
                        }
                    } catch (t: Throwable) {
                        Log.w(
                            TAG,
                            "Live attempt ${index + 1} (${attempt.modelName}, tools=${attempt.includeTools}, sysInst=${attempt.includeSystemInstruction}) failed: ${t.message}",
                            t
                        )
                        if (!isStoppingManually && capturedCloseDiag == null) {
                            capturedCloseDiag = extractSessionCloseDiagnostics(liveSession)
                        }
                        cleanupSession()
                        lastError = t
                        val msg = t.message.orEmpty()
                        val retryable = !isStoppingManually && (
                            isChannelClosedThrowable(t) ||
                                msg.contains("not found", ignoreCase = true) ||
                                msg.contains("unsupported", ignoreCase = true) ||
                                msg.contains("invalid argument", ignoreCase = true)
                            )
                        if (!retryable) {
                            throw (t as? Exception ?: IllegalStateException(t.message, t))
                        }
                        delay(250)
                    }
                }

                if (isStoppingManually) {
                    cleanupSession()
                    _state.value = _state.value.copy(
                        phase = LivePhase.DISCONNECTED,
                        userCaption = "",
                        modelCaption = "",
                        errorMessage = null
                    )
                    return
                }

                if (!connected) {
                    val finalErr = lastError ?: IllegalStateException("Failed to connect to Gemini Live.")
                    val closeDiag = if (isChannelClosedThrowable(finalErr)) {
                        capturedCloseDiag ?: probeBidiWebSocketFailure(appCheckPreflightIssue)
                    } else {
                        capturedCloseDiag
                    }
                    val friendly = formatLiveErrorMessage(finalErr, closeDiag)
                    Log.e(TAG, "All Gemini Live attempts failed. diag=${closeDiag?.compactSummary()}", finalErr)
                    _state.value = _state.value.copy(
                        phase = LivePhase.ERROR,
                        errorMessage = friendly
                    )
                    return
                }

                val badgeHint = if (connectedAttempt != null && !connectedAttempt.includeTools) {
                    "Live Voice Only"
                } else {
                    _state.value.lastActionBadge
                }

                _state.value = _state.value.copy(
                    phase = LivePhase.LISTENING,
                    lastActionBadge = badgeHint,
                    errorMessage = null
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini Live connection timed out", e)
                cleanupSession()
                _state.value = _state.value.copy(
                    phase = LivePhase.ERROR,
                    errorMessage = "Gemini Live connection timed out. Please check your internet and try again."
                )
            } catch (e: CancellationException) {
                cleanupSession()
                if (isStoppingManually) {
                    _state.value = _state.value.copy(
                        phase = LivePhase.DISCONNECTED,
                        errorMessage = null
                    )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Gemini Live session", e)
                val closeDiag = extractSessionCloseDiagnostics(liveSession)
                cleanupSession()
                if (isStoppingManually) {
                    _state.value = _state.value.copy(
                        phase = LivePhase.DISCONNECTED,
                        errorMessage = null
                    )
                    return
                }
                val friendly = formatLiveErrorMessage(e, closeDiag)
                _state.value = _state.value.copy(
                    phase = LivePhase.ERROR,
                    errorMessage = friendly
                )
            }
        }
    }

    private suspend fun connectAndStartAudio(
        attempt: LiveAttemptConfig,
        voiceName: String,
        languageCode: String
    ) {
        asyncSessionError = null
        val model = buildLiveModel(
            attempt = attempt,
            voiceName = voiceName,
            languageCode = languageCode
        )
        val session = withTimeout(25_000L) {
            model.connect()
        }
        if (isStoppingManually) {
            try {
                if (!session.isClosed()) session.close()
            } catch (_: Throwable) {}
            throw CancellationException("Session stopped by user during connect")
        }
        liveSession = session

        val config = liveAudioConversationConfig {
            enableInterruptions = true
            if (attempt.includeTools) {
                functionCallHandler = { callPart ->
                    _state.value = _state.value.copy(phase = LivePhase.THINKING)
                    toolExecutor.executeBlocking(callPart)
                }
            }
            if (attempt.includeTranscription) {
                transcriptHandler = { input: Transcription?, output: Transcription? ->
                    handleTranscription(input, output)
                }
            }
            goAwayHandler = { goAway ->
                Log.w(TAG, "Gemini Live GoAway received, timeLeft=${goAway.timeLeft}")
                scope.launch {
                    stopSession(reason = "Live सेशन की समय-सीमा पूरी हो गई। दोबारा शुरू करने के लिए Live दबाएँ।")
                }
            }
        }

        // LiveSession.startAudioConversation() spawns internal networkScope & audioScope
        // attached to continuation.context[Job]. Wrapping it in a dedicated SupervisorJob +
        // CoroutineExceptionHandler guarantees async WebSocket / AudioRecord failures
        // ("Channel was closed by the server") are caught here instead of crashing viewModelScope.
        val sessionExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            handleAsyncSessionError(session, throwable)
        }
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + sessionExceptionHandler)
        activeSessionScope = sessionScope

        val startedSignal = CompletableDeferred<Result<Unit>>()
        sessionScope.launch {
            try {
                session.startAudioConversation(config)
                startedSignal.complete(Result.success(Unit))
            } catch (t: Throwable) {
                startedSignal.complete(Result.failure(t))
                throw t
            }
        }

        startedSignal.await().getOrThrow()

        // Allow initial audio/WebSocket frames to settle so immediate server rejections
        // trigger the next fallback configuration cleanly inside startSession().
        delay(650L)
        if (isStoppingManually) {
            throw CancellationException("Session stopped by user during audio startup")
        }
        asyncSessionError?.let { err ->
            throw err
        }
        if (session.isClosed()) {
            throw IllegalStateException("Channel was closed by the server during Live audio initialization")
        }
    }

    private fun handleAsyncSessionError(session: LiveSession, throwable: Throwable) {
        if (session !== liveSession) {
            Log.d(TAG, "Ignoring async error from stale LiveSession: ${throwable.message}")
            return
        }
        if (isStoppingManually || (throwable is CancellationException && !isChannelClosedThrowable(throwable))) {
            Log.d(TAG, "LiveSession async coroutine cancelled cleanly: ${throwable.message}")
            return
        }

        Log.w(TAG, "Caught async LiveSession error safely: ${throwable.message}", throwable)
        asyncSessionError = throwable

        // If still inside startSession()'s startup window (CONNECTING), connectAndStartAudio()
        // will inspect asyncSessionError and retry the next fallback configuration.
        if (_state.value.phase == LivePhase.CONNECTING) {
            return
        }

        scope.launch {
            flushModelJob?.cancel()
            flushUserBuffer()
            flushModelBuffer()
            val closeDiag = extractSessionCloseDiagnostics(session)
                ?: if (isChannelClosedThrowable(throwable)) probeBidiWebSocketFailure(null) else null
            cleanupSession()
            if (isStoppingManually) {
                _state.value = _state.value.copy(
                    phase = LivePhase.DISCONNECTED,
                    userCaption = "",
                    modelCaption = "",
                    errorMessage = null
                )
            } else {
                _state.value = _state.value.copy(
                    phase = LivePhase.ERROR,
                    userCaption = "",
                    modelCaption = "",
                    errorMessage = formatLiveErrorMessage(throwable, closeDiag)
                )
            }
        }
    }

    private fun handleTranscription(input: Transcription?, output: Transcription?) {
        val inputChunk = input?.text
        if (!inputChunk.isNullOrEmpty()) {
            flushModelBuffer()
            userTranscriptBuffer.append(inputChunk)
            _state.value = _state.value.copy(
                phase = LivePhase.LISTENING,
                userCaption = userTranscriptBuffer.toString().trim()
            )
        }

        val outputChunk = output?.text
        if (!outputChunk.isNullOrEmpty()) {
            flushUserBuffer()
            modelTranscriptBuffer.append(outputChunk)
            _state.value = _state.value.copy(
                phase = LivePhase.SPEAKING,
                modelCaption = modelTranscriptBuffer.toString().trim()
            )
            scheduleModelTurnFlush()
        }
    }

    private fun scheduleModelTurnFlush() {
        flushModelJob?.cancel()
        flushModelJob = scope.launch {
            delay(2_000L)
            flushModelBuffer()
            if (_state.value.isConnected) {
                _state.value = _state.value.copy(phase = LivePhase.LISTENING)
            }
        }
    }

    private fun flushUserBuffer() {
        val text = userTranscriptBuffer.toString().trim()
        if (text.isNotEmpty()) {
            userTranscriptBuffer.clear()
            scope.launch {
                repository.addMessage(
                    text = text,
                    isUser = true,
                    language = currentLanguage
                )
            }
        }
    }

    private fun flushModelBuffer() {
        val text = modelTranscriptBuffer.toString().trim()
        if (text.isNotEmpty()) {
            modelTranscriptBuffer.clear()
            val badge = _state.value.lastActionBadge ?: "Gemini Live"
            scope.launch {
                repository.addMessage(
                    text = text,
                    isUser = false,
                    actionBadge = badge,
                    language = currentLanguage
                )
            }
        }
    }

    fun onToolExecutedBadge(badge: String) {
        _state.value = _state.value.copy(lastActionBadge = badge)
    }

    /**
     * Attempts to send text over the active Live session.
     * Returns false if the session was closed (so the caller can seamlessly fall back to standard text chat).
     */
    suspend fun sendTextTurn(prompt: String): Boolean {
        val session = liveSession
        if (session == null || session.isClosed() || !_state.value.isConnected) {
            return false
        }
        return try {
            _state.value = _state.value.copy(phase = LivePhase.THINKING, userCaption = prompt)
            repository.addMessage(text = prompt, isUser = true, language = currentLanguage)
            session.send(prompt, turnComplete = true)
            true
        } catch (e: CancellationException) {
            if (isChannelClosedThrowable(e) || isStoppingManually) {
                Log.w(TAG, "Live channel closed during sendTextTurn; falling back to text chat", e)
                cleanupSession()
                _state.value = _state.value.copy(phase = LivePhase.DISCONNECTED, errorMessage = null)
                false
            } else {
                throw e
            }
        } catch (e: Exception) {
            if (isChannelClosedThrowable(e)) {
                Log.w(TAG, "Live WebSocket channel closed before sendTextTurn; resetting cleanly", e)
                cleanupSession()
                _state.value = _state.value.copy(phase = LivePhase.DISCONNECTED, errorMessage = null)
                false
            } else {
                Log.e(TAG, "Error sending text to live session", e)
                _state.value = _state.value.copy(
                    phase = LivePhase.ERROR,
                    errorMessage = formatLiveError(e)
                )
                false
            }
        }
    }

    suspend fun stopSession(reason: String? = null) {
        isStoppingManually = reason == null
        flushModelJob?.cancel()
        flushUserBuffer()
        flushModelBuffer()
        cleanupSession()
        _state.value = _state.value.copy(
            phase = if (reason != null) LivePhase.ERROR else LivePhase.DISCONNECTED,
            userCaption = "",
            modelCaption = "",
            errorMessage = reason
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(
            errorMessage = null,
            phase = if (_state.value.phase == LivePhase.ERROR) LivePhase.DISCONNECTED else _state.value.phase
        )
    }

    fun shutdown() {
        isStoppingManually = true
        flushModelJob?.cancel()
        val session = liveSession
        val sessionScope = activeSessionScope
        liveSession = null
        activeSessionScope = null
        try {
            session?.stopAudioConversation()
        } catch (_: Throwable) {}
        sessionScope?.cancel()
        scope.launch {
            try {
                if (session != null && !session.isClosed()) {
                    session.close()
                }
            } catch (_: Throwable) {}
        }
    }

    private suspend fun cleanupSession() {
        val session = liveSession
        val sessionScope = activeSessionScope
        liveSession = null
        activeSessionScope = null
        if (session != null) {
            try {
                session.stopAudioConversation()
            } catch (e: Throwable) {
                Log.w(TAG, "Ignoring error stopping audio conversation: ${e.message}")
            }
            try {
                if (!session.isClosed()) {
                    session.close()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Ignoring error closing LiveSession: ${e.message}")
            }
        }
        try {
            sessionScope?.cancel()
        } catch (e: Throwable) {
            Log.w(TAG, "Ignoring error cancelling session scope: ${e.message}")
        }
    }

    private suspend fun awaitAppCheckTokenIfNeeded(): String? {
        if (AppCheckDiagnostics.status.value.tokenReady) return null
        val (_, issue) = fetchAppCheckTokenAndIssue(timeoutMs = 2_200L)
        return issue
    }

    private suspend fun fetchAppCheckTokenAndIssue(timeoutMs: Long): Pair<String, String?> {
        val currentDiag = AppCheckDiagnostics.status.value
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Pair<String, String?>> { cont ->
                try {
                    FirebaseAppCheck.getInstance().getAppCheckToken(false)
                        .addOnCompleteListener { task ->
                            if (!cont.isActive) return@addOnCompleteListener
                            if (task.isSuccessful) {
                                val token = task.result?.token.orEmpty()
                                val isPlaceholder = token.isBlank() || token.startsWith("eyJlcnJvciI")
                                if (isPlaceholder) {
                                    val msg = "App Check placeholder/error token (${currentDiag.providerName})"
                                    AppCheckDiagnostics.update(
                                        currentDiag.copy(tokenReady = false, diagnosticMessage = msg)
                                    )
                                    cont.resume(token to msg)
                                } else {
                                    AppCheckDiagnostics.update(
                                        currentDiag.copy(
                                            tokenReady = true,
                                            diagnosticMessage = "Valid token active (${currentDiag.providerName})"
                                        )
                                    )
                                    cont.resume(token to null)
                                }
                            } else {
                                val err = task.exception?.localizedMessage ?: "App Check token request failed"
                                AppCheckDiagnostics.update(
                                    currentDiag.copy(tokenReady = false, diagnosticMessage = err)
                                )
                                cont.resume("" to err)
                            }
                        }
                } catch (t: Throwable) {
                    if (cont.isActive) {
                        cont.resume("" to (t.localizedMessage ?: "App Check unavailable"))
                    }
                }
            }
        }
        return result ?: ("" to currentDiag.diagnosticMessage.takeIf { !currentDiag.tokenReady })
    }

    private suspend fun extractSessionCloseDiagnostics(session: LiveSession?): LiveServerCloseDiagnostics? {
        if (session == null) return null
        return try {
            val sessionField = session.javaClass.getDeclaredField("session").apply {
                isAccessible = true
            }
            val ktorSession = sessionField.get(session) ?: return null
            val closeReasonMethod = ktorSession.javaClass.methods.firstOrNull {
                it.name == "getCloseReason" && it.parameterCount == 0
            } ?: return null
            val deferred = closeReasonMethod.invoke(ktorSession) as? kotlinx.coroutines.Deferred<*> ?: return null
            val closeReasonObj = withTimeoutOrNull(250L) { deferred.await() } ?: return null
            val codeMethod = closeReasonObj.javaClass.methods.firstOrNull {
                it.name == "getCode" && it.parameterCount == 0
            }
            val messageMethod = closeReasonObj.javaClass.methods.firstOrNull {
                it.name == "getMessage" && it.parameterCount == 0
            }
            val code = (codeMethod?.invoke(closeReasonObj) as? Number)?.toInt()
            val message = (messageMethod?.invoke(closeReasonObj) as? String)?.trim()
            if (code == null && message.isNullOrBlank()) {
                null
            } else {
                LiveServerCloseDiagnostics(closeCode = code, closeReason = message)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Could not inspect Ktor closeReason reflectively: ${t.message}")
            null
        }
    }

    private suspend fun probeBidiWebSocketFailure(preflightAppCheckIssue: String?): LiveServerCloseDiagnostics? {
        val options = try {
            FirebaseApp.getInstance().options
        } catch (t: Throwable) {
            return LiveServerCloseDiagnostics(
                appCheckIssue = preflightAppCheckIssue,
                networkError = "FirebaseApp unavailable: ${t.message}"
            )
        }
        val apiKey = options.apiKey.orEmpty()
        if (apiKey.isBlank()) {
            return LiveServerCloseDiagnostics(
                appCheckIssue = preflightAppCheckIssue,
                networkError = "Missing Firebase API key in google-services.json"
            )
        }

        val (appCheckToken, tokenIssue) = fetchAppCheckTokenAndIssue(timeoutMs = 1_500L)
        val combinedAppCheckIssue = tokenIssue ?: preflightAppCheckIssue
        val certSha1 = getSigningCertSha1Hex()
        val projectId = options.projectId
        val wsUrl =
            "wss://firebasevertexai.googleapis.com/ws/google.firebase.vertexai.v1beta.GenerativeService/BidiGenerateContent?key=$apiKey"

        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()

        var webSocket: WebSocket? = null
        return try {
            withTimeoutOrNull(3_600L) {
                suspendCancellableCoroutine { cont ->
                    var capturedServerMsg: String? = null
                    val requestBuilder = Request.Builder()
                        .url(wsUrl)
                        .header("X-Android-Package", context.packageName)
                        .header("X-Firebase-AppId", options.applicationId)
                    if (!certSha1.isNullOrBlank()) {
                        requestBuilder.header("X-Android-Cert", certSha1)
                    }
                    if (appCheckToken.isNotBlank()) {
                        requestBuilder.header("X-Firebase-AppCheck", appCheckToken)
                    }

                    webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
                        override fun onOpen(ws: WebSocket, response: Response) {
                            val modelPath = if (!projectId.isNullOrBlank()) {
                                "projects/$projectId/models/$PRIMARY_LIVE_MODEL"
                            } else {
                                "models/$PRIMARY_LIVE_MODEL"
                            }
                            val setupJson = JSONObject().apply {
                                put("setup", JSONObject().apply {
                                    put("model", modelPath)
                                    put("generationConfig", JSONObject().apply {
                                        put("responseModalities", JSONArray().put("AUDIO"))
                                    })
                                })
                            }.toString()
                            ws.send(setupJson)
                        }

                        override fun onMessage(ws: WebSocket, text: String) {
                            capturedServerMsg = parseBidiServerSnippet(text)
                            if (text.contains("\"error\"", ignoreCase = true) ||
                                text.contains("\"setupComplete\"", ignoreCase = true)
                            ) {
                                ws.close(1000, "diagnostic_done")
                                if (cont.isActive) {
                                    cont.resume(
                                        LiveServerCloseDiagnostics(
                                            serverMessage = capturedServerMsg,
                                            appCheckIssue = combinedAppCheckIssue
                                        )
                                    )
                                }
                            }
                        }

                        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                            if (cont.isActive) {
                                cont.resume(
                                    LiveServerCloseDiagnostics(
                                        closeCode = code,
                                        closeReason = reason.trim().takeIf { it.isNotEmpty() },
                                        serverMessage = capturedServerMsg,
                                        appCheckIssue = combinedAppCheckIssue
                                    )
                                )
                            }
                        }

                        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                            if (cont.isActive) {
                                cont.resume(
                                    LiveServerCloseDiagnostics(
                                        closeCode = code,
                                        closeReason = reason.trim().takeIf { it.isNotEmpty() },
                                        serverMessage = capturedServerMsg,
                                        appCheckIssue = combinedAppCheckIssue
                                    )
                                )
                            }
                        }

                        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                            val httpCode = response?.code
                            val bodySnippet = try {
                                response?.body?.string()?.let { parseBidiServerSnippet(it) }
                            } catch (_: Throwable) {
                                null
                            }
                            if (cont.isActive) {
                                cont.resume(
                                    LiveServerCloseDiagnostics(
                                        httpCode = httpCode,
                                        serverMessage = bodySnippet ?: capturedServerMsg,
                                        appCheckIssue = combinedAppCheckIssue,
                                        networkError = "${t.javaClass.simpleName}: ${t.message}"
                                    )
                                )
                            }
                        }
                    })
                }
            } ?: LiveServerCloseDiagnostics(
                appCheckIssue = combinedAppCheckIssue,
                networkError = "Diagnostic probe timed out"
            )
        } catch (t: Throwable) {
            Log.d(TAG, "Bidi WebSocket probe error: ${t.message}")
            LiveServerCloseDiagnostics(
                appCheckIssue = combinedAppCheckIssue,
                networkError = t.message
            )
        } finally {
            try {
                webSocket?.cancel()
            } catch (_: Throwable) {}
            try {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            } catch (_: Throwable) {}
        }
    }

    private fun parseBidiServerSnippet(text: String): String {
        val trimmed = text.trim()
        if (trimmed.contains("\"setupComplete\"")) {
            return "Minimal Live setup accepted; full config rejected"
        }
        return try {
            val json = JSONObject(trimmed)
            val errObj = json.optJSONObject("error")
            if (errObj != null) {
                val status = errObj.optString("status")
                val msg = errObj.optString("message")
                listOf(status, msg).filter { it.isNotBlank() }.joinToString(": ").ifBlank { trimmed.take(180) }
            } else {
                trimmed.take(180)
            }
        } catch (_: Throwable) {
            trimmed.take(180)
        }
    }

    private fun getSigningCertSha1Hex(): String? {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString("") { "%02X".format(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatLiveError(e: Throwable): String = formatLiveErrorMessage(e)
}
