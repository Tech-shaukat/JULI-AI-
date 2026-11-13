package com.example.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.repository.JuliRepository
import com.example.intents.ContactResolver
import com.example.intents.JuliAssistantPrefs
import com.example.intents.MobileActionDispatcher
import com.example.intents.PendingAction
import com.example.service.NotificationCenterStore
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
                - Whenever the user asks to perform an action on the phone (open YouTube, search Google/Chrome, open Camera, open WiFi/Bluetooth/Display/Sound settings, set Google Maps navigation, read incoming messages/notifications, search contacts, save a note, add a task, remember personal info, check their notes/tasks, switch app tabs, make a phone call, send an SMS, or send a WhatsApp message), IMMEDIATELY invoke the corresponding tool function and then briefly confirm what you did.
                - Direct Calling & Contacts:
                  * If the user asks to call someone by name (e.g., "Rahul ko call karo", "Call Mom"), call search_contacts or make_phone_call with recipient_name. When CALL_PHONE & READ_CONTACTS permissions are granted and a unique number is found, JULI places the call directly; otherwise a confirmation dialog opens on screen.
                - Google Maps Navigation:
                  * When the user asks to set a map or navigate somewhere (e.g., "Patna Junction ka map set kar do", "Navigate to India Gate"), IMMEDIATELY call set_map_navigation with the destination.
                - Reading Messages & Notifications:
                  * When the user asks to read messages or asks whose notification arrived (e.g., "Mera message padh kar sunao", "Kiska notification aaya hai?"), IMMEDIATELY call read_notifications and read back the sender, app, and message clearly. If notification access is disabled, politely tell them to enable Notification Reader in the Actions -> Alerts tab.
                - SMS & WhatsApp Confirmation:
                  * For user privacy and safety, send_sms and send_whatsapp prepare a confirmation dialog on screen (where the user can review or edit the message) before opening SMS or WhatsApp.
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
        ),
        FunctionDeclaration(
            name = "search_contacts",
            description = "Searches the phone's Contacts and saved user memory by name to find a contact's phone number.",
            parameters = mapOf(
                "name_query" to Schema.string(description = "Name of the contact to search, e.g. 'Rahul' or 'Papa'.")
            ),
            optionalParameters = emptyList()
        ),
        FunctionDeclaration(
            name = "set_map_navigation",
            description = "Sets a Google Maps route or searches a place and starts navigation on the phone.",
            parameters = mapOf(
                "destination" to Schema.string(description = "Destination address, landmark, or city, e.g. 'India Gate, New Delhi' or 'Patna Junction'."),
                "travel_mode" to Schema.enumeration(
                    values = listOf("driving", "two_wheeler", "walking", "bicycling"),
                    description = "Mode of travel."
                ),
                "action_type" to Schema.enumeration(
                    values = listOf("navigate", "search"),
                    description = "Use 'navigate' to start turn-by-turn route or 'search' to view place on map."
                )
            ),
            optionalParameters = listOf("travel_mode", "action_type")
        ),
        FunctionDeclaration(
            name = "read_notifications",
            description = "Reads recent incoming notifications and messages (WhatsApp, SMS, Gmail, Calls, etc.) captured by JULI Notification Reader.",
            parameters = mapOf(
                "app_filter" to Schema.string(description = "Optional app filter like 'whatsapp', 'sms', 'messages', 'gmail', or 'all'."),
                "limit" to Schema.integer(description = "Maximum number of recent notifications to return (default 5).")
            ),
            optionalParameters = listOf("app_filter", "limit")
        )
    )

    val tool: Tool = Tool.functionDeclarations(functionDeclarations)
}

class JuliToolExecutor(
    private val context: Context,
    private val repository: JuliRepository,
    private val onNavigateTab: (String) -> Unit = {},
    private val onToolBadge: (String) -> Unit = {},
    private val onRequestPendingAction: (PendingAction) -> Unit = {},
    private val onDirectCallRequested: ((String, String) -> Unit)? = null,
    private val onMapDestinationSet: (String, String) -> Unit = { _, _ -> }
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
                var recipientName = args.stringArg("recipient_name").orEmpty()
                var phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(args.stringArg("phone_number").orEmpty())
                var matchedCount = 0

                if (phoneNumber.isBlank() && recipientName.isNotBlank()) {
                    val contactMatches = ContactResolver.findContactsByName(context, recipientName)
                    matchedCount = contactMatches.size
                    if (contactMatches.size == 1) {
                        recipientName = contactMatches.first().displayName
                        phoneNumber = contactMatches.first().phoneNumber
                    } else if (contactMatches.isNotEmpty()) {
                        recipientName = contactMatches.first().displayName
                        phoneNumber = contactMatches.first().phoneNumber
                    } else {
                        val memMatch = findPhoneInMemory(recipientName)
                        if (memMatch.isNotBlank()) phoneNumber = memMatch
                    }
                }

                val canDirectCall = phoneNumber.isNotBlank() &&
                    JuliAssistantPrefs.isDirectCallEnabled(context) &&
                    ContactResolver.hasCallPhonePermission(context) &&
                    matchedCount <= 1

                if (canDirectCall) {
                    mainHandler.post {
                        if (onDirectCallRequested != null) {
                            onDirectCallRequested.invoke(recipientName, phoneNumber)
                        } else {
                            MobileActionDispatcher.placeDirectCall(context, phoneNumber)
                        }
                    }
                    val target = recipientName.ifBlank { phoneNumber }
                    "Calling: $target" to buildJsonObject {
                        put("status", JsonPrimitive("calling_directly"))
                        put("recipient_name", JsonPrimitive(recipientName))
                        put("phone_number", JsonPrimitive(phoneNumber))
                    }
                } else {
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
                        put("contacts_permission_granted", JsonPrimitive(ContactResolver.hasReadContactsPermission(context)))
                    }
                }
            }

            "send_sms" -> {
                var recipientName = args.stringArg("recipient_name").orEmpty()
                var phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(args.stringArg("phone_number").orEmpty())
                if (phoneNumber.isBlank() && recipientName.isNotBlank()) {
                    val contactMatch = ContactResolver.findContactsByName(context, recipientName).firstOrNull()
                    if (contactMatch != null) {
                        recipientName = contactMatch.displayName
                        phoneNumber = contactMatch.phoneNumber
                    } else {
                        phoneNumber = findPhoneInMemory(recipientName)
                    }
                }
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
                var recipientName = args.stringArg("recipient_name").orEmpty()
                var phoneNumber = MobileActionDispatcher.sanitizePhoneNumber(args.stringArg("phone_number").orEmpty())
                if (phoneNumber.isBlank() && recipientName.isNotBlank()) {
                    val contactMatch = ContactResolver.findContactsByName(context, recipientName).firstOrNull()
                    if (contactMatch != null) {
                        recipientName = contactMatch.displayName
                        phoneNumber = contactMatch.phoneNumber
                    } else {
                        phoneNumber = findPhoneInMemory(recipientName)
                    }
                }
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

            "search_contacts" -> {
                val query = args.stringArg("name_query").orEmpty()
                val hasPerm = ContactResolver.hasReadContactsPermission(context)
                val matches = ContactResolver.findContactsByName(context, query)
                val memNumber = if (matches.isEmpty()) findPhoneInMemory(query) else ""
                "Contacts: $query" to buildJsonObject {
                    put("contacts_permission_granted", JsonPrimitive(hasPerm))
                    put("matches", JsonArray(matches.map {
                        buildJsonObject {
                            put("name", JsonPrimitive(it.displayName))
                            put("phone_number", JsonPrimitive(it.phoneNumber))
                        }
                    }))
                    put("memory_phone_number", JsonPrimitive(memNumber))
                }
            }

            "set_map_navigation" -> {
                val destination = args.stringArg("destination").orEmpty()
                val travelMode = args.stringArg("travel_mode") ?: "driving"
                val actionType = args.stringArg("action_type") ?: "navigate"
                if (destination.isNotBlank()) {
                    mainHandler.post {
                        onMapDestinationSet(destination, travelMode)
                        if (actionType == "search") {
                            MobileActionDispatcher.searchMapsPlace(context, destination)
                        } else {
                            MobileActionDispatcher.openMapsNavigation(context, destination, travelMode)
                        }
                    }
                }
                val label = if (destination.isBlank()) "Maps" else "Map: $destination"
                label to buildJsonObject {
                    put("status", JsonPrimitive(if (destination.isNotBlank()) "navigation_started" else "missing_destination"))
                    put("destination", JsonPrimitive(destination))
                    put("travel_mode", JsonPrimitive(travelMode))
                }
            }

            "read_notifications" -> {
                val filter = args.stringArg("app_filter")
                val limit = args["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5
                val accessEnabled = NotificationCenterStore.isNotificationAccessEnabled(context)
                val items = NotificationCenterStore.filterByApp(filter, limit)
                val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
                "Notifications (${items.size})" to buildJsonObject {
                    put("notification_access_enabled", JsonPrimitive(accessEnabled))
                    put("count", JsonPrimitive(items.size))
                    put("notifications", JsonArray(items.map {
                        buildJsonObject {
                            put("app", JsonPrimitive(it.appName))
                            put("sender", JsonPrimitive(it.sender))
                            put("message", JsonPrimitive(it.message))
                            put("time", JsonPrimitive(timeFmt.format(Date(it.timestamp))))
                        }
                    }))
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

    private fun Map<String, kotlinx.serialization.json.JsonElement>.stringArg(key: String): String? {
        return try {
            this[key]?.jsonPrimitive?.contentOrNull?.trim()
        } catch (_: Exception) {
            null
        }
    }
}
