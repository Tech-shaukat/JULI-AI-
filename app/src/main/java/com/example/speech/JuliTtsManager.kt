package com.example.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class JuliTtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingDone: () -> Unit,
    private val onError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val hindiLocale = Locale("hi", "IN")
    var currentSpeechRate: Float = 0.95f
        private set
    var currentPitch: Float = 1.08f
        private set

    fun setSpeechParams(rate: Float, pitch: Float) {
        currentSpeechRate = rate.coerceIn(0.6f, 1.6f)
        currentPitch = pitch.coerceIn(0.7f, 1.5f)
        tts?.setSpeechRate(currentSpeechRate)
        tts?.setPitch(currentPitch)
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("JuliTts", "Error instantiating TextToSpeech", e)
            onError("TTS engine initialization failed: ${e.localizedMessage}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            var langResult = engine.setLanguage(hindiLocale)

            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("JuliTts", "Hindi language not supported or missing data, falling back to default locale")
                langResult = engine.setLanguage(Locale.ENGLISH)
            }

            // Configure speech rate to around 0.95x as specified in requirements
            engine.setSpeechRate(0.95f)
            // Configure pitch for natural female tone
            engine.setPitch(1.08f)

            // Select natural female voice if available
            selectBestFemaleVoice(engine)

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("JuliTts", "TTS started utterance: $utteranceId")
                    onSpeakingStarted()
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("JuliTts", "TTS finished utterance: $utteranceId")
                    onSpeakingDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.w("JuliTts", "TTS error on utterance: $utteranceId")
                    onSpeakingDone()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w("JuliTts", "TTS error code: $errorCode on utterance: $utteranceId")
                    onSpeakingDone()
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    Log.d("JuliTts", "TTS stopped utterance: $utteranceId, interrupted=$interrupted")
                    onSpeakingDone()
                }
            })

            isInitialized = true
            onInitComplete(true)
        } else {
            Log.e("JuliTts", "TextToSpeech init failed with status: $status")
            isInitialized = false
            onInitComplete(false)
            onError("Failed to initialize speech voice output.")
        }
    }

    private fun selectBestFemaleVoice(engine: TextToSpeech) {
        try {
            val voices = engine.voices
            if (!voices.isNullOrEmpty()) {
                // Priority 1: Hindi female voice
                val hindiFemaleVoice = voices.find { voice ->
                    val isHindi = voice.locale.language.equals("hi", ignoreCase = true)
                    val name = voice.name.lowercase()
                    isHindi && (name.contains("female") || name.contains("-fem") || name.contains("f0") || name.contains("woman") || name.contains("-hie-") || name.contains("-hid-"))
                }

                // Priority 2: Any Hindi voice
                val hindiVoice = hindiFemaleVoice ?: voices.find { voice ->
                    voice.locale.language.equals("hi", ignoreCase = true)
                }

                // Priority 3: English female voice (Indian accent preferred)
                val englishFemaleVoice = hindiVoice ?: voices.find { voice ->
                    val isEn = voice.locale.language.equals("en", ignoreCase = true)
                    val name = voice.name.lowercase()
                    isEn && (name.contains("female") || name.contains("-fem") || name.contains("f0") || name.contains("woman"))
                }

                val chosenVoice: Voice? = hindiVoice ?: englishFemaleVoice
                if (chosenVoice != null) {
                    engine.voice = chosenVoice
                    Log.d("JuliTts", "Selected TTS voice: ${chosenVoice.name} (${chosenVoice.locale})")
                }
            }
        } catch (e: Exception) {
            Log.w("JuliTts", "Could not query specific voice list, using default: ${e.message}")
        }
    }

    fun speak(text: String, isHindi: Boolean = true) {
        if (!isInitialized || tts == null) {
            onError("Voice output is not ready yet.")
            return
        }

        try {
            val engine = tts ?: return
            if (isHindi) {
                engine.language = hindiLocale
            } else {
                engine.language = Locale.ENGLISH
            }
            engine.setSpeechRate(currentSpeechRate)
            engine.setPitch(currentPitch)

            val utteranceId = "JULI_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e("JuliTts", "Error during TTS speak", e)
            onSpeakingDone()
            onError("Voice playback error: ${e.localizedMessage}")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("JuliTts", "Error stopping TTS", e)
        } finally {
            onSpeakingDone()
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w("JuliTts", "Error shutting down TTS", e)
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
