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
import android.graphics.Bitmap
import com.example.ai.JuliImageStudioClient
import com.example.ai.JuliVideoStudioClient
import com.example.brain.BrainAction
import com.example.brain.JuliBrain
import com.example.intents.ContactResolver
import com.example.intents.JuliAssistantPrefs
import com.example.intents.MobileActionDispatcher
import com.example.intents.PendingAction
import com.example.service.CapturedNotification
import com.example.service.NotificationCenterStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class NavigationTab {
    ASSISTANT,
    CHAT,
    PRODUCTIVITY,
    ACTIONS,
    SETTINGS
}

enum class ActionsSubTab {
    CALLS,
    MAPS,
    ALERTS,
    STUDIO
}

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data object Loading : ImageStudioUiState
    data class Success(
        val bitmap: Bitmap,
        val file: File,
        val prompt: String
    ) : ImageStudioUiState
    data class Error(val message: String) : ImageStudioUiState
}

sealed interface VideoStudioUiState {
    data object Idle : VideoStudioUiState
    data object Loading : VideoStudioUiState
    data class Success(
        val file: File,
        val interactionId: String?,
        val aspectRatio: String,
        val summaryText: String?,
        val prompt: String
    ) : VideoStudioUiState
    data class Error(val message: String) : VideoStudioUiState
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

    private val _bannerError = MutableStateFlow<String?>(null)
    val bannerError: StateFlow<String?> = _bannerError.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    private val _lastActionBadge = MutableStateFlow<String?>(null)
    val lastActionBadge: StateFlow<String?> = _lastActionBadge.asStateFlow()

    private val _actionsSubTab = MutableStateFlow(ActionsSubTab.CALLS)
    val actionsSubTab: StateFlow<ActionsSubTab> = _actionsSubTab.asStateFlow()

    private val _mapDestination = MutableStateFlow("")
    val mapDestination: StateFlow<String> = _mapDestination.asStateFlow()

    private val _mapTravelMode = MutableStateFlow("driving")
    val mapTravelMode: StateFlow<String> = _mapTravelMode.asStateFlow()

    private val _directCallEnabled = MutableStateFlow(JuliAssistantPrefs.isDirectCallEnabled(application))
    val directCallEnabled: StateFlow<Boolean> = _directCallEnabled.asStateFlow()

    private val _autoAnnounceNotifs = MutableStateFlow(JuliAssistantPrefs.isAutoAnnounceNotifications(application))
    val autoAnnounceNotifs: StateFlow<Boolean> = _autoAnnounceNotifs.asStateFlow()

    private val _readNotifContent = MutableStateFlow(JuliAssistantPrefs.isReadNotificationContent(application))
    val readNotifContent: StateFlow<Boolean> = _readNotifContent.asStateFlow()

    private val _hasContactsPermission = MutableStateFlow(ContactResolver.hasReadContactsPermission(application))
    val hasContactsPermission: StateFlow<Boolean> = _hasContactsPermission.asStateFlow()

    private val _hasCallPhonePermission = MutableStateFlow(ContactResolver.hasCallPhonePermission(application))
    val hasCallPhonePermission: StateFlow<Boolean> = _hasCallPhonePermission.asStateFlow()

    private val _isNotificationAccessEnabled = MutableStateFlow(NotificationCenterStore.isNotificationAccessEnabled(application))
    val isNotificationAccessEnabled: StateFlow<Boolean> = _isNotificationAccessEnabled.asStateFlow()

    val capturedNotifications: StateFlow<List<CapturedNotification>> = NotificationCenterStore.notifications

    private val _imageStudioState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val imageStudioState: StateFlow<ImageStudioUiState> = _imageStudioState.asStateFlow()

    private val _videoStudioState = MutableStateFlow<VideoStudioUiState>(VideoStudioUiState.Idle)
    val videoStudioState: StateFlow<VideoStudioUiState> = _videoStudioState.asStateFlow()

    private val localBrain = JuliBrain()
    private val imageStudioClient by lazy { JuliImageStudioClient(getApplication()) }
    private val videoStudioClient by lazy { JuliVideoStudioClient(getApplication()) }

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
            },
            onDirectCallRequested = { recipientName, phoneNumber ->
                placeDirectCallAndAnnounce(recipientName, phoneNumber)
            },
            onMapDestinationSet = { destination, travelMode ->
                _mapDestination.value = destination
                _mapTravelMode.value = travelMode
            }
        )
    }

    private val geminiTextClient by lazy { JuliGeminiTextClient(getApplication(), toolExecutor) }

    val liveUiState: StateFlow<LiveUiState>
        get() = liveManager.state

    private var speechManager: JuliSpeechManager? = null
    private var ttsManager: JuliTtsManager? = null

    init {
        liveManager = JuliLiveManager(application, repository, toolExecutor)
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
                        val errMsg = live.errorMessage ?: "Gemini Live त्रुटि"
                        _statusText.value = errMsg
                        _bannerError.value = errMsg
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

    fun selectActionsSubTab(subTab: ActionsSubTab) {
        _actionsSubTab.value = subTab
    }

    fun refreshDevicePermissions() {
        val ctx = getApplication<Application>()
        _hasContactsPermission.value = ContactResolver.hasReadContactsPermission(ctx)
        _hasCallPhonePermission.value = ContactResolver.hasCallPhonePermission(ctx)
        _isNotificationAccessEnabled.value = NotificationCenterStore.isNotificationAccessEnabled(ctx)
    }

    fun setDirectCallEnabled(enabled: Boolean) {
        JuliAssistantPrefs.setDirectCallEnabled(getApplication(), enabled)
        _directCallEnabled.value = enabled
    }

    fun setAutoAnnounceNotifications(enabled: Boolean) {
        JuliAssistantPrefs.setAutoAnnounceNotifications(getApplication(), enabled)
        _autoAnnounceNotifs.value = enabled
    }

    fun setReadNotificationContent(enabled: Boolean) {
        JuliAssistantPrefs.setReadNotificationContent(getApplication(), enabled)
        _readNotifContent.value = enabled
    }

    fun setMapDestination(destination: String, travelMode: String = _mapTravelMode.value) {
        _mapDestination.value = destination
        _mapTravelMode.value = travelMode
    }

    fun launchMapNavigation(context: Context, destination: String, travelMode: String = _mapTravelMode.value) {
        val clean = destination.trim()
        if (clean.isEmpty()) return
        _mapDestination.value = clean
        _mapTravelMode.value = travelMode
        val ok = MobileActionDispatcher.openMapsNavigation(context, clean, travelMode)
        if (ok) {
            _lastActionBadge.value = "Map: $clean"
            speakText("$clean के लिए Google Maps नेविगेशन शुरू कर रही हूँ।", isHindi = true)
        } else {
            _errorMessage.value = "Google Maps इस डिवाइस पर खोला नहीं जा सका।"
        }
    }

    fun searchMapPlace(context: Context, query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        _mapDestination.value = clean
        val ok = MobileActionDispatcher.searchMapsPlace(context, clean)
        if (ok) {
            _lastActionBadge.value = "Place: $clean"
        } else {
            _errorMessage.value = "Google Maps इस डिवाइस पर खोला नहीं जा सका।"
        }
    }

    fun readNotificationAloud(item: CapturedNotification) {
        val spoken = if (item.message.isNotBlank()) {
            "${item.appName} से ${item.sender} का संदेश: ${item.message}"
        } else {
            "${item.appName} से ${item.sender} का नोटिफिकेशन आया है।"
        }
        speakText(spoken, isHindi = true)
    }

    fun readRecentNotifications(appFilter: String? = null) {
        viewModelScope.launch {
            buildAndSpeakNotificationsSummary(appFilter, isHindi = _language.value != "en")
        }
    }

    fun clearNotifications() {
        NotificationCenterStore.clearAll()
    }

    private suspend fun buildAndSpeakNotificationsSummary(appFilter: String?, isHindi: Boolean) {
        val ctx = getApplication<Application>()
        val accessEnabled = NotificationCenterStore.isNotificationAccessEnabled(ctx)
        _isNotificationAccessEnabled.value = accessEnabled
        if (!accessEnabled) {
            val msg = if (isHindi) {
                "मैसेज और नोटिफिकेशन पढ़ने के लिए कृपया Actions के Alerts टैब में Notification Reader चालू करें।"
            } else {
                "Please enable Notification Reader access in the Actions -> Alerts tab so I can read incoming messages and notifications."
            }
            _currentTab.value = NavigationTab.ACTIONS
            _actionsSubTab.value = ActionsSubTab.ALERTS
            finishLocalFallback(msg, isHindi, "Enable Alerts")
            return
        }

        val items = NotificationCenterStore.filterByApp(appFilter, limit = 5)
        if (items.isEmpty()) {
            val emptyMsg = if (isHindi) {
                "अभी कोई नया नोटिफिकेशन या मैसेज नहीं आया है।"
            } else {
                "There are no recent notifications or messages right now."
            }
            finishLocalFallback(emptyMsg, isHindi, "Alerts (0)")
            return
        }

        val summary = buildString {
            if (isHindi) {
                append("आपके हाल के ${items.size} नोटिफिकेशन हैं। ")
                items.forEachIndexed { idx, item ->
                    append("${idx + 1}: ${item.appName} पर ${item.sender}")
                    if (item.message.isNotBlank()) {
                        append(" ने लिखा है: ${item.message}। ")
                    } else {
                        append(" का नोटिफिकेशन है। ")
                    }
                }
            } else {
                append("You have ${items.size} recent notifications. ")
                items.forEachIndexed { idx, item ->
                    append("${idx + 1}: From ${item.sender} on ${item.appName}")
                    if (item.message.isNotBlank()) {
                        append(": ${item.message}. ")
                    } else {
                        append(". ")
                    }
                }
            }
        }
        finishLocalFallback(summary, isHindi, "Alerts (${items.size})")
    }

    fun callContactOrPrompt(context: Context, query: String) {
        viewModelScope.launch {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                _pendingAction.value = PendingAction.MakeCall()
                return@launch
            }
            val directDigits = MobileActionDispatcher.sanitizePhoneNumber(trimmed)
            val isPhoneNumber = directDigits.count { it.isDigit() } >= 6
            if (isPhoneNumber) {
                if (JuliAssistantPrefs.isDirectCallEnabled(context) && ContactResolver.hasCallPhonePermission(context)) {
                    placeDirectCallAndAnnounce(trimmed, directDigits)
                } else {
                    _pendingAction.value = PendingAction.MakeCall(phoneNumber = directDigits)
                }
                return@launch
            }

            val matches = ContactResolver.findContactsByName(context, trimmed)
            val memPhone = if (matches.isEmpty()) findPhoneInMemory(trimmed) else ""
            when {
                matches.size == 1 && JuliAssistantPrefs.isDirectCallEnabled(context) && ContactResolver.hasCallPhonePermission(context) -> {
                    val m = matches.first()
                    placeDirectCallAndAnnounce(m.displayName, m.phoneNumber)
                }
                matches.isNotEmpty() -> {
                    val m = matches.first()
                    _pendingAction.value = PendingAction.MakeCall(recipientName = m.displayName, phoneNumber = m.phoneNumber)
                }
                memPhone.isNotBlank() && JuliAssistantPrefs.isDirectCallEnabled(context) && ContactResolver.hasCallPhonePermission(context) -> {
                    placeDirectCallAndAnnounce(trimmed, memPhone)
                }
                else -> {
                    _pendingAction.value = PendingAction.MakeCall(recipientName = trimmed, phoneNumber = memPhone)
                }
            }
        }
    }

    private fun placeDirectCallAndAnnounce(recipientName: String, phoneNumber: String) {
        val ctx = getApplication<Application>()
        val target = recipientName.ifBlank { phoneNumber }
        val ok = MobileActionDispatcher.placeDirectCall(ctx, phoneNumber)
        if (ok) {
            _lastActionBadge.value = "Calling: $target"
            viewModelScope.launch {
                repository.addMessage(
                    text = "$target ($phoneNumber) को डायरेक्ट कॉल लगाया गया।",
                    isUser = false,
                    actionBadge = "Direct Call",
                    language = "hi"
                )
            }
            speakText("$target को कॉल लगा रही हूँ।", isHindi = true)
        } else {
            _errorMessage.value = "कॉल शुरू नहीं की जा सकी।"
        }
    }

    private suspend fun findPhoneInMemory(name: String): String {
        val lower = name.trim().lowercase()
        if (lower.isEmpty()) return ""
        return try {
            val memories = repository.allMemory.first()
            val match = memories.firstOrNull {
                it.key.lowercase().contains(lower) || it.value.lowercase().contains(lower)
            }
            val digits = match?.value?.let {
                Regex("(\\+?\\d[\\d\\s-]{7,14}\\d)").find(it)?.value
            }.orEmpty()
            MobileActionDispatcher.sanitizePhoneNumber(digits)
        } catch (_: Exception) {
            ""
        }
    }

    fun generateImage(prompt: String, aspectRatio: String = "1:1") {
        viewModelScope.launch {
            _imageStudioState.value = ImageStudioUiState.Loading
            try {
                val (bitmap, file) = imageStudioClient.generateImage(prompt, aspectRatio)
                _imageStudioState.value = ImageStudioUiState.Success(
                    bitmap = bitmap,
                    file = file,
                    prompt = prompt.trim()
                )
                _lastActionBadge.value = "Image Created"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageStudioState.value = ImageStudioUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun editImage(inputBitmap: Bitmap, editInstruction: String) {
        viewModelScope.launch {
            _imageStudioState.value = ImageStudioUiState.Loading
            try {
                val (bitmap, file) = imageStudioClient.editImage(inputBitmap, editInstruction)
                _imageStudioState.value = ImageStudioUiState.Success(
                    bitmap = bitmap,
                    file = file,
                    prompt = editInstruction.trim()
                )
                _lastActionBadge.value = "Image Edited"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageStudioState.value = ImageStudioUiState.Error(
                    e.localizedMessage ?: "Image editing failed."
                )
            }
        }
    }

    fun shareGeneratedMedia(file: File, mimeType: String, title: String) {
        val ok = imageStudioClient.shareFile(file, mimeType, title)
        if (!ok) {
            _errorMessage.value = "फ़ाइल शेयर नहीं की जा सकी।"
        }
    }

    fun resetImageStudio() {
        _imageStudioState.value = ImageStudioUiState.Idle
    }

    fun generateVideo(prompt: String, aspectRatio: String = "16:9", usePreviousInteraction: Boolean = false) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _videoStudioState.value = VideoStudioUiState.Error("कृपया वीडियो बनाने के लिए प्रॉम्प्ट लिखें।")
            return
        }
        val prevId = if (usePreviousInteraction) {
            (_videoStudioState.value as? VideoStudioUiState.Success)?.interactionId
        } else {
            null
        }
        viewModelScope.launch {
            _videoStudioState.value = VideoStudioUiState.Loading
            try {
                val result = videoStudioClient.generateVideo(
                    prompt = cleanPrompt,
                    aspectRatio = aspectRatio,
                    previousInteractionId = prevId
                )
                _videoStudioState.value = VideoStudioUiState.Success(
                    file = result.videoFile,
                    interactionId = result.interactionId,
                    aspectRatio = result.aspectRatio,
                    summaryText = result.summaryText,
                    prompt = cleanPrompt
                )
                _lastActionBadge.value = "Video Ready"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _videoStudioState.value = VideoStudioUiState.Error(
                    e.localizedMessage ?: "Video generation failed."
                )
            }
        }
    }

    fun hasVideoApiKey(): Boolean = videoStudioClient.hasApiKey()

    fun resetVideoStudio() {
        _videoStudioState.value = VideoStudioUiState.Idle
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
                _bannerError.value = null
                liveManager.clearError()

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
    }

    fun dismissBannerError() {
        _bannerError.value = null
        liveManager.clearError()
        if (_assistantState.value == OrbState.ERROR) {
            _assistantState.value = OrbState.IDLE
            _statusText.value = "Gemini Live Ready • टैप करें"
        }
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
                val useDirectCall = JuliAssistantPrefs.isDirectCallEnabled(context) &&
                    ContactResolver.hasCallPhonePermission(context)
                val ok = if (useDirectCall) {
                    MobileActionDispatcher.placeDirectCall(context, action.phoneNumber)
                } else {
                    MobileActionDispatcher.openDialer(context, action.phoneNumber)
                }
                val target = action.recipientName.ifBlank { action.phoneNumber }
                if (ok) {
                    _lastActionBadge.value = if (useDirectCall) "Calling: $target" else "Dialer: $target"
                    viewModelScope.launch {
                        repository.addMessage(
                            text = if (useDirectCall) {
                                "Direct call placed to $target (${action.phoneNumber})."
                            } else {
                                "Phone Dialer opened for $target (${action.phoneNumber})."
                            },
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

            _bannerError.value = null
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
                val msg = "Gemini AI उत्तर देने में समय अधिक ले रहा है (Timeout)। कृपया इंटरनेट जाँचें।"
                if (!tryLocalDeviceActionFallback(trimmed)) {
                    reportVisibleError(msg, "Timeout")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini query failed", e)
                val errDetail = e.localizedMessage ?: "Gemini AI connection failed."
                if (!tryLocalDeviceActionFallback(trimmed)) {
                    reportVisibleError("Gemini AI त्रुटि: $errDetail", "Error")
                }
            }
        }
    }

    private suspend fun reportVisibleError(message: String, badge: String) {
        _assistantState.value = OrbState.ERROR
        _statusText.value = message
        _bannerError.value = message
        _errorMessage.value = message
        repository.addMessage(
            text = message,
            isUser = false,
            actionBadge = badge,
            language = "hi"
        )
    }

    private suspend fun tryLocalDeviceActionFallback(query: String): Boolean {
        val context: Context = getApplication()
        return when (val action = localBrain.processQuery(query, _language.value)) {
            is BrainAction.OpenYouTube -> {
                MobileActionDispatcher.openYouTube(context, action.query)
                finishLocalFallback(action.speakResponse, action.isHindi, "YouTube")
                true
            }
            is BrainAction.OpenChrome -> {
                MobileActionDispatcher.openChrome(context, action.queryOrUrl)
                finishLocalFallback(action.speakResponse, action.isHindi, "Browser")
                true
            }
            is BrainAction.OpenCamera -> {
                MobileActionDispatcher.openCamera(context)
                finishLocalFallback(action.speakResponse, action.isHindi, "Camera")
                true
            }
            is BrainAction.OpenSettings -> {
                MobileActionDispatcher.openSettings(context, action.settingType)
                finishLocalFallback(action.speakResponse, action.isHindi, "Settings")
                true
            }
            is BrainAction.CallPhone -> {
                var resolvedName = action.recipientName
                var resolvedPhone = MobileActionDispatcher.sanitizePhoneNumber(action.phoneNumber)
                var matchCount = 0
                if (resolvedPhone.isBlank() && resolvedName.isNotBlank()) {
                    val matches = ContactResolver.findContactsByName(context, resolvedName)
                    matchCount = matches.size
                    if (matches.isNotEmpty()) {
                        resolvedName = matches.first().displayName
                        resolvedPhone = matches.first().phoneNumber
                    } else {
                        resolvedPhone = findPhoneInMemory(resolvedName)
                    }
                }
                val canDirectCall = resolvedPhone.isNotBlank() &&
                    JuliAssistantPrefs.isDirectCallEnabled(context) &&
                    ContactResolver.hasCallPhonePermission(context) &&
                    matchCount <= 1

                if (canDirectCall) {
                    placeDirectCallAndAnnounce(resolvedName, resolvedPhone)
                } else {
                    _pendingAction.value = PendingAction.MakeCall(resolvedName, resolvedPhone)
                    finishLocalFallback(action.speakResponse, action.isHindi, "Confirm Call")
                }
                true
            }
            is BrainAction.SendSms -> {
                _pendingAction.value = PendingAction.SendSms(action.recipientName, action.phoneNumber, action.message)
                finishLocalFallback(action.speakResponse, action.isHindi, "Confirm SMS")
                true
            }
            is BrainAction.SendWhatsApp -> {
                _pendingAction.value = PendingAction.SendWhatsApp(action.recipientName, action.phoneNumber, action.message)
                finishLocalFallback(action.speakResponse, action.isHindi, "Confirm WhatsApp")
                true
            }
            is BrainAction.NavigateMap -> {
                _mapDestination.value = action.destination
                _mapTravelMode.value = action.travelMode
                _currentTab.value = NavigationTab.ACTIONS
                _actionsSubTab.value = ActionsSubTab.MAPS
                MobileActionDispatcher.openMapsNavigation(context, action.destination, action.travelMode)
                finishLocalFallback(action.speakResponse, action.isHindi, "Map: ${action.destination}")
                true
            }
            is BrainAction.ReadNotifications -> {
                buildAndSpeakNotificationsSummary(action.appFilter, action.isHindi)
                true
            }
            is BrainAction.CreateNote -> {
                repository.saveNote(action.title, action.content)
                finishLocalFallback(action.speakResponse, action.isHindi, "Note Saved")
                true
            }
            is BrainAction.CreateTask -> {
                repository.saveTask(action.title, action.priority)
                finishLocalFallback(action.speakResponse, action.isHindi, "Task Added")
                true
            }
            is BrainAction.SaveMemory -> {
                repository.saveMemory(action.key, action.value)
                finishLocalFallback(action.speakResponse, action.isHindi, "Memory Saved")
                true
            }
            is BrainAction.ShowSection -> {
                _currentTab.value = when (action.section) {
                    "notes", "tasks" -> NavigationTab.PRODUCTIVITY
                    "actions" -> NavigationTab.ACTIONS
                    "settings" -> NavigationTab.SETTINGS
                    else -> NavigationTab.ASSISTANT
                }
                finishLocalFallback(action.speakResponse, action.isHindi, "Navigation")
                true
            }
            is BrainAction.Speak -> {
                if (action.actionBadge in listOf("Time", "Date", "Calculator", "Identity")) {
                    finishLocalFallback(action.text, action.isHindi, action.actionBadge ?: "Local")
                    true
                } else {
                    false
                }
            }
            is BrainAction.StopSpeaking -> {
                stopSpeaking()
                true
            }
        }
    }

    private suspend fun finishLocalFallback(text: String, isHindi: Boolean, badge: String) {
        _lastActionBadge.value = badge
        repository.addMessage(
            text = text,
            isUser = false,
            actionBadge = badge,
            language = if (isHindi) "hi" else "en"
        )
        speakText(text, isHindi)
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
