package com.example.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream

class JuliImageStudioClient(private val context: Context) {

    companion object {
        private const val TAG = "JuliImageStudio"
        private const val MODEL_NAME = "gemini-nano-banana-2.1"
    }

    private fun buildModel() = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = MODEL_NAME,
        generationConfig = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
    )

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1"
    ): Pair<Bitmap, File> = withContext(Dispatchers.IO) {
        if (!ensureFirebaseInitialized(context)) {
            throw IllegalStateException(
                "Firebase config (google-services.json) इस APK में मौजूद नहीं है।"
            )
        }
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            throw IllegalArgumentException("कृपया इमेज बनाने के लिए प्रॉम्प्ट लिखें।")
        }

        val augmentedPrompt = if (aspectRatio == "1:1") {
            cleanPrompt
        } else {
            "$cleanPrompt (aspect ratio: $aspectRatio)"
        }

        try {
            withTimeout(60_000L) {
                val response = buildModel().generateContent(augmentedPrompt)
                val bitmap = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
                    ?: throw IllegalStateException("मॉडल ने कोई इमेज नहीं भेजी। कृपया दूसरा प्रॉम्प्ट आज़माएँ।")
                val file = saveBitmapToCache(bitmap, "juli_img")
                bitmap to file
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("Image generation timed out after 60 seconds. Please retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            val raw = e.localizedMessage ?: "Image generation failed."
            if (raw.contains("App Check", ignoreCase = true) || raw.contains("403")) {
                val diag = AppCheckDiagnostics.status.value
                throw IllegalStateException("$raw [${diag.providerName}: ${diag.diagnosticMessage}]", e)
            }
            throw e
        }
    }

    suspend fun editImage(
        inputBitmap: Bitmap,
        editInstruction: String
    ): Pair<Bitmap, File> = withContext(Dispatchers.IO) {
        if (!ensureFirebaseInitialized(context)) {
            throw IllegalStateException(
                "Firebase config (google-services.json) इस APK में मौजूद नहीं है।"
            )
        }
        val cleanInstruction = editInstruction.trim()
        if (cleanInstruction.isEmpty()) {
            throw IllegalArgumentException("कृपया बताएं कि फोटो में क्या बदलाव करना है।")
        }

        try {
            withTimeout(60_000L) {
                val response = buildModel().generateContent(
                    content {
                        image(inputBitmap)
                        text(cleanInstruction)
                    }
                )
                val bitmap = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
                    ?: throw IllegalStateException("मॉडल से एडिटेड इमेज प्राप्त नहीं हुई।")
                val file = saveBitmapToCache(bitmap, "juli_edit")
                bitmap to file
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image editing timed out", e)
            throw IllegalStateException("Image editing timed out after 60 seconds. Please retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image editing failed", e)
            val raw = e.localizedMessage ?: "Image editing failed."
            if (raw.contains("App Check", ignoreCase = true) || raw.contains("403")) {
                val diag = AppCheckDiagnostics.status.value
                throw IllegalStateException("$raw [${diag.providerName}: ${diag.diagnosticMessage}]", e)
            }
            throw e
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap, prefix: String): File {
        val dir = File(context.cacheDir, "generated_images").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return file
    }

    fun shareFile(file: File, mimeType: String, title: String): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share media file", e)
            false
        }
    }
}
