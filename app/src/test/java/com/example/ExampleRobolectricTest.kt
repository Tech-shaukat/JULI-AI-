package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.JuliLiveManager
import com.example.ai.JuliToolExecutor
import com.example.ai.JuliTools
import com.example.ai.ensureFirebaseInitialized
import com.example.brain.BrainAction
import com.example.brain.JuliBrain
import com.example.data.local.JuliDatabase
import com.example.data.repository.JuliRepository
import com.example.intents.MobileActionDispatcher
import com.example.intents.PendingAction
import com.example.service.CapturedNotification
import com.example.service.NotificationCenterStore
import com.example.speech.WakeWordDetector
import com.google.firebase.ai.type.FunctionCallPart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string resource from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JULI AI", appName)
    }

    @Test
    fun `test on-device wake word detector recognizes Hey JULI variations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = WakeWordDetector(context, {}, {})

        assertTrue(detector.containsWakeWord("hey juli"))
        assertTrue(detector.containsWakeWord("Hey JULI how are you"))
        assertTrue(detector.containsWakeWord("hey julie"))
        assertTrue(detector.containsWakeWord("juli ai"))
        assertTrue(detector.containsWakeWord("हे जूली"))
        assertTrue(detector.containsWakeWord("सुनो जूली"))

        // False positives test
        assertFalse(detector.containsWakeWord("hello google"))
        assertFalse(detector.containsWakeWord("alexa turn on lights"))
        assertFalse(detector.containsWakeWord("random sentence"))
    }

    @Test
    fun `test JuliBrain parses YouTube commands`() {
        val brain = JuliBrain()
        val action = brain.processQuery("YouTube kholo", "hi")
        assertTrue("Action should be OpenYouTube", action is BrainAction.OpenYouTube)
        val ytAction = action as BrainAction.OpenYouTube
        assertTrue(ytAction.speakResponse.isNotEmpty())
    }

    @Test
    fun `test JuliBrain parses Note creation command`() {
        val brain = JuliBrain()
        val action = brain.processQuery("Note likho: Kal subah meeting hai", "hi")
        assertTrue("Action should be CreateNote", action is BrainAction.CreateNote)
        val noteAction = action as BrainAction.CreateNote
        assertTrue(noteAction.content.contains("meeting", ignoreCase = true))
    }

    @Test
    fun `test JuliBrain parses Task creation command`() {
        val brain = JuliBrain()
        val action = brain.processQuery("Task banao: Buy groceries urgent", "hi")
        assertTrue("Action should be CreateTask", action is BrainAction.CreateTask)
        val taskAction = action as BrainAction.CreateTask
        assertEquals("High", taskAction.priority)
    }

    @Test
    fun `test JuliBrain handles Bhojpuri phrases`() {
        val brain = JuliBrain()
        val action = brain.processQuery("Ka haal ba?", "bho")
        assertTrue(action is BrainAction.Speak)
        val speakAction = action as BrainAction.Speak
        assertTrue(speakAction.text.contains("बानी") || speakAction.text.contains("नीक"))
    }

    @Test
    fun `test JuliBrain parses Memory command`() {
        val brain = JuliBrain()
        val action = brain.processQuery("Mera naam Rahul hai yaad rakho", "hi")
        assertTrue("Action should be SaveMemory", action is BrainAction.SaveMemory)
        val memAction = action as BrainAction.SaveMemory
        assertEquals("user_name", memAction.key)
    }

    @Test
    fun `test JuliBrain handles Stop command`() {
        val brain = JuliBrain()
        val stopAction = brain.processQuery("Chup raho", "hi")
        assertTrue(stopAction is BrainAction.StopSpeaking)
    }

    @Test
    fun `test Gemini Live declarations expose all 18 task tools and female voices`() {
        assertEquals(18, JuliTools.functionDeclarations.size)
        assertTrue(JuliLiveManager.femaleVoices.any { it.id == "Sulafat" })
        assertTrue(JuliLiveManager.femaleVoices.any { it.id == "Aoede" })
    }

    @Test
    fun `test JuliToolExecutor saves notes tasks memory and reads context`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = JuliRepository(db.noteDao(), db.chatDao(), db.taskDao(), db.memoryDao())
            val executor = JuliToolExecutor(context, repo)

            val noteRes = executor.execute(
                FunctionCallPart(
                    name = "create_note",
                    args = mapOf("title" to JsonPrimitive("Meeting"), "content" to JsonPrimitive("10 AM sync")),
                    id = "call_1"
                )
            )
            assertEquals("saved", noteRes.responsePart.response["status"]?.jsonPrimitive?.content)

            val taskRes = executor.execute(
                FunctionCallPart(
                    name = "create_task",
                    args = mapOf("title" to JsonPrimitive("Buy milk"), "priority" to JsonPrimitive("High")),
                    id = "call_2"
                )
            )
            assertEquals("added", taskRes.responsePart.response["status"]?.jsonPrimitive?.content)

            val ctxRes = executor.execute(
                FunctionCallPart(
                    name = "get_user_context",
                    args = emptyMap(),
                    id = "call_3"
                )
            )
            assertEquals(1, ctxRes.responsePart.response["notes"]?.jsonArray?.size)
            assertEquals(1, ctxRes.responsePart.response["tasks"]?.jsonArray?.size)
            assertEquals(1, repo.allNotes.first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `test JuliToolExecutor triggers confirmation for Call SMS and WhatsApp tools`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, JuliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = JuliRepository(db.noteDao(), db.chatDao(), db.taskDao(), db.memoryDao())
            val pendingList = mutableListOf<PendingAction>()
            val executor = JuliToolExecutor(
                context = context,
                repository = repo,
                onRequestPendingAction = { pendingList.add(it) }
            )

            val callRes = executor.execute(
                FunctionCallPart(
                    name = "make_phone_call",
                    args = mapOf("recipient_name" to JsonPrimitive("Rahul"), "phone_number" to JsonPrimitive("+91 98765-43210")),
                    id = "call_phone_1"
                )
            )
            assertEquals("awaiting_user_confirmation", callRes.responsePart.response["status"]?.jsonPrimitive?.content)
            assertEquals("+919876543210", callRes.responsePart.response["phone_number"]?.jsonPrimitive?.content)

            val smsRes = executor.execute(
                FunctionCallPart(
                    name = "send_sms",
                    args = mapOf(
                        "recipient_name" to JsonPrimitive("Maa"),
                        "phone_number" to JsonPrimitive("9876543210"),
                        "message" to JsonPrimitive("Main ghar aa raha hoon")
                    ),
                    id = "call_sms_1"
                )
            )
            assertEquals("awaiting_user_confirmation", smsRes.responsePart.response["status"]?.jsonPrimitive?.content)

            val waRes = executor.execute(
                FunctionCallPart(
                    name = "send_whatsapp",
                    args = mapOf(
                        "recipient_name" to JsonPrimitive("Amit"),
                        "phone_number" to JsonPrimitive("9876543210"),
                        "message" to JsonPrimitive("Namaste bhai")
                    ),
                    id = "call_wa_1"
                )
            )
            assertEquals("awaiting_user_confirmation", waRes.responsePart.response["status"]?.jsonPrimitive?.content)
            delay(50)
            // Main looper idle happens in Robolectric if needed; verify tool responses & badge contract cleanly
            assertTrue(callRes.badge.contains("Rahul"))
            assertTrue(smsRes.badge.contains("Maa"))
            assertTrue(waRes.badge.contains("Amit"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `test MobileActionDispatcher phone formatting and intent builders`() {
        assertEquals("+919876543210", MobileActionDispatcher.sanitizePhoneNumber("+91 98765-43210"))
        assertEquals("919876543210", MobileActionDispatcher.formatWhatsAppDigits("9876543210"))
        assertEquals("919876543210", MobileActionDispatcher.formatWhatsAppDigits("09876543210"))

        val dialIntent = MobileActionDispatcher.buildDialIntent("+91 9876543210")
        assertEquals(Intent.ACTION_DIAL, dialIntent.action)
        assertEquals("tel:+919876543210", dialIntent.data.toString())

        val smsIntent = MobileActionDispatcher.buildSmsIntent("9876543210", "Hello")
        assertEquals(Intent.ACTION_SENDTO, smsIntent.action)
        assertEquals("smsto:9876543210", smsIntent.data.toString())
        assertEquals("Hello", smsIntent.getStringExtra("sms_body"))
    }

    @Test
    fun `test JuliBrain parses Call SMS and WhatsApp commands`() {
        val brain = JuliBrain()
        val callAction = brain.processQuery("9876543210 par call karo", "hi")
        assertTrue(callAction is BrainAction.CallPhone)
        assertEquals("9876543210", (callAction as BrainAction.CallPhone).phoneNumber)

        val smsAction = brain.processQuery("SMS bhejo 9876543210: Meeting at 5", "hi")
        assertTrue(smsAction is BrainAction.SendSms)
        assertEquals("Meeting at 5", (smsAction as BrainAction.SendSms).message)

        val waAction = brain.processQuery("WhatsApp bhejo: Namaste", "hi")
        assertTrue(waAction is BrainAction.SendWhatsApp)
        assertEquals("Namaste", (waAction as BrainAction.SendWhatsApp).message)
    }

    @Test
    fun `test ensureFirebaseInitialized initializes cleanly when google-services config is bundled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initialized = ensureFirebaseInitialized(context)
        assertTrue("FirebaseApp should initialize when google-services.json is bundled", initialized)
    }

    @Test
    fun `test JuliBrain parses Map navigation Notification reading and Contact-name Calling`() {
        val brain = JuliBrain()

        val mapAction = brain.processQuery("Patna Junction ka map set kar do", "hi")
        assertTrue("Action should be NavigateMap", mapAction is BrainAction.NavigateMap)
        assertTrue((mapAction as BrainAction.NavigateMap).destination.contains("Patna Junction", ignoreCase = true))

        val notifAction = brain.processQuery("WhatsApp notification aur message padho", "hi")
        assertTrue("Action should be ReadNotifications", notifAction is BrainAction.ReadNotifications)
        assertEquals("whatsapp", (notifAction as BrainAction.ReadNotifications).appFilter)

        val callByName = brain.processQuery("Rahul ko call karo", "hi")
        assertTrue("Action should be CallPhone", callByName is BrainAction.CallPhone)
        assertEquals("Rahul", (callByName as BrainAction.CallPhone).recipientName)
    }

    @Test
    fun `test NotificationCenterStore records and filters notifications`() {
        NotificationCenterStore.clearAll()
        NotificationCenterStore.record(
            CapturedNotification(
                id = "wa_1",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                sender = "Rahul",
                message = "Main station par pahunch gaya hoon",
                timestamp = 1000L
            )
        )
        NotificationCenterStore.record(
            CapturedNotification(
                id = "sms_1",
                packageName = "com.google.android.apps.messaging",
                appName = "Messages",
                sender = "Bank",
                message = "OTP is 123456",
                timestamp = 6000L
            )
        )

        assertEquals(2, NotificationCenterStore.notifications.value.size)
        val waOnly = NotificationCenterStore.filterByApp("whatsapp", 5)
        assertEquals(1, waOnly.size)
        assertEquals("Rahul", waOnly.first().sender)

        val smsOnly = NotificationCenterStore.filterByApp("sms", 5)
        assertEquals(1, smsOnly.size)
        assertEquals("Bank", smsOnly.first().sender)
        NotificationCenterStore.clearAll()
    }

    @Test
    fun `test JuliLiveManager detects and formats server channel closure including suppressed exceptions`() {
        val directServerClose = IllegalStateException("Channel was closed by the server")
        assertTrue(JuliLiveManager.isChannelClosedThrowable(directServerClose))
        val formatted = JuliLiveManager.formatLiveErrorMessage(directServerClose)
        assertTrue(formatted.contains("Channel closed"))

        val suppressedWrapper = RuntimeException("Audio loop aborted").apply {
            addSuppressed(IllegalStateException("WebSocket closed by the server"))
        }
        assertTrue(JuliLiveManager.isChannelClosedThrowable(suppressedWrapper))
        assertFalse(JuliLiveManager.isChannelClosedThrowable(IllegalArgumentException("Invalid audio rate")))
    }

    @Test
    fun `test JuliLiveManager formats exact WebSocket close diagnostics cleanly`() {
        val channelClosed = IllegalStateException("Channel was closed by the server")

        val appCheckDiag = com.example.ai.LiveServerCloseDiagnostics(
            closeCode = 1008,
            closeReason = "Firebase App Check token is invalid"
        )
        val appCheckMsg = JuliLiveManager.formatLiveErrorMessage(channelClosed, appCheckDiag)
        assertTrue(appCheckMsg.contains("App Check"))
        assertTrue(appCheckMsg.contains("WS 1008"))

        val quotaDiag = com.example.ai.LiveServerCloseDiagnostics(
            httpCode = 429,
            serverMessage = "RESOURCE_EXHAUSTED: Live quota exceeded"
        )
        val quotaMsg = JuliLiveManager.formatLiveErrorMessage(channelClosed, quotaDiag)
        assertTrue(quotaMsg.contains("HTTP 429"))
        assertTrue(quotaMsg.contains("RESOURCE_EXHAUSTED"))

        val genericCloseDiag = com.example.ai.LiveServerCloseDiagnostics(
            closeCode = 1007,
            closeReason = "Invalid setup payload"
        )
        val genericMsg = JuliLiveManager.formatLiveErrorMessage(channelClosed, genericCloseDiag)
        assertTrue(genericMsg.contains("WS 1007: Invalid setup payload"))
    }
}
