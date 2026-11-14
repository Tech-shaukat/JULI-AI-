package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.configureAppCheck
import com.example.intents.MobileActionDispatcher
import com.example.service.WakeWordService
import com.example.speech.JuliSpeechManager
import com.example.ui.components.ConfirmationDialog
import com.example.ui.screens.ActionsScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ProductivityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CosmicMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCosmos
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.JuliViewModel
import com.example.viewmodel.NavigationTab
import kotlinx.coroutines.launch

import android.content.Intent
import com.example.intents.PendingAction

class MainActivity : ComponentActivity() {

    private val viewModel: JuliViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check with launch intent BEFORE setContent, ViewModels, or Firebase ops
        configureAppCheck(this, intent)
        viewModel.handleIntentExtras(intent)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                JuliMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntentExtras(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuliMainApp(viewModel: JuliViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTab by viewModel.currentTab.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val liveTranscription by viewModel.liveTranscription.collectAsState()
    val language by viewModel.language.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val pitch by viewModel.pitch.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()
    val isWakeWordActive by viewModel.isWakeWordActive.collectAsState()
    val liveUiState by viewModel.liveUiState.collectAsState()
    val lastActionBadge by viewModel.lastActionBadge.collectAsState()
    val bannerError by viewModel.bannerError.collectAsState()
    val actionsSubTab by viewModel.actionsSubTab.collectAsState()
    val mapDestination by viewModel.mapDestination.collectAsState()
    val mapTravelMode by viewModel.mapTravelMode.collectAsState()
    val directCallEnabled by viewModel.directCallEnabled.collectAsState()
    val autoAnnounceNotifs by viewModel.autoAnnounceNotifs.collectAsState()
    val readNotifContent by viewModel.readNotifContent.collectAsState()
    val hasContactsPermission by viewModel.hasContactsPermission.collectAsState()
    val hasCallPhonePermission by viewModel.hasCallPhonePermission.collectAsState()
    val isNotificationAccessEnabled by viewModel.isNotificationAccessEnabled.collectAsState()
    val capturedNotifications by viewModel.capturedNotifications.collectAsState()
    val imageStudioState by viewModel.imageStudioState.collectAsState()
    val videoStudioState by viewModel.videoStudioState.collectAsState()
    val appCheckStatus by viewModel.appCheckStatus.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var showMicRationale by remember { mutableStateOf(false) }
    var pendingMicPurpose by remember { mutableStateOf<String?>(null) }

    // Refresh runtime permissions & Notification Listener status whenever returning from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDevicePermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // BackHandler: Return to Assistant tab when user presses system Back
    BackHandler(enabled = currentTab != NavigationTab.ASSISTANT) {
        viewModel.selectTab(NavigationTab.ASSISTANT)
    }

    // Permission launchers
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (hasMicPermission && pendingMicPurpose == "wake") {
            pendingMicPurpose = null
            viewModel.toggleWakeWordService(context, true) {}
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        val purpose = pendingMicPurpose
        if (isGranted) {
            when (purpose) {
                "quick" -> {
                    pendingMicPurpose = null
                    viewModel.onQuickVoiceClicked(true) {}
                }
                "wake" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        pendingMicPurpose = null
                        viewModel.toggleWakeWordService(context, true) {}
                    }
                }
                else -> {
                    pendingMicPurpose = null
                    viewModel.toggleGeminiLive(true) {}
                }
            }
        } else {
            pendingMicPurpose = null
            showMicRationale = true
        }
    }

    val callAndContactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshDevicePermissions()
    }

    val speechPopupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isWakeWordActive) {
            WakeWordService.resume(context)
        }
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                viewModel.processUserQuery(text)
            }
        }
    }

    // Display error messages with Voice Popup fallback action for speech errors
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            viewModel.clearError()
            val isSpeechError = msg.contains("सुनाई नहीं दिया") ||
                msg.contains("आवाज़ नहीं मिली") ||
                msg.contains("Voice Popup") ||
                msg.contains("स्पीच")

            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (isSpeechError) "Voice Popup" else null,
                duration = SnackbarDuration.Long
            )

            if (result == SnackbarResult.ActionPerformed && isSpeechError) {
                if (isWakeWordActive) {
                    WakeWordService.pause(context)
                }
                val intent = JuliSpeechManager.buildRecognizerIntent(context, language).apply {
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "JULI AI सुन रही है... कृपया बोलें")
                }
                try {
                    speechPopupLauncher.launch(intent)
                } catch (_: Exception) {
                    if (isWakeWordActive) {
                        WakeWordService.resume(context)
                    }
                    snackbarHostState.showSnackbar("Voice popup इस डिवाइस पर उपलब्ध नहीं है।")
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DeepSpace,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(CyberCyan, NeonPurple, CosmicMagenta))
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(DeepSpace),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "JULI Logo",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "JULI AI",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CyberCyan.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = when {
                                            liveUiState.isConnected -> "GEMINI LIVE"
                                            isWakeWordActive -> "HEY JULI ON"
                                            else -> "LIVE AI"
                                        },
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = when (currentTab) {
                                    NavigationTab.ASSISTANT -> "Personal Voice Assistant • AI सहायिका"
                                    NavigationTab.CHAT -> "Conversation History"
                                    NavigationTab.PRODUCTIVITY -> "Notes & Tasks Planner"
                                    NavigationTab.ACTIONS -> "Mobile Shortcuts & Wake Word"
                                    NavigationTab.SETTINGS -> "Preferences & Memory"
                                },
                                fontSize = 10.sp,
                                color = TextDim
                            )
                        }
                    }
                },
                actions = {
                    // Quick YouTube shortcut
                    IconButton(
                        onClick = { MobileActionDispatcher.openYouTube(context) },
                        modifier = Modifier.testTag("top_bar_youtube_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = "Open YouTube",
                            tint = Color(0xFFFF0033),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Clear chat if on Chat tab
                    if (currentTab == NavigationTab.CHAT && messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat",
                                tint = TextDim,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkCosmos,
                    titleContentColor = TextPrimary
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkCosmos,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.ASSISTANT,
                    onClick = { viewModel.selectTab(NavigationTab.ASSISTANT) },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Assistant") },
                    label = { Text("Assistant", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DeepSpace,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextDim,
                        unselectedTextColor = TextDim
                    )
                )

                NavigationBarItem(
                    selected = currentTab == NavigationTab.CHAT,
                    onClick = { viewModel.selectTab(NavigationTab.CHAT) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (messages.isNotEmpty()) {
                                    Badge(containerColor = CyberCyan, contentColor = DeepSpace) {
                                        Text("${messages.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "Chat")
                        }
                    },
                    label = { Text("Chat", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DeepSpace,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextDim,
                        unselectedTextColor = TextDim
                    )
                )

                NavigationBarItem(
                    selected = currentTab == NavigationTab.PRODUCTIVITY,
                    onClick = { viewModel.selectTab(NavigationTab.PRODUCTIVITY) },
                    icon = {
                        val pendingCount = tasks.count { !it.isCompleted } + notes.size
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(containerColor = NeonPurple, contentColor = Color.White) {
                                        Text("$pendingCount")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = "Productivity")
                        }
                    },
                    label = { Text("Planner", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DeepSpace,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextDim,
                        unselectedTextColor = TextDim
                    )
                )

                NavigationBarItem(
                    selected = currentTab == NavigationTab.ACTIONS,
                    onClick = { viewModel.selectTab(NavigationTab.ACTIONS) },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "Actions") },
                    label = { Text("Actions", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DeepSpace,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextDim,
                        unselectedTextColor = TextDim
                    )
                )

                NavigationBarItem(
                    selected = currentTab == NavigationTab.SETTINGS,
                    onClick = { viewModel.selectTab(NavigationTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DeepSpace,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextDim,
                        unselectedTextColor = TextDim
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationTab.ASSISTANT -> {
                    AssistantScreen(
                        assistantState = assistantState,
                        liveUiState = liveUiState,
                        rmsLevel = rmsLevel,
                        statusText = statusText,
                        liveTranscription = liveTranscription,
                        lastActionBadge = lastActionBadge,
                        bannerError = bannerError,
                        language = language,
                        isWakeWordActive = isWakeWordActive,
                        onToggleLive = {
                            viewModel.toggleGeminiLive(
                                hasRecordPermission = hasMicPermission,
                                onRequestPermission = {
                                    pendingMicPurpose = "live"
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            )
                        },
                        onQuickVoiceClick = {
                            viewModel.onQuickVoiceClicked(
                                hasRecordPermission = hasMicPermission,
                                onRequestPermission = {
                                    pendingMicPurpose = "quick"
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            )
                        },
                        onStopSpeaking = { viewModel.stopSpeaking() },
                        onDismissError = { viewModel.dismissBannerError() },
                        onLanguageChange = { viewModel.setLanguage(it) },
                        onToggleWakeWord = {
                            if (!hasMicPermission) {
                                pendingMicPurpose = "wake"
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                pendingMicPurpose = "wake"
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleWakeWordService(context, true) {}
                            }
                        },
                        onQuickAction = { query -> viewModel.processUserQuery(query) }
                    )
                }

                NavigationTab.CHAT -> {
                    ChatScreen(
                        messages = messages,
                        onSendMessage = { query -> viewModel.processUserQuery(query) },
                        onSpeakAgain = { text, isHindi -> viewModel.speakText(text, isHindi) },
                        onSuggestionClick = { suggestion -> viewModel.processUserQuery(suggestion) }
                    )
                }

                NavigationTab.PRODUCTIVITY -> {
                    ProductivityScreen(
                        notes = notes,
                        tasks = tasks,
                        onSaveNote = { title, content -> viewModel.saveManualNote(title, content) },
                        onDeleteNote = { note -> viewModel.deleteNote(note) },
                        onSaveTask = { title, priority -> viewModel.saveManualTask(title, priority) },
                        onToggleTask = { task -> viewModel.toggleTask(task) },
                        onDeleteTask = { task -> viewModel.deleteTask(task) },
                        onSpeakText = { text -> viewModel.speakText(text, isHindi = true) }
                    )
                }

                NavigationTab.ACTIONS -> {
                    ActionsScreen(
                        selectedSubTab = actionsSubTab,
                        onSelectSubTab = { viewModel.selectActionsSubTab(it) },
                        isWakeWordActive = isWakeWordActive,
                        onToggleWakeWord = {
                            if (!hasMicPermission) {
                                pendingMicPurpose = "wake"
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                pendingMicPurpose = "wake"
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleWakeWordService(context, true) {}
                            }
                        },
                        hasContactsPermission = hasContactsPermission,
                        hasCallPhonePermission = hasCallPhonePermission,
                        directCallEnabled = directCallEnabled,
                        onRequestCallPermissions = {
                            callAndContactsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.CALL_PHONE
                                )
                            )
                        },
                        onToggleDirectCall = { viewModel.setDirectCallEnabled(it) },
                        onCallByNameOrNumber = { query -> viewModel.callContactOrPrompt(context, query) },
                        onRequestCall = { viewModel.setPendingAction(PendingAction.MakeCall()) },
                        onRequestSms = { viewModel.setPendingAction(PendingAction.SendSms()) },
                        onRequestWhatsApp = { viewModel.setPendingAction(PendingAction.SendWhatsApp()) },
                        onOpenYouTube = { MobileActionDispatcher.openYouTube(context) },
                        onOpenChrome = { MobileActionDispatcher.openChrome(context) },
                        onOpenCamera = { MobileActionDispatcher.openCamera(context) },
                        onOpenSettings = { type -> MobileActionDispatcher.openSettings(context, type) },
                        installedApps = installedApps,
                        onOpenAppByName = { query -> viewModel.openInstalledApp(context, query) },
                        onLaunchInstalledApp = { app -> viewModel.launchInstalledApp(context, app) },
                        mapDestination = mapDestination,
                        mapTravelMode = mapTravelMode,
                        onMapDestinationChange = { dest -> viewModel.setMapDestination(dest, mapTravelMode) },
                        onMapTravelModeChange = { mode -> viewModel.setMapDestination(mapDestination, mode) },
                        onStartMapNavigation = { dest, mode -> viewModel.launchMapNavigation(context, dest, mode) },
                        onSearchMapPlace = { query -> viewModel.searchMapPlace(context, query) },
                        isNotificationAccessEnabled = isNotificationAccessEnabled,
                        autoAnnounceNotifs = autoAnnounceNotifs,
                        readNotifContent = readNotifContent,
                        capturedNotifications = capturedNotifications,
                        onOpenNotificationSettings = { MobileActionDispatcher.openNotificationListenerSettings(context) },
                        onToggleAutoAnnounceNotifs = { viewModel.setAutoAnnounceNotifications(it) },
                        onToggleReadNotifContent = { viewModel.setReadNotificationContent(it) },
                        onReadAllNotificationsAloud = { viewModel.readRecentNotifications() },
                        onReadSingleNotificationAloud = { item -> viewModel.readNotificationAloud(item) },
                        onClearNotifications = { viewModel.clearNotifications() },
                        imageStudioState = imageStudioState,
                        videoStudioState = videoStudioState,
                        hasVideoApiKey = viewModel.hasVideoApiKey(),
                        onGenerateImage = { prompt, ratio -> viewModel.generateImage(prompt, ratio) },
                        onEditImage = { bmp, prompt -> viewModel.editImage(bmp, prompt) },
                        onGenerateVideo = { prompt, ratio, followUp -> viewModel.generateVideo(prompt, ratio, followUp) },
                        onShareGeneratedMedia = { file, mime, title -> viewModel.shareGeneratedMedia(file, mime, title) }
                    )
                }

                NavigationTab.SETTINGS -> {
                    SettingsScreen(
                        currentLanguage = language,
                        selectedLiveVoice = liveUiState.selectedVoice,
                        speechRate = speechRate,
                        pitch = pitch,
                        isWakeWordActive = isWakeWordActive,
                        directCallEnabled = directCallEnabled,
                        hasContactsPermission = hasContactsPermission,
                        hasCallPhonePermission = hasCallPhonePermission,
                        isNotificationAccessEnabled = isNotificationAccessEnabled,
                        autoAnnounceNotifs = autoAnnounceNotifs,
                        readNotifContent = readNotifContent,
                        appCheckStatus = appCheckStatus,
                        memories = memories,
                        onLanguageChange = { viewModel.setLanguage(it) },
                        onSelectLiveVoice = { viewModel.setLiveVoice(it) },
                        onSpeechParamsChange = { rate, p -> viewModel.setSpeechParams(rate, p) },
                        onToggleWakeWord = {
                            if (!hasMicPermission) {
                                pendingMicPurpose = "wake"
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                pendingMicPurpose = "wake"
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleWakeWordService(context, true) {}
                            }
                        },
                        onToggleDirectCall = { viewModel.setDirectCallEnabled(it) },
                        onRequestCallPermissions = {
                            callAndContactsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.CALL_PHONE
                                )
                            )
                        },
                        onToggleAutoAnnounceNotifs = { viewModel.setAutoAnnounceNotifications(it) },
                        onToggleReadNotifContent = { viewModel.setReadNotificationContent(it) },
                        onOpenNotificationSettings = { MobileActionDispatcher.openNotificationListenerSettings(context) },
                        onRefreshAppCheck = { viewModel.refreshAppCheckStatus() },
                        onDeleteMemory = { key -> viewModel.deleteMemory(key) },
                        onRequestClearAllData = { viewModel.clearAllData() }
                    )
                }
            }
        }
    }

    // Confirmation dialog before data deletion or Call/SMS/WhatsApp actions
    pendingAction?.let { action ->
        ConfirmationDialog(
            pendingAction = action,
            onConfirm = { updatedAction ->
                viewModel.executePendingAction(context, updatedAction)
            },
            onDismiss = {
                viewModel.setPendingAction(null)
            }
        )
    }

    // Microphone permission rationale dialog
    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            containerColor = DarkCosmos,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Microphone Permission Required",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "JULI AI needs access to your microphone to listen to Hindi, English, and Bhojpuri voice commands and detect 'Hey JULI'. Please grant the permission to enable speech features.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicRationale = false
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Grant Permission", color = DeepSpace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMicRationale = false }) {
                    Text("Dismiss", color = TextSecondary)
                }
            }
        )
    }
}
