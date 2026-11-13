package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class VideoGenerationResult(
    val videoFile: File,
    val interactionId: String?,
    val aspectRatio: String,
    val summaryText: String?
)

class JuliVideoStudioClient(private val context: Context) {

    companion object {
        private const val TAG = "JuliVideoStudio"
        private const val MODEL_NAME = "gemini-omni-1.2-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(): Boolean {
        val key = BuildConfig.VIDEO_STUDIO_API_KEY.trim()
        return key.isNotEmpty() && !key.startsWith("YOUR_")
    }

    suspend fun generateVideo(
        prompt: String,
        aspectRatio: String = "16:9",
        previousInteractionId: String? = null
    ): VideoGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.VIDEO_STUDIO_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey.startsWith("YOUR_")) {
            throw IllegalStateException(
                "Video generation (gemini-omni-1.2-flash) के लिए AI Studio Secrets में VIDEO_STUDIO_API_KEY सेट करें।"
            )
        }

        val normalizedRatio = if (aspectRatio == "9:16") "9:16" else "16:9"
        val promptWithRatio = buildString {
            append(prompt.trim())
            if (!prompt.contains("16:9") && !prompt.contains("9:16")) {
                append(" (aspect ratio: $normalizedRatio)")
            }
        }

        val payload = JSONObject().apply {
            put("model", MODEL_NAME)
            put("input", promptWithRatio)
            if (!previousInteractionId.isNullOrBlank()) {
                put("previous_interaction_id", previousInteractionId)
            }
            put("generation_config", JSONObject().apply {
                put("response_modalities", JSONArray().apply {
                    put("video")
                    put("audio")
                    put("text")
                })
                put("aspect_ratio", normalizedRatio)
            })
        }

        try {
            withTimeout(180_000L) {
                val requestBody = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/interactions")
                    .addHeader("x-goog-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                var responseJson = executeJsonRequest(request)
                var interactionName = responseJson.optString("name").ifBlank { null }
                var interactionId = responseJson.optString("id").ifBlank {
                    interactionName?.substringAfterLast('/')
                }

                // Poll if the Interactions API returned an in-progress background job
                var status = responseJson.optString("status").lowercase()
                var pollCount = 0
                while ((status == "in_progress" || status == "pending" || status == "running") &&
                    !interactionName.isNullOrBlank() &&
                    pollCount < 30
                ) {
                    pollCount++
                    delay(4_000L)
                    val pollPath = if (interactionName.startsWith("interactions/")) {
                        interactionName
                    } else {
                        "interactions/$interactionName"
                    }
                    val pollRequest = Request.Builder()
                        .url("$BASE_URL/$pollPath")
                        .addHeader("x-goog-api-key", apiKey)
                        .get()
                        .build()
                    responseJson = executeJsonRequest(pollRequest)
                    status = responseJson.optString("status").lowercase()
                }

                if (status == "failed" || status == "error") {
                    val errObj = responseJson.optJSONObject("error")
                    val msg = errObj?.optString("message")?.ifBlank { null }
                        ?: "Video generation failed ($status)."
                    throw IllegalStateException(msg)
                }

                val outputs = responseJson.optJSONArray("outputs")
                    ?: throw IllegalStateException("Interactions API returned no outputs.")

                var videoFile: File? = null
                var summaryText: String? = null

                for (i in 0 until outputs.length()) {
                    val out = outputs.optJSONObject(i) ?: continue
                    val type = out.optString("type").lowercase()
                    val mime = out.optString("mime_type").lowercase()
                    when {
                        type == "text" -> {
                            val txt = out.optString("text").ifBlank { out.optString("data") }
                            if (txt.isNotBlank()) summaryText = txt
                        }
                        type == "video" || mime.startsWith("video/") -> {
                            val inlineData = out.optString("data")
                            val uri = out.optString("uri").ifBlank { out.optString("url") }
                            videoFile = when {
                                inlineData.isNotBlank() -> writeBase64Video(inlineData)
                                uri.isNotBlank() -> downloadVideoUri(uri, apiKey)
                                else -> null
                            }
                        }
                    }
                }

                val resolvedFile = videoFile
                    ?: throw IllegalStateException("Model completed without a video payload.")

                VideoGenerationResult(
                    videoFile = resolvedFile,
                    interactionId = interactionId,
                    aspectRatio = normalizedRatio,
                    summaryText = summaryText
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video generation failed", e)
            throw e
        }
    }

    private fun executeJsonRequest(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedErr = try {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                throw IllegalStateException(
                    parsedErr?.ifBlank { null }
                        ?: "Interactions API HTTP ${response.code}: ${rawBody.take(200)}"
                )
            }
            return JSONObject(rawBody)
        }
    }

    private fun writeBase64Video(base64Data: String): File {
        val dir = File(context.cacheDir, "generated_videos").apply { mkdirs() }
        val file = File(dir, "juli_video_${System.currentTimeMillis()}.mp4")
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        file.writeBytes(bytes)
        return file
    }

    private fun downloadVideoUri(uri: String, apiKey: String): File {
        val req = Request.Builder()
            .url(uri)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()
        httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download generated video (${response.code}).")
            }
            val bytes = response.body?.bytes()
                ?: throw IllegalStateException("Empty video download response.")
            val dir = File(context.cacheDir, "generated_videos").apply { mkdirs() }
            val file = File(dir, "juli_video_${System.currentTimeMillis()}.mp4")
            file.writeBytes(bytes)
            return file
        }
    }
}
