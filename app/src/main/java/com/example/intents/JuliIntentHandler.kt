package com.example.intents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URLEncoder

object JuliIntentHandler {

    /**
     * Opens YouTube with an optional search query.
     * Tries the native YouTube app first, and falls back to browser if not installed.
     */
    fun openYouTube(context: Context, query: String? = null): Boolean {
        val trimmedQuery = query?.trim()

        return try {
            if (trimmedQuery.isNullOrEmpty()) {
                // Try launching YouTube app directly
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }

                // Fallback to vnd.youtube:
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (appIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appIntent)
                    return true
                }

                // Web browser fallback
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } else {
                val encoded = URLEncoder.encode(trimmedQuery, "UTF-8")

                // Try YouTube App Search Intent
                val appSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", trimmedQuery)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                if (appSearchIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appSearchIntent)
                    return true
                }

                // Try Uri view with YouTube app
                val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uriIntent)
                true
            }
        } catch (e: Exception) {
            Log.e("JuliIntent", "Failed to open YouTube", e)
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            } catch (fallbackEx: Exception) {
                Log.e("JuliIntent", "Fallback browser open failed", fallbackEx)
                false
            }
        }
    }

    /**
     * Opens Google search for general queries
     */
    fun openGoogleSearch(context: Context, query: String): Boolean {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("JuliIntent", "Failed to open Google search", e)
            false
        }
    }

    /**
     * Opens app settings for permissions
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("JuliIntent", "Failed to open app settings", e)
        }
    }
}
