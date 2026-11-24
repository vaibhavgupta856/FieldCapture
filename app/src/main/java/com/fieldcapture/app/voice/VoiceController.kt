package com.fieldcapture.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Phone-microphone voice path. Rationale (documented in README): DAT Speech, Voice
 * Invocations and Audio Streaming are experimental/approval-gated in DAT 1.1, and
 * MockDeviceKit explicitly does not emit deterministic PCM. The phone mic gives a
 * reproducible hands-free walk after one tap to arm the recogniser.
 */
class VoiceController(
    private val context: Context,
    private val onTranscript: (String, Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var listening = false
    private var restarted = 0

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasPermission()) return
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        // Restart on transient errors; 7 = no speech, 6 = no match.
                        if (listening && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) scheduleRestart()
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { onTranscript(it, true) }
                        scheduleRestart()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { onTranscript(it, false) }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.US)
            }
        }
        listening = true
        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { scheduleRestart() }
    }

    private fun scheduleRestart() {
        if (!listening) return
        restarted++
        handler.postDelayed({ if (listening) startListening() }, 350)
    }

    fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "fieldcapture_${System.currentTimeMillis()}")
    }

    fun stop() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.stopListening() }
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
