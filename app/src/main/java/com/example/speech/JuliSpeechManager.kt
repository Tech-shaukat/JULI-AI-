package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class JuliSpeechManager(
    private val context: Context,
    private val onStateChange: (SpeechState) -> Unit,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onRmsLevel: (Float) -> Unit,
    private val onError: (String) -> Unit
) {
    sealed class SpeechState {
        object Idle : SpeechState()
        object Listening : SpeechState()
        object Processing : SpeechState()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastPartialText: String? = null
    private var hasBeginningOfSpeech = false
    private var readyTimestampMs = 0L
    private var retryCount = 0
    private var activeLanguageCode = "hi"

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(languageCode: String = "hi") {
        activeLanguageCode = languageCode
        retryCount = 0
        runOnMain {
            startListeningInternal(languageCode)
        }
    }

    private fun startListeningInternal(languageCode: String) {
        destroyInternal()

        if (!isAvailable()) {
            onStateChange(SpeechState.Idle)
            onError("Speech recognition service is not available on this device.")
            return
        }

        lastPartialText = null
        hasBeginningOfSpeech = false
        readyTimestampMs = System.currentTimeMillis()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("JuliSpeech", "onReadyForSpeech (lang=$languageCode)")
                    readyTimestampMs = System.currentTimeMillis()
                    isListening = true
                    onStateChange(SpeechState.Listening)
                }

                override fun onBeginningOfSpeech() {
                    Log.d("JuliSpeech", "onBeginningOfSpeech")
                    hasBeginningOfSpeech = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    onRmsLevel(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d("JuliSpeech", "onEndOfSpeech")
                    onStateChange(SpeechState.Processing)
                }

                override fun onError(error: Int) {
                    isListening = false
                    onRmsLevel(0f)

                    val partialFallback = lastPartialText?.trim()
                    if (!partialFallback.isNullOrEmpty() &&
                        (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    ) {
                        Log.i("JuliSpeech", "Using partial text fallback after error $error: '$partialFallback'")
                        onStateChange(SpeechState.Idle)
                        destroyInternal()
                        onFinalText(partialFallback)
                        return
                    }

                    // Auto-retry once if recognizer fired ERROR_NO_MATCH prematurely (<1.2s) before user spoke
                    val elapsedMs = System.currentTimeMillis() - readyTimestampMs
                    if (error == SpeechRecognizer.ERROR_NO_MATCH && !hasBeginningOfSpeech && elapsedMs < 1200L && retryCount < 1) {
                        retryCount++
                        Log.w("JuliSpeech", "Premature ERROR_NO_MATCH after ${elapsedMs}ms, retrying once...")
                        destroyInternal()
                        mainHandler.postDelayed({
                            startListeningInternal(activeLanguageCode)
                        }, 200L)
                        return
                    }

                    onStateChange(SpeechState.Idle)

                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "माइक्रोफ़ोन ऑडियो एरर। कृपया माइक चेक करें।"
                        SpeechRecognizer.ERROR_CLIENT -> "स्पीच क्लाइंट एरर। कृपया फिर से प्रयास करें।"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक्रोफ़ोन की अनुमति (Permission) आवश्यक है।"
                        SpeechRecognizer.ERROR_NETWORK -> "इंटरनेट कनेक्शन कमजोर है। कृपया नेटवर्क चेक करें।"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "नेटवर्क टाइमआउट। कृपया फिर से बोलें।"
                        SpeechRecognizer.ERROR_NO_MATCH -> "कुछ सुनाई नहीं दिया। कृपया माइक के पास साफ बोलें या Voice Popup आज़माएँ।"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "स्पीच सर्विस व्यस्त है। एक सेकंड बाद प्रयास करें।"
                        SpeechRecognizer.ERROR_SERVER -> "स्पीच सर्वर एरर। कृपया फिर से प्रयास करें।"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "कोई आवाज़ नहीं मिली। माइक दबाकर फिर से बोलें।"
                        else -> "स्पीच पहचानने में त्रुटि ($error)। कृपया फिर से बोलें।"
                    }
                    Log.w("JuliSpeech", "SpeechRecognizer error: $error -> $errorMessage")
                    destroyInternal()
                    onError(errorMessage)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onStateChange(SpeechState.Idle)
                    onRmsLevel(0f)

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: lastPartialText?.trim()
                    destroyInternal()

                    if (!text.isNullOrEmpty()) {
                        onFinalText(text)
                    } else {
                        onError("कुछ समझ नहीं आया, कृपया फिर से कहें।")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim()
                    if (!partial.isNullOrEmpty()) {
                        lastPartialText = partial
                        onPartialText(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = buildRecognizerIntent(context, languageCode)
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("JuliSpeech", "Failed to start listening", e)
            isListening = false
            onStateChange(SpeechState.Idle)
            onError("माइक शुरू करने में समस्या: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        runOnMain {
            if (isListening) {
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w("JuliSpeech", "Error stopping speech recognizer", e)
                }
            }
            destroyInternal()
            onStateChange(SpeechState.Idle)
            onRmsLevel(0f)
        }
    }

    fun destroy() {
        runOnMain {
            destroyInternal()
        }
    }

    private fun destroyInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("JuliSpeech", "Error destroying speech recognizer", e)
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        fun buildRecognizerIntent(context: Context, languageCode: String): Intent {
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                // Generous silence thresholds so recognition doesn't cut off prematurely
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)

                when (languageCode) {
                    "bho" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("bho-IN", "hi-IN", "en-IN"))
                    }
                    "en" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US", "hi-IN"))
                    }
                    "hi" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN"))
                    }
                    else -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN"))
                    }
                }
            }
        }
    }
}
