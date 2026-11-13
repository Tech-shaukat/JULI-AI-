package com.example.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.intents.JuliAssistantPrefs
import java.util.Locale

class JuliNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "JuliNotifListener"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastSpokenAt = 0L

    override fun onCreate() {
        super.onCreate()
        try {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val hiLocale = Locale.forLanguageTag("hi-IN")
                    val res = tts?.setLanguage(hiLocale)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.US
                    }
                    tts?.setSpeechRate(0.95f)
                    tts?.setPitch(1.06f)
                    ttsReady = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize notification TTS", e)
        }
    }

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsReady = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        // Ignore JULI AI's own notifications and ongoing system progress notifications
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: Bundle.EMPTY
        val sender = extractSender(extras)
        val body = extractMessageBody(extras)
        if (sender.isBlank() && body.isBlank()) return

        val appName = resolveAppLabel(pkg)
        val captured = CapturedNotification(
            id = "${sbn.key}_${sbn.postTime}",
            packageName = pkg,
            appName = appName,
            sender = sender.ifBlank { appName },
            message = body,
            timestamp = sbn.postTime
        )

        NotificationCenterStore.record(captured)

        if (JuliAssistantPrefs.isAutoAnnounceNotifications(applicationContext)) {
            announceNotification(captured)
        }
    }

    private fun extractSender(extras: Bundle): String {
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        return convTitle?.ifBlank { title.orEmpty() } ?: title.orEmpty()
    }

    private fun extractMessageBody(extras: Bundle): String {
        // Handle MessagingStyle bundles if present (WhatsApp, Messages, Telegram, etc.)
        val messagesParcelables = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.filterIsInstance<Bundle>()
                ?.toTypedArray()
        }

        if (!messagesParcelables.isNullOrEmpty()) {
            val lastBundle = messagesParcelables.lastOrNull()
            val msgText = lastBundle?.getCharSequence("text")?.toString()?.trim().orEmpty()
            val msgSender = lastBundle?.getCharSequence("sender")?.toString()?.trim().orEmpty()
            if (msgText.isNotEmpty()) {
                return if (msgSender.isNotEmpty()) "$msgSender: $msgText" else msgText
            }
        }

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        if (bigText.isNotEmpty()) return bigText

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text

        return extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
    }

    private fun resolveAppLabel(pkg: String): String {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun announceNotification(item: CapturedNotification) {
        val now = System.currentTimeMillis()
        if (!ttsReady || (now - lastSpokenAt) < 3_500L) return
        lastSpokenAt = now

        val readContent = JuliAssistantPrefs.isReadNotificationContent(applicationContext)
        val spoken = if (readContent && item.message.isNotBlank()) {
            "${item.appName} पर ${item.sender} का मैसेज आया है: ${item.message.take(160)}"
        } else if (item.sender.isNotBlank() && item.sender != item.appName) {
            "${item.appName} पर ${item.sender} का नोटिफिकेशन आया है।"
        } else {
            "${item.appName} से नया नोटिफिकेशन आया है।"
        }

        try {
            tts?.speak(spoken, TextToSpeech.QUEUE_ADD, null, "notif_${item.timestamp}")
        } catch (e: Exception) {
            Log.w(TAG, "Error announcing notification", e)
        }
    }
}
