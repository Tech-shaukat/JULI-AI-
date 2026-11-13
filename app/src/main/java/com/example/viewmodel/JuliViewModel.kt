package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.JuliGeminiTextClient
import com.example.ai.JuliLiveManager
import com.example.ai.JuliToolExecutor
import com.example.ai.LivePhase
import com.example.ai.LiveUiState
import com.example.data.local.JuliDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.Note
import com.example.data.model.TaskItem
import com.example.data.model.UserMemory
import com.example.data.repository.JuliRepository
import android.content.Intent
import com.example.intents.MobileActionDispatcher
import com.example.intents.PendingAction
import com.example.service.WakeWordEvent
import com.example.service.WakeWordService
import com.example.speech.JuliSpeechManager
import com.example.speech.JuliTtsManager
import com.example.ui.components.OrbState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavigationTab {
    ASSISTANT,
    CHAT,
    PRODUCTIVITY,
    ACTIONS,
    SETTINGS
}

class JuliViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "JuliViewModel"
    }

    private val database = JuliDatabase.getDatabase(application)
    private val repository = JuliRepository(
        database.noteDao(),
        database.chatDao(),
        database.taskDao(),
        database.memoryDao()
    )

    val chatMessages: StateFlow<List<ChatMessage>> = repository.allMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notes: StateFlow<List<Note>> = repository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val tasks: StateFlow<List<TaskItem>> = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val memories: StateFlow<List<UserMemory>> = repository.allMemory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val isWakeWordActive: StateFlow<Boolean> = WakeWordService.isServiceRunning

    private val _currentTab = MutableStateFlow(NavigationTab.ASSISTANT)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _assistantState = MutableStateFlow(OrbState.IDLE)
    val assistantState: StateFlow<OrbState> = _assistantState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _liveTranscription = MutableStateFlow("")
    val liveTranscription: StateFlow<String> = _liveTranscription.asStateFlow()

    private val _language = MutableStateFlow("hi") // "hi", "en", "bho", "auto"
    val language: StateFlow<String> = _language.asStateFlow()

    private val _speechRate = MutableStateFlow(0.95f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(1.08f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _statusText = MutableStateFlow("Gemini Live Ready • बात करने के लिए दबाएँ")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    private val _lastActionBadge = MutableStateFlow<String?>(null)
    val lastActionBadge: StateFlow<String?> = _lastActionBadge.asStateFlow()

    private lateinit var liveManager: JuliLiveManager

    private val toolExecutor by lazy {
        JuliToolExecutor(
            context = getApplication(),
            repository = repository,
            onNavigateTab = { tab ->
                _currentTab.value = when (tab.lowercase()) {
                    "chat" -> NavigationTab.CHAT
                    "planner", "notes", "tasks" -> NavigationTab.PRODUCTIVITY
                    "actions" -> NavigationTab.ACTIONS
                    "settings" -> NavigationTab.SETTINGS
                    else -> NavigationTab.ASSISTANT
                }
            },
            onToolBadge = { badge ->
                _lastActionBadge.value = badge
                if (::liveManager.isInitialized) {
                    liveManager.onToolExecutedBadge(badge)
                }
            },
            onRequestPendingAction = { action ->
                _pendingAction.value = action
            }
        )
    }

    private val geminiTextClient by lazy { JuliGeminiTextClient(toolExecutor) }

    val liveUiState: StateFlow<LiveUiState>
        get() = liveManager.state

    private var speechManager: JuliSpeechManager? = null
    private var ttsManager: JuliTtsManager? = null

    init {
        liveManager = JuliLiveManager(repository, toolExecutor)
        initSpeechAndTts(application)
        observeWakeWordEvents()
        observeLiveSessionState()
    }

    private fun observeLiveSessionState() {
        viewModelScope.launch {
            liveManager.state.collect { live ->
                when (live.phase) {
                    LivePhase.DISCONNECTED -> {
                        if (speechManager == null || _assistantState.value != OrbState.THINKING) {
                            if (_assistantState.value != OrbState.SPEAKING) {
                                _assistantState.value = OrbState.IDLE
                                _statusText.value = if (isWakeWordActive.value) {
                                    "Listening for 'Hey JULI'..."
                                } else {
                                    "Gemini Live Ready • टैप करें"
                                }
                            }
                        }
                    }
                    LivePhase.CONNECTING -> {
                        _assistantState.value = OrbState.THINKING
                        _statusText.value = "Gemini Live जुड़ रहा है..."
                    }
                    LivePhase.LISTENING -> {
                        _assistantState.value = OrbState.LISTENING
                        _statusText.value = "Gemini Live • सुन रही हूँ, बोलिए..."
                        if (live.userCaption.isNotBlank()) {
                            _liveTranscription.value = live.userCaption
                        }
                    }
                    LivePhase.THINKING -> {
                        _assistantState.value = OrbState.THINKING
                        _statusText.value = "Gemini Live • कार्य कर रही हूँ..."
                    }
                    LivePhase.SPEAKING -> {
                        _assistantState.value = OrbState.SPEAKING
                        _statusText.value = "Gemini Live • बोल रही हूँ..."
                        _liveTranscription.value = ""
                    }
                    LivePhase.ERROR -> {
                        _assistantState.value = OrbState.ERROR
                        _statusText.value = live.errorMessage ?: "Gemini Live त्रुटि"
                        if (!live.errorMessage.isNullOrBlank()) {
                            _errorMessage.value = live.errorMessage
                        }
                        if (isWakeWordActive.value) {
                            WakeWordService.resume(getApplication())
                        }
                    }
                }
            }
        }
    }

    private fun initSpeechAndTts(context: Context) {
        ttsManager = JuliTtsManager(
            context = context,
            onInitComplete = { success ->
                if (success) {
                    ttsManager?.setSpeechParams(_speechRate.value, _pitch.value)
                }
            },
            onSpeakingStarted = {
                if (!liveManager.state.value.isConnected) {
                    _assistantState.value = OrbState.SPEAKING
                    _statusText.value = "Speaking • बोल रही हूँ..."
                }
            },
            onSpeakingDone = {
                if (!liveManager.state.value.isConnected && _assistantState.value == OrbState.SPEAKING) {
                    _assistantState.value = OrbState.IDLE
                    _statusText.value = if (isWakeWordActive.value) {
                        "Listening for 'Hey JULI'..."
                    } else {
                        "Gemini Live Ready • तैयार हूँ"
                    }
                    if (isWakeWordActive.value) {
                        WakeWordService.resume(getApplication())
                    }
                }
            },
            onError = { err ->
                _errorMessage.value = err
                _assistantState.value = OrbState.IDLE
                if (isWakeWordActive.value) {
                    WakeWordService.resume(getApplication())
                }
            }
        )

        speechManager = JuliSpeechManager(
            context = context,
            onStateChange = { state ->
                if (liveManager.state.value.isConnected) return@JuliSpeechManager
                when (state) {
                    is JuliSpeechManager.SpeechState.Listening -> {
                        _assistantState.value = OrbState.LISTENING
                        _statusText.value = "Listening • सुन रही हूँ..."
                    }
                    is JuliSpeechManager.SpeechState.Processing -> {
                        _assistantState.value = OrbState.THINKING
                        _statusText.value = "Gemini AI समझ रही है..."
                    }
                    is JuliSpeechManager.SpeechState.Idle -> {
                        if (_assistantState.value == OrbState.LISTENING) {
                            _assistantState.value = OrbState.IDLE
                            _statusText.value = if (isWakeWordActive.value) {
                                "Listening for 'Hey JULI'..."
                            } else {
                                "Gemini Live Ready"
                            }
                        }
                    }
                }
            },
            onPartialText = { partial ->
                _liveTranscription.value = partial
            },
            onFinalText = { final ->
                _liveTranscription.value = ""
                processUserQuery(final)
            },
            onRmsLevel = { rms ->
                _rmsLevel.value = rms
            },
            onError = { error ->
                _errorMessage.value = error
                _assistantState.value = OrbState.IDLE
                _statusText.value = if (isWakeWordActive.value) "Listening for 'Hey JULI'..." else "Ready"
                _liveTranscription.value = ""
                if (isWakeWordActive.value) {
                    WakeWordService.resume(getApplication())
                }
            }
        )
    }

    private fun observeWakeWordEvents() {
        viewModelScope.launch {
            WakeWordService.wakeWordEvents.collect { event ->
                when (event) {
                    is WakeWordEvent.WakeWordTriggered -> {
                        _assistantState.value = OrbState.LISTENING
                        _statusText.value = "Hey JULI detected! Listening..."
                    }
                    is WakeWordEvent.CommandRecognized -> {
                        _assistantState.value = OrbState.THINKING
                        _statusText.value = "Gemini AI: \"${event.command}\"..."
                    }
                    is WakeWordEvent.AssistantSpoke -> {
                        _assistantState.value = OrbState.SPEAKING
                        _statusText.value = "JULI: \"${event.response}\""
                    }
                    is WakeWordEvent.PendingActionRequested -> {
                        _pendingAction.value = event.action
                    }
                }
            }
        }
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun toggleWakeWordService(context: Context, hasRecordPermission: Boolean, onRequestPermission: () -> Unit) {
        if (!hasRecordPermission) {
            onRequestPermission()
            return
        }

        if (isWakeWordActive.value) {
            WakeWordService.stop(context)
            _statusText.value = "Wake Word Service Stopped"
        } else {
            viewModelScope.launch {
                if (liveManager.state.value.isConnected) {
                    liveManager.stopSession()
                }
                WakeWordService.start(context)
                _statusText.value = "Listening for 'Hey JULI' in background..."
            }
        }
    }

    /**
     * Primary Gemini Live bidirectional audio-to-audio + task execution toggle.
     */
    fun toggleGeminiLive(hasRecordPermission: Boolean, onRequestPermission: () -> Unit) {
        if (!hasRecordPermission) {
            onRequestPermission()
            return
        }

        viewModelScope.launch {
            val currentLive = liveManager.state.value
            if (currentLive.isConnected || currentLive.phase == LivePhase.CONNECTING) {
                liveManager.stopSession()
                _assistantState.value = OrbState.IDLE
                _statusText.value = if (isWakeWordActive.value) {
                    WakeWordService.resume(getApplication())
                    "Listening for 'Hey JULI'..."
                } else {
                    "Gemini Live Ready • टैप करें"
                }
            } else {
                ttsManager?.stop()
                speechManager?.stopListening()
                _liveTranscription.value = ""
                _lastActionBadge.value = null

                if (isWakeWordActive.value) {
                    WakeWordService.pause(getApplication())
                    delay(250)
                }
                liveManager.startSession(_language.value)
            }
        }
    }

    /**
     * Single-shot speech recognizer -> Gemini 3.8 Flash + TTS (useful as a quick command option).
     */
    fun onQuickVoiceClicked(hasRecordPermission: Boolean, onRequestPermission: () -> Unit) {
        if (!hasRecordPermission) {
            onRequestPermission()
            return
        }

        if (liveManager.state.value.isConnected || liveManager.state.value.phase == LivePhase.CONNECTING) {
            toggleGeminiLive(hasRecordPermission, onRequestPermission)
            return
        }

        if (_assistantState.value == OrbState.SPEAKING) {
            stopSpeaking()
            return
        }

        if (_assistantState.value == OrbState.LISTENING) {
            speechManager?.stopListening()
            _assistantState.value = OrbState.IDLE
            _statusText.value = "Ready"
            if (isWakeWordActive.value) {
                WakeWordService.resume(getApplication())
            }
        } else {
            _liveTranscription.value = ""
            _assistantState.value = OrbState.LISTENING
            _statusText.value = "Listening • बोलिए..."
            viewModelScope.launch {
                if (isWakeWordActive.value) {
                    WakeWordService.pause(getApplication())
                    delay(220)
                }
                speechManager?.startListening(_language.value)
            }
        }
    }

    fun stopSpeaking() {
        viewModelScope.launch {
            if (liveManager.state.value.isConnected || liveManager.state.value.phase == LivePhase.CONNECTING) {
                liveManager.stopSession()
            }
            ttsManager?.stop()
            _assistantState.value = OrbState.IDLE
            _statusText.value = "Gemini Live Ready"
            if (isWakeWordActive.value) {
                WakeWordService.resume(getApplication())
            }
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
    }

    fun setLiveVoice(voiceName: String) {
        liveManager.setSelectedVoice(voiceName)
    }

    fun setSpeechParams(rate: Float, pitch: Float) {
        _speechRate.value = rate
        _pitch.value = pitch
        ttsManager?.setSpeechParams(rate, pitch)
    }

    fun clearError() {
        _errorMessage.value = null
        liveManager.clearError()
    }

    fun setPendingAction(action: PendingAction?) {
        _pendingAction.value = action
    }

    fun handleIntentExtras(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra(WakeWordService.EXTRA_PENDING_ACTION_TYPE) ?: return
        val recipient = intent.getStringExtra(WakeWordService.EXTRA_PENDING_RECIPIENT).orEmpty()
        val phone = intent.getStringExtra(WakeWordService.EXTRA_PENDING_PHONE).orEmpty()
        val msg = intent.getStringExtra(WakeWordService.EXTRA_PENDING_MESSAGE).orEmpty()

        intent.removeExtra(WakeWordService.EXTRA_PENDING_ACTION_TYPE)
        _pendingAction.value = when (type) {
            "call" -> PendingAction.MakeCall(recipientName = recipient, phoneNumber = phone)
            "sms" -> PendingAction.SendSms(recipientName = recipient, phoneNumber = phone, message = msg)
            "whatsapp" -> PendingAction.SendWhatsApp(recipientName = recipient, phoneNumber = phone, message = msg)
            else -> null
        }
    }

    fun executePendingAction(context: Context, overrideAction: PendingAction? = null) {
        val action = overrideAction ?: _pendingAction.value ?: return
        _pendingAction.value = null

        when (action) {
            is PendingAction.DeleteMemory -> {
                viewModelScope.launch {
                    repository.deleteMemory(action.key)
                }
            }

            is PendingAction.ClearAllData -> {
                viewModelScope.launch {
                    repository.clearChatHistory()
                    repository.clearAllMemory()
                    speakText("All stored data and history have been cleared.", isHindi = false)
                }
            }

            is PendingAction.MakeCall -> {
                val ok = MobileActionDispatcher.openDialer(context, action.phoneNumber)
                val target = action.recipientName.ifBlank { action.phoneNumber }
                if (ok) {
                    _lastActionBadge.value = "Dialer: $target"
                    viewModelScope.launch {
                        repository.addMessage(
                            text = "Phone Dialer opened for $target (${action.phoneNumber}).",
                            isUser = false,
                            actionBadge = "Phone Call"
                        )
                    }
                } else {
                    _errorMessage.value = "Phone Dialer इस डिवाइस पर खोला नहीं जा सका।"
                }
            }

            is PendingAction.SendSms -> {
                val ok = MobileActionDispatcher.sendSms(context, action.phoneNumber, action.message)
                val target = action.recipientName.ifBlank { action.phoneNumber }
                if (ok) {
                    _lastActionBadge.value = "SMS: $target"
                    viewModelScope.launch {
                        repository.addMessage(
                            text = "SMS app opened for $target: \"${action.message}\"",
                            isUser = false,
                            actionBadge = "SMS"
                        )
                    }
                } else {
                    _errorMessage.value = "SMS ऐप इस डिवाइस पर खोला नहीं जा सका।"
                }
            }

            is PendingAction.SendWhatsApp -> {
                val ok = MobileActionDispatcher.sendWhatsApp(context, action.phoneNumber, action.message)
                val target = action.recipientName.ifBlank { action.phoneNumber.ifBlank { "WhatsApp" } }
                if (ok) {
                    _lastActionBadge.value = "WhatsApp: $target"
                    viewModelScope.launch {
                        repository.addMessage(
                            text = "WhatsApp opened for $target: \"${action.message}\"",
                            isUser = false,
                            actionBadge = "WhatsApp"
                        )
                    }
                } else {
                    _errorMessage.value = "WhatsApp इस डिवाइस पर इंस्टॉल या उपलब्ध नहीं है।"
                }
            }
        }
    }

    fun processUserQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            // Immediate local silence request
            val lower = trimmed.lowercase()
            if (lower in listOf("stop", "chup", "chup raho", "stop speaking", "shant", "band karo")) {
                stopSpeaking()
                return@launch
            }

            // If Gemini Live audio session is active, send right into the live WebSocket session
            if (liveManager.state.value.isConnected) {
                liveManager.sendTextTurn(trimmed)
                return@launch
            }

            val historySnapshot = chatMessages.value
            repository.addMessage(text = trimmed, isUser = true, language = _language.value)

            _assistantState.value = OrbState.THINKING
            _statusText.value = "Gemini AI सोच रही है..."

            try {
                val reply = geminiTextClient.processQuery(
                    query = trimmed,
                    languageCode = _language.value,
                    recentMessages = historySnapshot
                )

                val badge = reply.executedBadges.lastOrNull() ?: "Gemini AI"
                _lastActionBadge.value = badge
                repository.addMessage(
                    text = reply.text,
                    isUser = false,
                    actionBadge = badge,
                    language = if (reply.isHindi) "hi" else "en"
                )
                speakText(reply.text, reply.isHindi)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini query timed out", e)
                val msg = "Gemini AI उत्तर देने में समय अधिक ले रहा है (Timeout)। कृपया फिर से प्रयास करें।"
                _assistantState.value = OrbState.ERROR
                _statusText.value = "Request Timed Out"
                _errorMessage.value = msg
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini query failed", e)
                val errDetail = e.localizedMessage ?: "Gemini AI connection failed."
                _assistantState.value = OrbState.ERROR
                _statusText.value = "Gemini AI Error"
                _errorMessage.value = errDetail
            }
        }
    }

    fun speakText(text: String, isHindi: Boolean) {
        _assistantState.value = OrbState.SPEAKING
        _statusText.value = "Speaking • बोल रही हूँ..."
        ttsManager?.speak(text, isHindi)
    }

    fun saveManualNote(title: String, content: String) {
        viewModelScope.launch {
            repository.saveNote(title, content)
            val msg = "Note saved: $title"
            repository.addMessage(text = msg, isUser = false, actionBadge = "Manual Note")
            speakText("नोट सुरक्षित कर लिया गया है।", isHindi = true)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun saveManualTask(title: String, priority: String) {
        viewModelScope.launch {
            repository.saveTask(title, priority)
            speakText("टास्क जोड़ दिया गया है।", isHindi = true)
        }
    }

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(task)
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch {
            repository.deleteMemory(key)
        }
    }

    fun clearAllData() {
        _pendingAction.value = PendingAction.ClearAllData
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
        ttsManager?.shutdown()
    }
}
