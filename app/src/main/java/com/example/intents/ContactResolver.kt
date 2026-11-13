package com.example.intents

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class ContactMatch(
    val displayName: String,
    val phoneNumber: String
)

object JuliAssistantPrefs {
    private const val PREFS_NAME = "juli_assistant_prefs"
    private const val KEY_DIRECT_CALL_ENABLED = "direct_call_enabled"
    private const val KEY_AUTO_ANNOUNCE_NOTIFS = "auto_announce_notifs"
    private const val KEY_READ_NOTIF_CONTENT = "read_notif_content"

    fun isDirectCallEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DIRECT_CALL_ENABLED, true)
    }

    fun setDirectCallEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DIRECT_CALL_ENABLED, enabled)
            .apply()
    }

    fun isAutoAnnounceNotifications(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ANNOUNCE_NOTIFS, true)
    }

    fun setAutoAnnounceNotifications(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ANNOUNCE_NOTIFS, enabled)
            .apply()
    }

    fun isReadNotificationContent(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_READ_NOTIF_CONTENT, false)
    }

    fun setReadNotificationContent(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_READ_NOTIF_CONTENT, enabled)
            .apply()
    }
}

object ContactResolver {
    private const val TAG = "ContactResolver"

    fun hasReadContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Searches phone contacts by name when READ_CONTACTS is granted.
     * Ranks exact matches first, then prefix matches, then substring matches.
     */
    fun findContactsByName(context: Context, queryName: String, limit: Int = 5): List<ContactMatch> {
        val trimmed = queryName.trim()
        if (trimmed.isEmpty() || !hasReadContactsPermission(context)) {
            return emptyList()
        }

        val lowerQuery = trimmed.lowercase()
        val exactMatches = mutableListOf<ContactMatch>()
        val prefixMatches = mutableListOf<ContactMatch>()
        val partialMatches = mutableListOf<ContactMatch>()
        val seenNumbers = mutableSetOf<String>()

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$trimmed%")

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = if (nameCol >= 0) cursor.getString(nameCol)?.trim().orEmpty() else ""
                    val rawNum = if (numCol >= 0) cursor.getString(numCol)?.trim().orEmpty() else ""
                    val cleanNum = MobileActionDispatcher.sanitizePhoneNumber(rawNum)
                    val normalizedDigits = cleanNum.filter { it.isDigit() }.takeLast(10)

                    if (name.isNotEmpty() && cleanNum.isNotEmpty() && seenNumbers.add(normalizedDigits.ifEmpty { cleanNum })) {
                        val match = ContactMatch(displayName = name, phoneNumber = cleanNum)
                        val lowerName = name.lowercase()
                        when {
                            lowerName == lowerQuery -> exactMatches.add(match)
                            lowerName.startsWith(lowerQuery) -> prefixMatches.add(match)
                            else -> partialMatches.add(match)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }

        return (exactMatches + prefixMatches + partialMatches).take(limit)
    }
}
