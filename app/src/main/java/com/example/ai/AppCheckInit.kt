package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

data class AppCheckStatus(
    val providerName: String = "Initializing...",
    val hasSiteKey: Boolean = false,
    val tokenReady: Boolean = false,
    val diagnosticMessage: String = "Checking App Check..."
)

object AppCheckDiagnostics {
    private val _status = MutableStateFlow(AppCheckStatus())
    val status: StateFlow<AppCheckStatus> = _status.asStateFlow()

    fun update(status: AppCheckStatus) {
        _status.value = status
    }
}

fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            val initialized = FirebaseApp.initializeApp(appContext)
            if (initialized == null) {
                Log.e(TAG, "FirebaseApp.initializeApp returned null; google-services.json may be missing from APK.")
                false
            } else {
                true
            }
        } else {
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize FirebaseApp", e)
        false
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (!ensureFirebaseInitialized(context)) {
            Log.e(TAG, "Skipping App Check setup because FirebaseApp could not be initialized.")
            AppCheckDiagnostics.update(
                AppCheckStatus(
                    providerName = "Not Initialized",
                    hasSiteKey = false,
                    tokenReady = false,
                    diagnosticMessage = "google-services.json missing in APK"
                )
            )
            return
        }

        val siteKey = resolveRecaptchaSiteKey(context)
        val hasSiteKey = !siteKey.isNullOrBlank()
        var activeProvider = "None"

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            if (!debugToken.isNullOrEmpty()) {
                // Must use synchronous commit() before installing DebugAppCheckProviderFactory
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                activeProvider = "Debug Provider (Injected Token)"
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            } else if (prefs.contains(DEBUG_SECRET_KEY) || isRunningOnEmulator() || !hasSiteKey) {
                Log.w(TAG, "Using DebugAppCheckProviderFactory (cached secret, emulator, or missing siteKey).")
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                activeProvider = if (prefs.contains(DEBUG_SECRET_KEY)) {
                    "Debug Provider (Cached Token)"
                } else if (!hasSiteKey) {
                    "Debug Provider (Missing recaptcha_site_key)"
                } else {
                    "Debug Provider (Emulator)"
                }
            } else {
                // Sideloaded debug APK on a physical phone without injected debug token:
                // Use provisioned reCAPTCHA Enterprise site key so App Check attestation succeeds on real devices.
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey!!)
                )
                activeProvider = "reCAPTCHA Enterprise (Debug APK)"
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed for physical device.")
            }
        } else {
            if (hasSiteKey) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey!!)
                )
                activeProvider = "reCAPTCHA Enterprise (Release)"
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            } else {
                activeProvider = "Missing recaptcha_site_key"
                Log.e(TAG, "recaptcha_site_key string resource missing or blank.")
            }
        }

        AppCheckDiagnostics.update(
            AppCheckStatus(
                providerName = activeProvider,
                hasSiteKey = hasSiteKey,
                tokenReady = false,
                diagnosticMessage = "Warming up $activeProvider..."
            )
        )
        refreshAppCheckToken(forceRefresh = false, providerName = activeProvider, hasSiteKey = hasSiteKey)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckDiagnostics.update(
            AppCheckStatus(
                providerName = "Error",
                hasSiteKey = false,
                tokenReady = false,
                diagnosticMessage = e.localizedMessage ?: "App Check init error"
            )
        )
    }
}

fun refreshAppCheckToken(
    forceRefresh: Boolean = true,
    providerName: String = AppCheckDiagnostics.status.value.providerName,
    hasSiteKey: Boolean = AppCheckDiagnostics.status.value.hasSiteKey
) {
    try {
        FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result?.token.orEmpty()
                    val isPlaceholder = token.isBlank() || token.startsWith("eyJlcnJvciI")
                    if (isPlaceholder) {
                        Log.w(TAG, "App Check returned placeholder/error token via $providerName")
                        AppCheckDiagnostics.update(
                            AppCheckStatus(
                                providerName = providerName,
                                hasSiteKey = hasSiteKey,
                                tokenReady = false,
                                diagnosticMessage = "Attestation failed ($providerName). Ensure firebase_applet_config.xml is in APK and device has Play Services/Internet."
                            )
                        )
                    } else {
                        Log.d(TAG, "App Check token verified successfully via $providerName")
                        AppCheckDiagnostics.update(
                            AppCheckStatus(
                                providerName = providerName,
                                hasSiteKey = hasSiteKey,
                                tokenReady = true,
                                diagnosticMessage = "Valid token active ($providerName)"
                            )
                        )
                    }
                } else {
                    val msg = task.exception?.localizedMessage ?: "Unknown App Check token failure"
                    Log.e(TAG, "App Check getAppCheckToken failed ($providerName): $msg", task.exception)
                    AppCheckDiagnostics.update(
                        AppCheckStatus(
                            providerName = providerName,
                            hasSiteKey = hasSiteKey,
                            tokenReady = false,
                            diagnosticMessage = "$providerName error: $msg"
                        )
                    )
                }
            }
    } catch (e: Exception) {
        Log.e(TAG, "Failed requesting App Check token", e)
        AppCheckDiagnostics.update(
            AppCheckStatus(
                providerName = providerName,
                hasSiteKey = hasSiteKey,
                tokenReady = false,
                diagnosticMessage = e.localizedMessage ?: "App Check refresh failed"
            )
        )
    }
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return context.getString(resId).takeIf { it.isNotBlank() }
}

private fun isRunningOnEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
        Build.FINGERPRINT.startsWith("unknown", ignoreCase = true) ||
        Build.MODEL.contains("google_sdk", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
        (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
        Build.PRODUCT.contains("sdk", ignoreCase = true)
}
