package com.example.iris

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class VoiceManager(
    private val context: Context,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onSpeechResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val TAG = "IRIS_VOICE"
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isListening = false

    // Main thread handler for thread-safe operations
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        initializeTTS()
        initializeSpeechRecognizer()
    }

    // ═══════════════════════════════════════════════════════════════
    // TEXT-TO-SPEECH (TTS)
    // ═══════════════════════════════════════════════════════════════

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed")
                onError("Text-to-speech not available")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started speaking")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS finished speaking")
                // Auto-start listening after IRIS finishes speaking
                // CRITICAL FIX: Use Handler to post to main thread
                if (utteranceId == "IRIS_RESPONSE") {
                    mainHandler.post {
                        startListening()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error")
            }
        })
    }

    /**
     * Speak text with optional auto-listening after completion
     */
    fun speak(text: String, autoListenAfter: Boolean = true) {
        mainHandler.post {
            if (!isTtsReady) {
                Log.e(TAG, "TTS not ready")
                onError("Text-to-speech not ready")
                return@post
            }

            // Stop any ongoing speech
            tts?.stop()

            val utteranceId = if (autoListenAfter) "IRIS_RESPONSE" else "IRIS_SPEAK"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

            Log.d(TAG, "Speaking: ${text.take(50)}...")
        }
    }

    /**
     * Stop speaking immediately
     */
    fun stopSpeaking() {
        mainHandler.post {
            tts?.stop()
            Log.d(TAG, "Stopped speaking")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SPEECH-TO-TEXT (STT)
    // ═══════════════════════════════════════════════════════════════

    private fun initializeSpeechRecognizer() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "Speech recognition not available")
                onError("Speech recognition not available on this device")
                return@post
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListeningStateChanged(true)
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Can be used for volume visualization
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    onListeningStateChanged(false)
                    Log.d(TAG, "Speech ended")
                }

                override fun onError(error: Int) {
                    isListening = false
                    onListeningStateChanged(false)

                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error: $error"
                    }

                    Log.e(TAG, "Recognition error: $errorMessage")

                    // Don't show error for "no match" or "timeout" - just retry
                    if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        onError(errorMessage)
                    } else {
                        // Auto-retry after no match/timeout
                        mainHandler.postDelayed({
                            startListening()
                        }, 500)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onListeningStateChanged(false)

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        Log.d(TAG, "Recognized: $spokenText")
                        onSpeechResult(spokenText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Can be used for real-time transcription
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            Log.d(TAG, "Speech recognizer initialized")
        }
    }

    /**
     * Start listening for voice input
     */
    fun startListening() {
        // Ensure we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }

        if (isListening) {
            Log.d(TAG, "Already listening, ignoring request")
            return
        }

        // Stop any ongoing speech first
        tts?.stop()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            onError("Failed to start voice recognition")
        }
    }

    /**
     * Stop listening for voice input
     */
    fun stopListening() {
        mainHandler.post {
            if (isListening) {
                isListening = false
                speechRecognizer?.stopListening()
                onListeningStateChanged(false)
                Log.d(TAG, "Stopped listening")
            }
        }
    }

    /**
     * Check if currently listening
     */
    fun isCurrentlyListening(): Boolean = isListening

    /**
     * Clean up all resources
     */
    fun cleanup() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null

                tts?.stop()
                tts?.shutdown()
                tts = null

                isListening = false
                isTtsReady = false

                Log.d(TAG, "VoiceManager cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
    }
}