package com.example.intents

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import java.util.Locale

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val activityName: String,
    val normalizedName: String
)

data class AppLaunchResult(
    val launched: Boolean,
    val matchedAppName: String? = null,
    val matchedPackageName: String? = null,
    val suggestions: List<String> = emptyList()
)

object InstalledAppLauncher {

    private const val TAG = "InstalledAppLauncher"

    private val fillerTokens = setOf(
        "open", "launch", "start", "run", "please", "pls", "app", "application",
        "the", "my", "karo", "kar", "kardo", "karna", "kholo", "khol", "kholdo",
        "kholna", "chalao", "chala", "chalu", "openkaro", "on", "mera", "meri",
        "phone", "mobile", "me", "mein", "jara", "zara", "ek", "baar",
        "खोलो", "खोल", "दो", "करो", "चलाओ", "चालू", "ओपन", "ऐप", "एप्प", "प्लीज", "जरा", "ज़रा", "मेरा", "मेरी"
    )

    // Multilingual (Hindi Devanagari / Hinglish / Common) aliases mapped to canonical lowercase app keywords
    private val aliasToCanonical: Map<String, List<String>> = mapOf(
        "व्हाट्सएप" to listOf("whatsapp", "wa business", "whatsapp business"),
        "वाट्सएप" to listOf("whatsapp"),
        "whatsapp" to listOf("whatsapp"),
        "watsapp" to listOf("whatsapp"),
        "इंस्टाग्राम" to listOf("instagram"),
        "इंस्टा" to listOf("instagram"),
        "insta" to listOf("instagram"),
        "instagram" to listOf("instagram"),
        "फेसबुक" to listOf("facebook", "fb lite", "facebook lite"),
        "fb" to listOf("facebook"),
        "यूट्यूब" to listOf("youtube"),
        "youtube" to listOf("youtube"),
        "yt" to listOf("youtube"),
        "क्रोम" to listOf("chrome", "google chrome"),
        "गूगल" to listOf("google", "chrome"),
        "मैप" to listOf("maps", "google maps"),
        "मैप्स" to listOf("maps", "google maps"),
        "गूगल मैप" to listOf("maps", "google maps"),
        "गूगल मैप्स" to listOf("maps", "google maps"),
        "पेटीएम" to listOf("paytm"),
        "paytm" to listOf("paytm"),
        "फोनपे" to listOf("phonepe"),
        "फोन पे" to listOf("phonepe"),
        "phone pay" to listOf("phonepe"),
        "गूगल पे" to listOf("gpay", "google pay", "tez"),
        "जीपे" to listOf("gpay", "google pay"),
        "gpay" to listOf("gpay", "google pay"),
        "g pay" to listOf("gpay", "google pay"),
        "टेलीग्राम" to listOf("telegram"),
        "telegram" to listOf("telegram"),
        "स्नैपचैट" to listOf("snapchat"),
        "snapchat" to listOf("snapchat"),
        "स्पॉटिफाई" to listOf("spotify"),
        "spotify" to listOf("spotify"),
        "जियोसावन" to listOf("jiosaavn", "saavn"),
        "फ्लिपकार्ट" to listOf("flipkart"),
        "flipkart" to listOf("flipkart"),
        "अमेजन" to listOf("amazon", "amazon shopping"),
        "अमेज़न" to listOf("amazon", "amazon shopping"),
        "amazon" to listOf("amazon", "amazon shopping"),
        "मीशो" to listOf("meesho"),
        "स्विगी" to listOf("swiggy"),
        "जोमैटो" to listOf("zomato"),
        "ज़ोमैटो" to listOf("zomato"),
        "उबर" to listOf("uber"),
        "ओला" to listOf("ola"),
        "रैपिडो" to listOf("rapido"),
        "प्ले स्टोर" to listOf("play store", "google play store"),
        "प्लेस्टोर" to listOf("play store", "google play store"),
        "playstore" to listOf("play store", "google play store"),
        "जीमेल" to listOf("gmail"),
        "gmail" to listOf("gmail"),
        "ईमेल" to listOf("gmail", "email", "mail"),
        "गैलरी" to listOf("gallery", "photos", "google photos"),
        "फोटो" to listOf("photos", "gallery"),
        "फोटोज" to listOf("photos", "gallery"),
        "फोटोज़" to listOf("photos", "gallery"),
        "कैमरा" to listOf("camera"),
        "कैलकुलेटर" to listOf("calculator"),
        "कैलेंडर" to listOf("calendar"),
        "घड़ी" to listOf("clock", "alarm"),
        "अलार्म" to listOf("clock", "alarm"),
        "क्लॉक" to listOf("clock"),
        "कॉन्टैक्ट" to listOf("contacts"),
        "कॉन्टैक्ट्स" to listOf("contacts"),
        "संपर्क" to listOf("contacts"),
        "डायलर" to listOf("phone", "dialer", "contacts"),
        "फोन" to listOf("phone", "dialer"),
        "मैसेज" to listOf("messages", "messaging", "sms"),
        "मैसेजेस" to listOf("messages", "messaging"),
        "एसएमएस" to listOf("messages", "messaging"),
        "सेटिंग" to listOf("settings"),
        "सेटिंग्स" to listOf("settings"),
        "फाइल" to listOf("files", "file manager", "my files"),
        "फाइल्स" to listOf("files", "file manager", "my files"),
        "फाइल मैनेजर" to listOf("file manager", "files", "my files"),
        "नोट्स" to listOf("notes", "keep notes", "samsung notes"),
        "जूम" to listOf("zoom"),
        "मीट" to listOf("meet", "google meet"),
        "नेटफ्लिक्स" to listOf("netflix"),
        "हॉटस्टार" to listOf("hotstar", "jiohotstar", "disney+ hotstar"),
        "जियोहॉटस्टार" to listOf("jiohotstar", "hotstar"),
        "जियोसिनेमा" to listOf("jiocinema", "jiohotstar"),
        "ट्विटर" to listOf("x", "twitter"),
        "लिंक्डइन" to listOf("linkedin"),
        "डिस्कॉर्ड" to listOf("discord"),
        "चैटजीपीटी" to listOf("chatgpt"),
        "जेमिनी" to listOf("gemini"),
        "बैंक" to listOf("yono", "imobile", "hdfc", "bob world", "bhim"),
        "भीम" to listOf("bhim"),
        "आधार" to listOf("maadhaar"),
        "डिजिलॉकर" to listOf("digilocker"),
        "फ्री फायर" to listOf("free fire", "free fire max"),
        "बीजीएमआई" to listOf("bgmi", "battlegrounds"),
        "पबजी" to listOf("bgmi", "pubg", "battlegrounds")
    )

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val selfPackage = context.packageName
            resolveInfos
                .asSequence()
                .mapNotNull { info ->
                    val activityInfo = info.activityInfo ?: return@mapNotNull null
                    val pkg = activityInfo.packageName ?: return@mapNotNull null
                    if (pkg == selfPackage) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                    if (label.isEmpty()) return@mapNotNull null
                    InstalledAppInfo(
                        appName = label,
                        packageName = pkg,
                        activityName = activityInfo.name,
                        normalizedName = normalize(label)
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase(Locale.ROOT) }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps", e)
            emptyList()
        }
    }

    fun openAppByName(context: Context, spokenQuery: String): AppLaunchResult {
        val cleaned = extractAppTarget(spokenQuery)
        if (cleaned.isBlank()) {
            return AppLaunchResult(launched = false)
        }

        val apps = getInstalledApps(context)
        if (apps.isEmpty()) {
            return AppLaunchResult(launched = false)
        }

        val match = findBestMatch(cleaned, apps)
        if (match != null) {
            val launched = launchApp(context, match)
            if (launched) {
                return AppLaunchResult(
                    launched = true,
                    matchedAppName = match.appName,
                    matchedPackageName = match.packageName
                )
            }
        }

        val suggestions = findSuggestions(cleaned, apps)
        return AppLaunchResult(
            launched = false,
            suggestions = suggestions
        )
    }

    fun launchApp(context: Context, app: InstalledAppInfo): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
            val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(explicitIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching app ${app.packageName}", e)
            false
        }
    }

    fun extractAppTarget(raw: String): String {
        val tokens = raw
            .replace("[,!?.।\"']".toRegex(), " ")
            .trim()
            .split("\\s+".toRegex())
            .filter { token ->
                val lower = token.lowercase(Locale.ROOT)
                token.isNotBlank() && lower !in fillerTokens
            }
        return tokens.joinToString(" ").trim()
    }

    internal fun findBestMatch(query: String, apps: List<InstalledAppInfo>): InstalledAppInfo? {
        val candidates = expandCandidates(query)

        // 1. Exact normalized match
        for (cand in candidates) {
            val candNorm = normalize(cand)
            val candCompact = candNorm.replace(" ", "")
            apps.firstOrNull {
                it.normalizedName == candNorm || it.normalizedName.replace(" ", "") == candCompact
            }?.let { return it }
        }

        // 2. Starts-with or whole-word match
        for (cand in candidates) {
            val candNorm = normalize(cand)
            if (candNorm.length < 2) continue
            apps.firstOrNull { app ->
                app.normalizedName.startsWith(candNorm) ||
                    app.normalizedName.split(" ").any { it == candNorm }
            }?.let { return it }
        }

        // 3. Substring containment or package name match
        for (cand in candidates) {
            val candNorm = normalize(cand)
            val candCompact = candNorm.replace(" ", "")
            if (candCompact.length < 3) continue
            apps.firstOrNull { app ->
                app.normalizedName.contains(candNorm) ||
                    app.normalizedName.replace(" ", "").contains(candCompact) ||
                    (candNorm.length >= 4 && candNorm.contains(app.normalizedName) && app.normalizedName.length >= 3) ||
                    app.packageName.lowercase(Locale.ROOT).split('.').any { it == candCompact }
            }?.let { return it }
        }

        // 4. Fuzzy Levenshtein similarity for slight speech transcription errors
        var bestApp: InstalledAppInfo? = null
        var bestScore = 0.0
        for (cand in candidates) {
            val candCompact = normalize(cand).replace(" ", "")
            if (candCompact.length < 4) continue
            for (app in apps) {
                val appCompact = app.normalizedName.replace(" ", "")
                if (appCompact.length < 3) continue
                val sim = similarity(candCompact, appCompact)
                if (sim > bestScore) {
                    bestScore = sim
                    bestApp = app
                }
            }
        }
        return if (bestScore >= 0.74) bestApp else null
    }

    private fun findSuggestions(query: String, apps: List<InstalledAppInfo>): List<String> {
        val candCompact = normalize(query).replace(" ", "")
        if (candCompact.isEmpty()) return apps.take(5).map { it.appName }
        return apps
            .map { app ->
                val appCompact = app.normalizedName.replace(" ", "")
                val bonus = if (appCompact.contains(candCompact) || candCompact.contains(appCompact)) 0.35 else 0.0
                app.appName to (similarity(candCompact, appCompact) + bonus)
            }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }
    }

    private fun expandCandidates(query: String): List<String> {
        val result = linkedSetOf<String>()
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        result.add(trimmed)
        result.add(lower)

        aliasToCanonical[lower]?.let { result.addAll(it) }
        aliasToCanonical[trimmed]?.let { result.addAll(it) }

        // Check if any alias phrase appears inside the query
        aliasToCanonical.forEach { (alias, targets) ->
            if (lower.contains(alias) || trimmed.contains(alias)) {
                result.addAll(targets)
            }
        }
        return result.toList()
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun similarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshtein(a, b)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = temp
            }
        }
        return dp[b.length]
    }
}
