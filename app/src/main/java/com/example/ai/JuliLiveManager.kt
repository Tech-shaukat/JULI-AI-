package com.example.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.repository.JuliRepository
import com.google.firebase.Firebase
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

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
        val includeSpeechConfig: Boolean
    )

    companion object {
        private const val TAG = "JuliLiveManager"
        private const val PRIMARY_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val SECONDARY_LIVE_MODEL = "gemini-3.1-flash-live-preview"

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

        internal fun formatLiveErrorMessage(e: Throwable): String {
            val raw = e.localizedMessage ?: e.message ?: "Failed to connect to Gemini Live."
            if (raw.contains("App Check", ignoreCase = true) || raw.contains("403")) {
                val diag = AppCheckDiagnostics.status.value
                return "$raw [${diag.providerName}: ${diag.diagnosticMessage}]"
            }
            if (isChannelClosedThrowable(e)) {
                return "Gemini Live सर्वर कनेक्शन बंद हो गया (Channel closed)। कृपया इंटरनेट जाँचकर दोबारा Live दबाएँ, या Quick Mic का उपयोग करें।"
            }
            return raw
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
            systemInstruction = JuliTools.buildSystemInstruction(languageCode)
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

                val attempts = listOf(
                    LiveAttemptConfig(PRIMARY_LIVE_MODEL, includeTools = true, includeTranscription = true, includeSpeechConfig = true),
                    LiveAttemptConfig(SECONDARY_LIVE_MODEL, includeTools = true, includeTranscription = true, includeSpeechConfig = true),
                    LiveAttemptConfig(PRIMARY_LIVE_MODEL, includeTools = true, includeTranscription = false, includeSpeechConfig = true),
                    LiveAttemptConfig(PRIMARY_LIVE_MODEL, includeTools = false, includeTranscription = false, includeSpeechConfig = false)
                )

                var lastError: Throwable? = null
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
                            "Gemini Live started with model=${attempt.modelName}, tools=${attempt.includeTools}, voice=${_state.value.selectedVoice}"
                        )
                        break
                    } catch (e: CancellationException) {
                        if (isStoppingManually || isChannelClosedThrowable(e)) {
                            cleanupSession()
                            if (isStoppingManually) break
                            lastError = e
                        } else {
                            throw e
                        }
                    } catch (t: Throwable) {
                        Log.w(
                            TAG,
                            "Live attempt ${index + 1} (${attempt.modelName}, tools=${attempt.includeTools}) failed: ${t.message}",
                            t
                        )
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
                        delay(350)
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
                    throw (finalErr as? Exception ?: IllegalStateException(finalErr.message, finalErr))
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
                cleanupSession()
                if (isStoppingManually) {
                    _state.value = _state.value.copy(
                        phase = LivePhase.DISCONNECTED,
                        errorMessage = null
                    )
                    return
                }
                val friendly = formatLiveError(e)
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
                    errorMessage = formatLiveError(throwable)
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

    private fun formatLiveError(e: Throwable): String = formatLiveErrorMessage(e)
}
