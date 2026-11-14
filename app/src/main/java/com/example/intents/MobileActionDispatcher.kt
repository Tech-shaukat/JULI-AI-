package com.example.intents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.net.URLEncoder

object MobileActionDispatcher {

    private const val TAG = "MobileAction"

    /**
     * Strips formatting characters from a phone number while keeping leading '+' and digits.
     */
    fun sanitizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder()
        trimmed.forEachIndexed { index, ch ->
            if (ch.isDigit() || (ch == '+' && index == 0)) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Formats phone digits for WhatsApp wa.me / api.whatsapp.com URLs.
     * Defaults 10-digit Indian numbers to country code 91 when no country code is specified.
     */
    fun formatWhatsAppDigits(raw: String): String {
        val clean = sanitizePhoneNumber(raw)
        if (clean.isEmpty()) return ""
        val digitsOnly = clean.filter { it.isDigit() }
        return when {
            clean.startsWith("+") -> digitsOnly
            digitsOnly.length == 10 -> "91$digitsOnly"
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> "91${digitsOnly.drop(1)}"
            else -> digitsOnly
        }
    }

    fun buildDialIntent(phoneNumber: String): Intent {
        val clean = sanitizePhoneNumber(phoneNumber)
        val uri = if (clean.isNotEmpty()) Uri.parse("tel:$clean") else null
        return Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildDirectCallIntent(phoneNumber: String): Intent {
        val clean = sanitizePhoneNumber(phoneNumber)
        return Intent(Intent.ACTION_CALL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildSmsIntent(phoneNumber: String, message: String): Intent {
        val clean = sanitizePhoneNumber(phoneNumber)
        val uri = Uri.parse("smsto:$clean")
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the system Phone Dialer pre-filled with the number (Play-compliant, zero restricted permissions).
     */
    fun openDialer(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = buildDialIntent(phoneNumber)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening phone dialer", e)
            false
        }
    }

    /**
     * Places a direct phone call when CALL_PHONE permission is granted; safely falls back to Dialer otherwise.
     */
    fun placeDirectCall(context: Context, phoneNumber: String): Boolean {
        val clean = sanitizePhoneNumber(phoneNumber)
        if (clean.isEmpty()) return openDialer(context, "")

        if (!ContactResolver.hasCallPhonePermission(context)) {
            return openDialer(context, clean)
        }
        return try {
            context.startActivity(buildDirectCallIntent(clean))
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "CALL_PHONE permission missing at runtime, falling back to Dialer", e)
            openDialer(context, clean)
        } catch (e: Exception) {
            Log.e(TAG, "Error placing direct call", e)
            openDialer(context, clean)
        }
    }

    fun openMapsNavigation(context: Context, destination: String, travelMode: String = "driving"): Boolean {
        val trimmed = destination.trim()
        if (trimmed.isEmpty()) return false
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val modeChar = when (travelMode.lowercase()) {
            "walking", "w" -> "w"
            "bicycling", "b" -> "b"
            "two_wheeler", "bike", "l" -> "l"
            else -> "d"
        }

        return try {
            val navUri = Uri.parse("google.navigation:q=$encoded&mode=$modeChar")
            val mapsNavIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapsNavIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsNavIntent)
                return true
            }

            val geoUri = Uri.parse("geo:0,0?q=$encoded")
            val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (geoIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(geoIntent)
                return true
            }

            openWebUrl(context, "https://www.google.com/maps/dir/?api=1&destination=$encoded")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Google Maps navigation", e)
            openWebUrl(context, "https://www.google.com/maps/dir/?api=1&destination=$encoded")
        }
    }

    fun searchMapsPlace(context: Context, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return try {
            val geoUri = Uri.parse("geo:0,0?q=$encoded")
            val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapsIntent)
                return true
            }
            openWebUrl(context, "https://www.google.com/maps/search/?api=1&query=$encoded")
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Google Maps", e)
            openWebUrl(context, "https://www.google.com/maps/search/?api=1&query=$encoded")
        }
    }

    fun openNotificationListenerSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Notification Listener settings", e)
            openSettings(context, "general")
        }
    }

    /**
     * Opens the system SMS composer pre-filled with recipient and body.
     */
    fun sendSms(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val intent = buildSmsIntent(phoneNumber, message)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening SMS composer", e)
            false
        }
    }

    /**
     * Opens WhatsApp (or WhatsApp Business) pre-filled with recipient and/or message.
     * If phoneNumber is omitted, opens WhatsApp's contact picker with the message text.
     */
    fun sendWhatsApp(context: Context, phoneNumber: String, message: String): Boolean {
        val waDigits = formatWhatsAppDigits(phoneNumber)
        val encodedMsg = URLEncoder.encode(message, "UTF-8")

        return try {
            if (waDigits.isNotEmpty()) {
                val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$waDigits&text=$encodedMsg")
                for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                    val pkgIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (pkgIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(pkgIntent)
                        return true
                    }
                }
                val fallbackIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } else {
                for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (sendIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(sendIntent)
                        return true
                    }
                }
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    },
                    "Send via WhatsApp"
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching WhatsApp", e)
            false
        }
    }

    /**
     * Opens YouTube with optional query
     */
    fun openYouTube(context: Context, query: String? = null): Boolean {
        val trimmed = query?.trim()
        return try {
            if (trimmed.isNullOrEmpty()) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
                openWebUrl(context, "https://www.youtube.com")
            } else {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val appSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", trimmed)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (appSearchIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appSearchIntent)
                    return true
                }
                openWebUrl(context, "https://www.youtube.com/results?search_query=$encoded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening YouTube", e)
            openWebUrl(context, "https://m.youtube.com")
        }
    }

    /**
     * Opens Chrome or default browser
     */
    fun openChrome(context: Context, queryOrUrl: String? = null): Boolean {
        return try {
            val targetUrl = when {
                queryOrUrl.isNullOrBlank() -> "https://www.google.com"
                queryOrUrl.startsWith("http://") || queryOrUrl.startsWith("https://") -> queryOrUrl
                else -> "https://www.google.com/search?q=" + URLEncoder.encode(queryOrUrl.trim(), "UTF-8")
            }

            val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (chromeIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chromeIntent)
                return true
            }
            openWebUrl(context, targetUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Chrome", e)
            false
        }
    }

    /**
     * Opens Camera app
     */
    fun openCamera(context: Context): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Camera", e)
            false
        }
    }

    /**
     * Opens system settings (WiFi, Bluetooth, Display, Sound, General)
     */
    fun openSettings(context: Context, type: String): Boolean {
        return try {
            val action = when (type.lowercase()) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening settings $type", e)
            false
        }
    }

    fun openAppByName(context: Context, appQuery: String): AppLaunchResult {
        return InstalledAppLauncher.openAppByName(context, appQuery)
    }

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        return InstalledAppLauncher.getInstalledApps(context)
    }

    fun openWebUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening web url", e)
            false
        }
    }
}
