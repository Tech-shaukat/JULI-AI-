package com.example.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

/**
 * Lightweight, strictly on-device wake-word detector for "Hey JULI".
 * Does NOT stream audio to any cloud service.
 * Monitors voice activity locally and triggers keyword matching.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val ENERGY_THRESHOLD = 950.0 // Sensitive voice activity threshold
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var isVerifyingKeyword = false

    @Volatile
    private var audioRecord: AudioRecord? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wakeKeywords = listOf(
        "hey juli", "hey julie", "hey julia", "juli", "julie", "julia", "jooli",
        "हे जूली", "हे जुली", "सुनो जूली", "जूली", "जुली", "aye juli", "hai juli", "juli ai"
    )

    fun start() {
        if (isRunning) return
        isRunning = true
        isVerifyingKeyword = false
        Log.d(TAG, "Starting on-device wake word detector for 'Hey JULI'...")

        monitorJob?.cancel()
        monitorJob = scope.launch {
            runAcousticMonitorLoop()
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping on-device wake word detector...")
        isRunning = false
        isVerifyingKeyword = false
        monitorJob?.cancel()
        monitorJob = null
        releaseAudioRecord()
        mainHandler.post { releaseRecognizerInternal() }
    }

    fun isListening(): Boolean = isRunning

    private suspend fun runAcousticMonitorLoop() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(3200)
        val audioBuffer = ShortArray(bufferSize / 2)

        while (isRunning && scope.isActive) {
            if (isVerifyingKeyword) {
                delay(200)
                continue
            }

            if (audioRecord == null) {
                val initialized = initAudioRecord(bufferSize)
                if (!initialized) {
                    delay(500)
                    continue
                }
            }

            try {
                val readCount = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                if (readCount > 0) {
                    val energy = calculateRms(audioBuffer, readCount)

                    if (energy > ENERGY_THRESHOLD && !isVerifyingKeyword && isRunning) {
                        Log.d(TAG, "Voice activity detected (energy: $energy). Releasing AudioRecord & verifying wake keyword...")
                        isVerifyingKeyword = true
                        // Fully release AudioRecord hardware handle BEFORE starting SpeechRecognizer
                        releaseAudioRecord()
                        delay(120)
                        if (isRunning) {
                            mainHandler.post { verifyWakeWord() }
                        } else {
                            isVerifyingKeyword = false
                        }
                    }
                } else {
                    delay(50)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord read error: ${e.message}")
                releaseAudioRecord()
                delay(300)
            }
        }

        releaseAudioRecord()
    }

    private fun initAudioRecord(bufferSize: Int): Boolean {
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                record.release()
                mainHandler.post { onError("Could not initialize microphone for wake word.") }
                stop()
                false
            } else {
                record.startRecording()
                audioRecord = record
                true
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing RECORD_AUDIO permission", e)
            mainHandler.post { onError("Microphone permission required for wake word.") }
            stop()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioRecord", e)
            false
        }
    }

    private fun verifyWakeWord() {
        if (!isRunning) {
            isVerifyingKeyword = false
            return
        }

        try {
            releaseRecognizerInternal()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                isVerifyingKeyword = false
                return
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.d(TAG, "Recognizer error during wake verification: $error")
                    releaseRecognizerInternal()
                    resumeMonitoringAfterDelay()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val matchedText = matches?.firstOrNull()?.lowercase(Locale.ROOT)?.trim()
                    Log.d(TAG, "Heard phrase during verification: '$matchedText'")
                    releaseRecognizerInternal()

                    if (!matchedText.isNullOrEmpty() && containsWakeWord(matchedText)) {
                        Log.i(TAG, ">>> 'Hey JULI' WAKE WORD CONFIRMED! <<<")
                        isVerifyingKeyword = false
                        onWakeWordDetected(matchedText)
                    } else {
                        resumeMonitoringAfterDelay()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = partials?.firstOrNull()?.lowercase(Locale.ROOT)?.trim()
                    if (!text.isNullOrEmpty() && containsWakeWord(text)) {
                        Log.i(TAG, ">>> 'Hey JULI' detected in partial results! <<<")
                        releaseRecognizerInternal()
                        isVerifyingKeyword = false
                        onWakeWordDetected(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN"))
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start keyword verification", e)
            releaseRecognizerInternal()
            resumeMonitoringAfterDelay()
        }
    }

    private fun resumeMonitoringAfterDelay() {
        scope.launch {
            delay(250)
            isVerifyingKeyword = false
        }
    }

    fun containsWakeWord(phrase: String): Boolean {
        val lower = phrase.lowercase(Locale.ROOT)
        return wakeKeywords.any { lower.contains(it) }
    }

    private fun calculateRms(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / readSize)
    }

    private fun releaseAudioRecord() {
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioRecord", e)
            }
        }
    }

    private fun releaseRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }
}
