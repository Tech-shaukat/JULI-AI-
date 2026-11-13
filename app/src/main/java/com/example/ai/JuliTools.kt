package com.example.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.repository.JuliRepository
import com.example.intents.MobileActionDispatcher
import com.example.intents.PendingAction
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ToolExecutionResult(
    val toolName: String,
    val badge: String,
    val responsePart: FunctionResponsePart
)

object JuliTools {

    fun buildSystemInstruction(languageCode: String): Content {
        val langHint = when (languageCode) {
            "en" -> "Prefer natural, warm Indian English unless the user speaks Hindi or Bhojpuri."
            "bho" -> "Prefer friendly, respectful Bhojpuri (in Devanagari) or Hindi as the user speaks."
            "auto" -> "Automatically reply in whichever language (Hindi, English, or Bhojpuri) the user speaks."
            else -> "Prefer natural, sweet conversational Hindi (Devanagari script) mixed gently with everyday words where natural, or match the user's language if they speak English or Bhojpuri."
        }

        return content {
            text(
                """
                You are JULI AI (जूलिया), a warm, intelligent, polite female personal voice assistant on an Android 15 phone.
                $langHint

                Core behavior:
                - Speak naturally, concisely, and warmly—ideal for real-time voice conversation. Avoid markdown formatting, bullet lists, or long essays unless asked.
                - Whenever the user asks to perform an action on the phone (open YouTube, search Google/Chrome, open Camera, open WiFi/Bluetooth/Display/Sound settings, save a note, add a task, remember personal info, check their notes/tasks, switch app tabs, make a phone call, send an SMS, or send a WhatsApp message), IMMEDIATELY invoke the corresponding tool function and then briefly confirm what you prepared or did.
                - Calling, SMS, and WhatsApp with Safety Confirmation:
                  * If the user asks to call someone, send an SMS, or send a WhatsApp message to a contact name without giving a phone number, call get_user_context first to check if their phone number is saved in memory.
                  * Then ALWAYS invoke make_phone_call, send_sms, or send_whatsapp with whatever recipient_name, phone_number, and message details you have.
                  * For user privacy and safety, those three tools open a confirmation dialog on screen (where the user can review, pick a contact, or edit the message) before opening the Dialer, SMS app, or WhatsApp. Clearly tell the user to tap Confirm on the screen. Never claim a call or message was already sent without confirmation.
                - If the user asks about their saved notes, tasks, or remembered facts, call get_user_context first so your answer is grounded in real stored data.
                - Never invent tool results or pretend an action succeeded if the tool returned an error.
                """.trimIndent()
            )
        }
    }

    val functionDeclarations: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "open_youtube",
            description = "Opens the YouTube app on the user's phone, optionally searching for a specific video, song, or topic.",
            parameters = mapOf(
                "query" to Schema.string(description = "Optional search query for YouTube, e.g. 'lofi songs' or 'news today'.")
            ),
            optionalParameters = listOf("query")
        ),
        FunctionDeclaration(
            name = "open_chrome",
            description = "Opens Chrome/web browser to a specific URL or performs a Google web search.",
            parameters = mapOf(
                "query_or_url" to Schema.string(description = "Optional search query or full https:// URL.")
            ),
            optionalParameters = listOf("query_or_url")
        ),
        FunctionDeclaration(
            name = "open_camera",
            description = "Launches the device Camera app so the user can take a photo.",
            parameters = emptyMap(),
            optionalParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "open_settings",
            description = "Opens a specific Android system settings screen.",
            parameters = mapOf(
                "setting_type" to Schema.enumeration(
                    values = listOf("wifi", "bluetooth", "display", "sound", "general"),
                    description = "Which Android settings panel to open."
                )
            ),
            optionalParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "make_phone_call",
            description = "Prepares a phone call confirmation dialog for the user before opening the Android Phone Dialer.",
            parameters = mapOf(
                "recipient_name" to Schema.string(description = "Optional contact or person name, e.g. 'Rahul' or 'Papa'."),
                "phone_number" to Schema.string(description = "Optional phone number digits, e.g. '+919876543210'.")
            ),
            optionalParameters = listOf("recipient_name", "phone_number")
        ),
        FunctionDeclaration(
            name = "send_sms",
            description = "Prepares an SMS message confirmation dialog for the user before opening the default SMS app.",
            parameters = mapOf(
                "recipient_name" to Schema.string(description = "Optional recipient name."),
                "phone_number" to Schema.string(description = "Optional recipient phone number."),
                "message" to Schema.string(description = "The SMS message text to send.")
            ),
            optionalParameters = listOf("recipient_name", "phone_number")
        ),
        FunctionDeclaration(
            name = "send_whatsapp",
            description = "Prepares a WhatsApp message confirmation dialog for the user before opening WhatsApp with the message pre-filled.",
            parameters = mapOf(
                "recipient_name" to Schema.string(description = "Optional recipient name."),
                "phone_number" to Schema.string(description = "Optional recipient phone number with or without country code."),
                "message" to Schema.string(description = "The WhatsApp message text to send.")
            ),
            optionalParameters = listOf("recipient_name", "phone_number")
        ),
        FunctionDeclaration(
            name = "create_note",
            description = "Saves a personal note locally on the device.",
            parameters = mapOf(
                "title" to Schema.string(description = "Short title for the note."),
                "content" to Schema.string(description = "Full text content of the note.")
            ),
            optionalParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "create_task",
            description = "Adds a to-do task or reminder item to the user's Planner.",
            parameters = mapOf(
                "title" to Schema.string(description = "Description of the task to add."),
                "priority" to Schema.enumeration(
                    values = listOf("Normal", "High"),
                    description = "Priority level of the task."
                )
            ),
            optionalParameters = listOf("priority")
        ),
        FunctionDeclaration(
            name = "save_memory",
            description = "Saves an important personal fact or preference requested by the user (e.g., user's name, favorite topic, or contact phone number).",
            parameters = mapOf(
                "key" to Schema.string(description = "Snake-case key like 'user_name', 'favorite_music', or 'contact_rahul'."),
                "value" to Schema.string(description = "Value to remember.")
            ),
            optionalParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "get_user_context",
            description = "Reads the user's currently saved notes, pending tasks, and remembered facts from local storage.",
            parameters = emptyMap(),
            optionalParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "navigate_tab",
            description = "Switches the JULI AI app screen to a specific tab.",
            parameters = mapOf(
                "tab" to Schema.enumeration(
                    values = listOf("assistant", "chat", "planner", "actions", "settings"),
                    description = "Target tab inside JULI AI."
                )
            ),
            optionalParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "get_current_datetime",
            description = "Gets the current local date and time on the user's phone.",
            parameters = emptyMap(),
            optionalParameters = emptyList()
        )
    )

    val tool: Tool = Tool.functionDeclarations(functionDeclarations)
}

class JuliToolExecutor(
    private val context: Context,
    private val repository: JuliRepository,
    private val onNavigateTab: (String) -> Unit = {},
    private val onToolBadge: (String) -> Unit = {},
    private val onRequestPendingAction: (PendingAction) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun executeBlocking(call: FunctionCallPart): FunctionResponsePart {
        return runBlocking(Dispatchers.IO) {
            withTimeout(10_000L) {
                execute(call).responsePart
            }
        }
    }

    suspend fun execute(call: FunctionCallPart): ToolExecutionResult {
        val args = call.args
        Log.i("JuliTools", "Executing tool '${call.name}' with args=$args")

        val (badge, payload) = when (call.name) {
            "open_youtube" -> {
                val query = args.stringArg("query")
                mainHandler.post { MobileActionDispatcher.openYouTube(context, query) }
                val label = if (query.isNullOrBlank()) "YouTube Opened" else "YouTube: $query"
                label to buildJsonObject {
                    put("status", JsonPrimitive("opened"))
                    put("query", JsonPrimitive(query ?: ""))
                }
            }

            "open_chrome" -> {
                val queryOrUrl = args.stringArg("query_or_url")
                mainHandler.post { MobileActionDispatcher.openChrome(context, queryOrUrl) }
                val label = if (queryOrUrl.isNullOrBlank()) "Chrome Opened" else "Browser: $queryOrUrl"
                label to buildJsonObject {
                    put("status", JsonPrimitive("opened"))
                    put("target", JsonPrimitive(queryOrUrl ?: "https://www.google.com"))
                }
            }

            "open_camera" -> {
                mainHandler.post { MobileActionDispatcher.openCamera(context) }
                "Camera Opened" to buildJsonObject {
                    put("status", JsonPrimitive("launched"))
                }
            }

            "open_settings" -> {
                val settingType = args.stringArg("setting_type") ?: "general"
                mainHandler.post { MobileActionDispatcher.openSettings(context, settingType) }
                "Settings: $settingType" to buildJsonObject {
                    put("status", JsonPrimitive("opened"))
                    put("setting_type", JsonPrimitive(settingType))
                }
            }

            "make_phone_call" -> {
                val recipientName = args.stringArg("recipient_name").orEmpty()
                val phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(args.stringArg("phone_number").orEmpty())
                val action = PendingAction.MakeCall(recipientName = recipientName, phoneNumber = phoneNumber)
                mainHandler.post { onRequestPendingAction(action) }
                val label = when {
                    recipientName.isNotBlank() -> "Confirm Call: $recipientName"
                    phoneNumber.isNotBlank() -> "Confirm Call: $phoneNumber"
                    else -> "Confirm Phone Call"
                }
                label to buildJsonObject {
                    put("status", JsonPrimitive("awaiting_user_confirmation"))
                    put("recipient_name", JsonPrimitive(recipientName))
                    put("phone_number", JsonPrimitive(phoneNumber))
                    put("requires_number_input", JsonPrimitive(phoneNumber.isBlank()))
                }
            }

            "send_sms" -> {
                val recipientName = args.stringArg("recipient_name").orEmpty()
                val phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(args.stringArg("phone_number").orEmpty())
                val message = args.stringArg("message").orEmpty()
                val action = PendingAction.SendSms(
                    recipientName = recipientName,
                    phoneNumber = phoneNumber,
                    message = message
                )
                mainHandler.post { onRequestPendingAction(action) }
                val target = recipientName.ifBlank { phoneNumber.ifBlank { "Contact" } }
                "Confirm SMS: $target" to buildJsonObject {
                    put("status", JsonPrimitive("awaiting_user_confirmation"))
                    put("recipient_name", JsonPrimitive(recipientName))
                    put("phone_number", JsonPrimitive(phoneNumber))
                    put("message", JsonPrimitive(message))
                    put("requires_number_input", JsonPrimitive(phoneNumber.isBlank()))
                }
            }

            "send_whatsapp" -> {
                val recipientName = args.stringArg("recipient_name").orEmpty()
                val phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(args.stringArg("phone_number").orEmpty())
                val message = args.stringArg("message").orEmpty()
                val action = PendingAction.SendWhatsApp(
                    recipientName = recipientName,
                    phoneNumber = phoneNumber,
                    message = message
                )
                mainHandler.post { onRequestPendingAction(action) }
                val target = recipientName.ifBlank { phoneNumber.ifBlank { "WhatsApp" } }
                "Confirm WhatsApp: $target" to buildJsonObject {
                    put("status", JsonPrimitive("awaiting_user_confirmation"))
                    put("recipient_name", JsonPrimitive(recipientName))
                    put("phone_number", JsonPrimitive(phoneNumber))
                    put("message", JsonPrimitive(message))
                }
            }

            "create_note" -> {
                val title = args.stringArg("title")?.ifBlank { "Voice Note" } ?: "Voice Note"
                val content = args.stringArg("content")?.ifBlank { title } ?: title
                val noteId = repository.saveNote(title, content)
                "Note Saved: $title" to buildJsonObject {
                    put("status", JsonPrimitive("saved"))
                    put("note_id", JsonPrimitive(noteId))
                    put("title", JsonPrimitive(title))
                    put("content", JsonPrimitive(content))
                }
            }

            "create_task" -> {
                val title = args.stringArg("title")?.ifBlank { "New Task" } ?: "New Task"
                val priority = args.stringArg("priority")?.takeIf { it == "High" || it == "Normal" } ?: "Normal"
                val taskId = repository.saveTask(title, priority)
                "Task Added: $title" to buildJsonObject {
                    put("status", JsonPrimitive("added"))
                    put("task_id", JsonPrimitive(taskId))
                    put("title", JsonPrimitive(title))
                    put("priority", JsonPrimitive(priority))
                }
            }

            "save_memory" -> {
                val key = args.stringArg("key")?.ifBlank { "pref_${System.currentTimeMillis() % 1000}" }
                    ?: "pref_${System.currentTimeMillis() % 1000}"
                val value = args.stringArg("value").orEmpty()
                if (value.isNotBlank()) {
                    repository.saveMemory(key, value)
                }
                "Memory Saved" to buildJsonObject {
                    put("status", JsonPrimitive(if (value.isNotBlank()) "saved" else "ignored_empty"))
                    put("key", JsonPrimitive(key))
                    put("value", JsonPrimitive(value))
                }
            }

            "get_user_context" -> {
                val notes = repository.allNotes.first().take(10)
                val tasks = repository.allTasks.first().take(15)
                val memories = repository.allMemory.first().take(15)

                "Loaded Notes & Tasks" to buildJsonObject {
                    put("notes", JsonArray(notes.map {
                        buildJsonObject {
                            put("title", JsonPrimitive(it.title))
                            put("content", JsonPrimitive(it.content))
                        }
                    }))
                    put("tasks", JsonArray(tasks.map {
                        buildJsonObject {
                            put("title", JsonPrimitive(it.title))
                            put("priority", JsonPrimitive(it.priority))
                            put("completed", JsonPrimitive(it.isCompleted))
                        }
                    }))
                    put("memories", JsonArray(memories.map {
                        buildJsonObject {
                            put("key", JsonPrimitive(it.key))
                            put("value", JsonPrimitive(it.value))
                        }
                    }))
                }
            }

            "navigate_tab" -> {
                val tab = args.stringArg("tab") ?: "assistant"
                mainHandler.post { onNavigateTab(tab) }
                "Opened $tab" to buildJsonObject {
                    put("status", JsonPrimitive("navigated"))
                    put("tab", JsonPrimitive(tab))
                }
            }

            "get_current_datetime" -> {
                val now = Date()
                val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
                val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(now)
                "Time & Date" to buildJsonObject {
                    put("time", JsonPrimitive(timeStr))
                    put("date", JsonPrimitive(dateStr))
                }
            }

            else -> {
                "Unsupported Tool" to buildJsonObject {
                    put("error", JsonPrimitive("Unknown tool: ${call.name}"))
                }
            }
        }

        mainHandler.post { onToolBadge(badge) }

        val responsePart = FunctionResponsePart(
            name = call.name,
            response = payload,
            id = call.id
        )
        return ToolExecutionResult(call.name, badge, responsePart)
    }

    private fun Map<String, kotlinx.serialization.json.JsonElement>.stringArg(key: String): String? {
        return try {
            this[key]?.jsonPrimitive?.contentOrNull?.trim()
        } catch (_: Exception) {
            null
        }
    }
}
