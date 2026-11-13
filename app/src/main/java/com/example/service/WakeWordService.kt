package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.JuliGeminiTextClient
import com.example.ai.JuliToolExecutor
import com.example.data.local.JuliDatabase
import com.example.data.repository.JuliRepository
import com.example.intents.PendingAction
import com.example.speech.JuliSpeechManager
import com.example.speech.JuliTtsManager
import com.example.speech.WakeWordDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WakeWordEvent {
    object WakeWordTriggered : WakeWordEvent()
    data class CommandRecognized(val command: String) : WakeWordEvent()
    data class AssistantSpoke(val response: String) : WakeWordEvent()
    data class PendingActionRequested(val action: PendingAction) : WakeWordEvent()
}

class WakeWordService : Service() {

    companion object {
        const val TAG = "WakeWordService"
        const val CHANNEL_ID = "juli_wake_word_channel"
        const val CONFIRM_CHANNEL_ID = "juli_confirm_action_channel"
        const val NOTIFICATION_ID = 1001
        const val CONFIRM_NOTIFICATION_ID = 1002

        const val EXTRA_PENDING_ACTION_TYPE = "extra_pending_action_type"
        const val EXTRA_PENDING_RECIPIENT = "extra_pending_recipient"
        const val EXTRA_PENDING_PHONE = "extra_pending_phone"
        const val EXTRA_PENDING_MESSAGE = "extra_pending_message"

        const val ACTION_START_SERVICE = "com.example.service.START_WAKE_WORD"
        const val ACTION_STOP_SERVICE = "com.example.service.STOP_WAKE_WORD"
        const val ACTION_PAUSE_DETECTION = "com.example.service.PAUSE_WAKE_WORD"
        const val ACTION_RESUME_DETECTION = "com.example.service.RESUME_WAKE_WORD"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _wakeWordEvents = MutableSharedFlow<WakeWordEvent>(extraBufferCapacity = 16)
        val wakeWordEvents: SharedFlow<WakeWordEvent> = _wakeWordEvents.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            if (!_isServiceRunning.value) return
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_PAUSE_DETECTION
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            if (!_isServiceRunning.value) return
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_RESUME_DETECTION
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeWordDetector: WakeWordDetector? = null
    private var commandSpeechManager: JuliSpeechManager? = null
    private var ttsManager: JuliTtsManager? = null
    private lateinit var repository: JuliRepository
    private val toolExecutor by lazy {
        JuliToolExecutor(
            context = applicationContext,
            repository = repository,
            onRequestPendingAction = { action ->
                scope.launch {
                    _wakeWordEvents.emit(WakeWordEvent.PendingActionRequested(action))
                }
                showConfirmActionNotification(action)
            }
        )
    }
    private val geminiTextClient by lazy { JuliGeminiTextClient(toolExecutor) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WakeWordService onCreate")
        val db = JuliDatabase.getDatabase(this)
        repository = JuliRepository(db.noteDao(), db.chatDao(), db.taskDao(), db.memoryDao())

        createNotificationChannel()
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE

        when (action) {
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Received ACTION_STOP_SERVICE")
                stopWakeListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_DETECTION -> {
                Log.d(TAG, "Pausing wake word detector for foreground mic use")
                wakeWordDetector?.stop()
                updateNotification("JULI AI is active in app...")
                return START_STICKY
            }
            ACTION_RESUME_DETECTION -> {
                Log.d(TAG, "Resuming wake word detector")
                if (_isServiceRunning.value) {
                    updateNotification("Listening for \"Hey JULI\"...")
                    startWakeListening()
                }
                return START_STICKY
            }
        }

        Log.d(TAG, "Starting foreground wake word service with microphone type...")
        startForegroundWithNotification("Listening for \"Hey JULI\"...")

        _isServiceRunning.value = true
        startWakeListening()

        return START_STICKY
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildForegroundNotification(contentText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JULI AI • Wake Word Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Open JULI", openAppPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JULI AI Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status when JULI AI is listening for 'Hey JULI'"
                setShowBadge(false)
            }
            val confirmChannel = NotificationChannel(
                CONFIRM_CHANNEL_ID,
                "JULI AI Action Confirmation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompts confirmation before opening Dialer, SMS, or WhatsApp"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(confirmChannel)
        }
    }

    private fun showConfirmActionNotification(action: PendingAction) {
        val (type, recipient, phone, msg) = when (action) {
            is PendingAction.MakeCall -> listOf("call", action.recipientName, action.phoneNumber, "")
            is PendingAction.SendSms -> listOf("sms", action.recipientName, action.phoneNumber, action.message)
            is PendingAction.SendWhatsApp -> listOf("whatsapp", action.recipientName, action.phoneNumber, action.message)
            else -> return
        }

        val targetLabel = recipient.ifBlank { phone.ifBlank { "Contact" } }
        val titleText = when (type) {
            "call" -> "Confirm Call: $targetLabel"
            "sms" -> "Confirm SMS: $targetLabel"
            else -> "Confirm WhatsApp: $targetLabel"
        }
        val bodyText = if (msg.isNotBlank()) "Tap to review & confirm: \"$msg\"" else "Tap JULI AI to review & confirm."

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PENDING_ACTION_TYPE, type)
            putExtra(EXTRA_PENDING_RECIPIENT, recipient)
            putExtra(EXTRA_PENDING_PHONE, phone)
            putExtra(EXTRA_PENDING_MESSAGE, msg)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            CONFIRM_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CONFIRM_CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CONFIRM_NOTIFICATION_ID, notification)
    }

    private fun initTts() {
        ttsManager = JuliTtsManager(
            context = this,
            onInitComplete = { success ->
                if (success) {
                    ttsManager?.setSpeechParams(0.95f, 1.08f)
                }
            },
            onSpeakingStarted = {},
            onSpeakingDone = {
                // Resume wake word listening after TTS finishes
                if (_isServiceRunning.value) {
                    updateNotification("Listening for \"Hey JULI\"...")
                    wakeWordDetector?.start()
                }
            },
            onError = { err ->
                Log.w(TAG, "Service TTS error: $err")
                if (_isServiceRunning.value) {
                    wakeWordDetector?.start()
                }
            }
        )
    }

    private fun startWakeListening() {
        wakeWordDetector?.stop()
        wakeWordDetector = WakeWordDetector(
            context = this,
            onWakeWordDetected = { phrase ->
                handleWakeWordDetected(phrase)
            },
            onError = { error ->
                Log.w(TAG, "WakeWordDetector error: $error")
            }
        )
        wakeWordDetector?.start()
    }

    private fun stopWakeListening() {
        _isServiceRunning.value = false
        wakeWordDetector?.stop()
        wakeWordDetector = null
        commandSpeechManager?.destroy()
        commandSpeechManager = null
    }

    private fun handleWakeWordDetected(wakePhrase: String) {
        Log.i(TAG, "Wake word detected: $wakePhrase")
        wakeWordDetector?.stop() // Pause wake word detector to capture the command

        triggerHapticAndChime()
        updateNotification("\"Hey JULI\" heard! Listening to your command...")

        scope.launch {
            _wakeWordEvents.emit(WakeWordEvent.WakeWordTriggered)
            kotlinx.coroutines.delay(250)
            listenForUserCommand()
        }
    }

    private fun listenForUserCommand() {
        commandSpeechManager?.destroy()
        commandSpeechManager = JuliSpeechManager(
            context = this,
            onStateChange = {},
            onPartialText = {},
            onFinalText = { command ->
                handleUserCommand(command)
            },
            onRmsLevel = {},
            onError = { error ->
                Log.w(TAG, "Command recognition error: $error")
                updateNotification("Listening for \"Hey JULI\"...")
                wakeWordDetector?.start()
            }
        )
        commandSpeechManager?.startListening("hi")
    }

    private fun handleUserCommand(command: String) {
        Log.i(TAG, "Processing wake-word command with Gemini AI: $command")
        updateNotification("Gemini AI Thinking: \"$command\"...")

        scope.launch {
            _wakeWordEvents.emit(WakeWordEvent.CommandRecognized(command))
            repository.addMessage(text = command, isUser = true, language = "hi")

            try {
                val reply = geminiTextClient.processQuery(
                    query = command,
                    languageCode = "hi",
                    recentMessages = emptyList()
                )
                val badge = reply.executedBadges.lastOrNull() ?: "Gemini AI"
                speakResponse(reply.text, reply.isHindi, badge)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Wake-word Gemini call timed out", e)
                speakResponse("Gemini से जुड़ने में समय अधिक लग रहा है। कृपया इंटरनेट जाँचें।", true, "Timeout")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Wake-word Gemini call failed", e)
                val errMsg = e.localizedMessage ?: "Gemini AI service unavailable"
                speakResponse("माफ़ कीजिए, Gemini AI से संपर्क नहीं हो पाया: $errMsg", true, "Error")
            }
        }
    }

    private suspend fun speakResponse(response: String, isHindi: Boolean, badge: String?) {
        repository.addMessage(text = response, isUser = false, actionBadge = badge, language = if (isHindi) "hi" else "en")
        _wakeWordEvents.emit(WakeWordEvent.AssistantSpoke(response))
        updateNotification("JULI: $response")
        ttsManager?.speak(response, isHindi)
    }

    private fun triggerHapticAndChime() {
        try {
            // Haptic feedback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(120)
            }

            // Audio tone activation chime
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
            toneGen.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error triggering feedback", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WakeWordService onDestroy")
        stopWakeListening()
        ttsManager?.shutdown()
        ttsManager = null
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
