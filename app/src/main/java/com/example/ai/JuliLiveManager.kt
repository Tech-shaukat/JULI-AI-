package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.repository.JuliRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Transcription
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    companion object {
        private const val TAG = "JuliLiveManager"
        private const val LIVE_MODEL_NAME = "gemini-3.1-flash-live-preview"

        val femaleVoices = listOf(
            LiveVoiceOption("Sulafat", "Sulafat", "Warm & Sweet Female"),
            LiveVoiceOption("Leda", "Leda", "Youthful Female"),
            LiveVoiceOption("Aoede", "Aoede", "Breezy Female"),
            LiveVoiceOption("Despina", "Despina", "Smooth Female"),
            LiveVoiceOption("Vindemiatrix", "Vindemiatrix", "Gentle Female"),
            LiveVoiceOption("Kore", "Kore", "Clear & Firm Female")
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val aiClient by lazy { Firebase.ai(backend = GenerativeBackend.googleAI()) }

    private var liveSession: LiveSession? = null
    private var flushModelJob: Job? = null

    private val userTranscriptBuffer = StringBuilder()
    private val modelTranscriptBuffer = StringBuilder()
    private var currentLanguage = "hi"

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private fun buildLiveModel(voiceName: String, languageCode: String): LiveGenerativeModel {
        return aiClient.liveModel(
            modelName = LIVE_MODEL_NAME,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice(voiceName))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            tools = listOf(JuliTools.tool),
            systemInstruction = JuliTools.buildSystemInstruction(languageCode)
        )
    }

    fun setSelectedVoice(voiceName: String) {
        _state.value = _state.value.copy(selectedVoice = voiceName)
    }

    suspend fun startSession(languageCode: String) {
        if (_state.value.phase == LivePhase.CONNECTING || _state.value.isConnected) {
            return
        }

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
            val model = buildLiveModel(_state.value.selectedVoice, languageCode)
            val session = withTimeout(30_000L) {
                model.connect()
            }
            liveSession = session

            val config = liveAudioConversationConfig {
                enableInterruptions = true
                functionCallHandler = { callPart ->
                    _state.value = _state.value.copy(phase = LivePhase.THINKING)
                    toolExecutor.executeBlocking(callPart)
                }
                transcriptHandler = { input: Transcription?, output: Transcription? ->
                    handleTranscription(input, output)
                }
                goAwayHandler = { goAway ->
                    Log.w(TAG, "Gemini Live GoAway received, timeLeft=${goAway.timeLeft}")
                    scope.launch {
                        stopSession(reason = "Live session limit reached. Tap Live to reconnect.")
                    }
                }
            }

            session.startAudioConversation(config)
            _state.value = _state.value.copy(
                phase = LivePhase.LISTENING,
                errorMessage = null
            )
            Log.i(TAG, "Gemini Live audio conversation started with voice=${_state.value.selectedVoice}")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini Live connection timed out", e)
            cleanupSession()
            _state.value = _state.value.copy(
                phase = LivePhase.ERROR,
                errorMessage = "Gemini Live connection timed out. Please check your internet and try again."
            )
        } catch (e: CancellationException) {
            cleanupSession()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Gemini Live session", e)
            cleanupSession()
            _state.value = _state.value.copy(
                phase = LivePhase.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to connect to Gemini Live."
            )
        }
    }

    private fun handleTranscription(input: Transcription?, output: Transcription?) {
        val inputChunk = input?.text
        if (!inputChunk.isNullOrEmpty()) {
            // If model was speaking before, finalize its previous message turn
            flushModelBuffer()
            userTranscriptBuffer.append(inputChunk)
            _state.value = _state.value.copy(
                phase = LivePhase.LISTENING,
                userCaption = userTranscriptBuffer.toString().trim()
            )
        }

        val outputChunk = output?.text
        if (!outputChunk.isNullOrEmpty()) {
            // User finished speaking and model is replying; persist user turn once
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
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text to live session", e)
            _state.value = _state.value.copy(
                phase = LivePhase.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to send message to Gemini Live."
            )
            false
        }
    }

    suspend fun stopSession(reason: String? = null) {
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

    private suspend fun cleanupSession() {
        val session = liveSession
        liveSession = null
        if (session != null) {
            try {
                session.stopAudioConversation()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping audio conversation", e)
            }
            try {
                if (!session.isClosed()) {
                    session.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing LiveSession", e)
            }
        }
    }
}
