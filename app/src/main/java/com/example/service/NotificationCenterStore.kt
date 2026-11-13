package com.example.service

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val message: String,
    val timestamp: Long
)

object NotificationCenterStore {
    private const val MAX_NOTIFICATIONS = 30

    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<CapturedNotification>(extraBufferCapacity = 16)
    val incomingEvents: SharedFlow<CapturedNotification> = _incomingEvents.asSharedFlow()

    fun isNotificationAccessEnabled(context: Context): Boolean {
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun record(item: CapturedNotification) {
        val current = _notifications.value
        val duplicate = current.firstOrNull()?.let {
            it.packageName == item.packageName &&
                it.sender == item.sender &&
                it.message == item.message &&
                (item.timestamp - it.timestamp) < 4_000L
        } == true

        if (duplicate) return

        val updated = (listOf(item) + current)
            .distinctBy { it.id }
            .take(MAX_NOTIFICATIONS)
        _notifications.value = updated
        _incomingEvents.tryEmit(item)
    }

    fun clearAll() {
        _notifications.value = emptyList()
    }

    fun filterByApp(appFilter: String?, limit: Int = 5): List<CapturedNotification> {
        val list = _notifications.value
        val filter = appFilter?.trim()?.lowercase().orEmpty()
        val filtered = if (filter.isEmpty() || filter == "all") {
            list
        } else {
            list.filter {
                it.appName.lowercase().contains(filter) ||
                    it.packageName.lowercase().contains(filter) ||
                    (filter == "sms" && (it.packageName.contains("messaging") || it.packageName.contains("mms") || it.appName.lowercase().contains("message")))
            }
        }
        return filtered.take(limit.coerceIn(1, 15))
    }
}
