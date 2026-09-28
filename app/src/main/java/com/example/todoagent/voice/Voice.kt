package com.example.todoagent.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Turns speech into text. Prefers the on-device recognizer (Android 12+) so the voice part
 * never leaves the phone. Must be created and used on the main thread.
 *
 * One recognizer is created and reused for every command. Creating and destroying a new one
 * per tap makes some phones report "recognizer busy" on the second command.
 */
class SpeechInput(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var finished = false
    private var retriedBusy = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        finished = false
        retriedBusy = false
        listen()
    }

    private fun listen() {
        val r = recognizer ?: createRecognizer().also { recognizer = it }
        r.cancel() // make sure no previous session is still open
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                // Ask the recognizer to wait longer before deciding you're done talking.
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        )
        onListeningChanged(true)
    }

    private fun createRecognizer(): SpeechRecognizer {
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val r = if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(listener)
        return r
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onEndOfSpeech() = onListeningChanged(false)

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.firstResult()?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            finished = true
            onListeningChanged(false)
            val text = results.firstResult()
            if (text.isNullOrBlank()) onError("Didn't catch that. Tap the mic and try again.")
            else onFinal(text)
        }

        override fun onError(error: Int) {
            // Busy: the previous session hasn't released yet. Start fresh once, shortly after.
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && !finished && !retriedBusy) {
                retriedBusy = true
                recognizer?.destroy()
                recognizer = null
                handler.postDelayed({ listen() }, 400)
                return
            }
            onListeningChanged(false)
            // Harmless errors some recognizers send after a successful result.
            if (finished ||
                error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                error == SpeechRecognizer.ERROR_CLIENT
            ) return
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off. Allow it in settings."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is still busy. Wait a second and tap again."
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                        "The offline English speech model isn't installed. Download it in your phone's speech settings."
                    else -> "Speech recognition stopped (code $error). Tap the mic to try again."
                }
            )
        }
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}

/** Reads the agent's short replies aloud. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var enabled = true

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.US
    }

    fun speak(text: String) {
        if (enabled && ready && text.isNotBlank()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "todoagent-reply")
        }
    }

    fun stop() {
        tts.stop()
    }
}