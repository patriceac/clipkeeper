package com.magicclipboard.ime.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidVoiceInputController(
    private val context: Context,
) : VoiceInputController, RecognitionListener {
    private val mutableState = MutableStateFlow<VoiceInputState>(availabilityState())
    override val state: StateFlow<VoiceInputState> = mutableState

    private var speechRecognizer: SpeechRecognizer? = null
    private var resultCallback: ((String) -> Unit)? = null

    override fun start(
        localeTag: String,
        onResult: (String) -> Unit,
    ) {
        if (availabilityState() !is VoiceInputState.Available) {
            mutableState.value = availabilityState()
            return
        }
        resultCallback = onResult
        val recognizer = speechRecognizer ?: createRecognizer().also { speechRecognizer = it }
        mutableState.value = VoiceInputState.Listening
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            },
        )
    }

    override fun stop() {
        speechRecognizer?.stopListening()
    }

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        mutableState.value = VoiceInputState.Processing
    }

    override fun onError(error: Int) {
        mutableState.value = VoiceInputState.Unavailable("Voice input failed")
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) {
            resultCallback?.invoke(text)
        }
        mutableState.value = availabilityState()
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(
        eventType: Int,
        params: Bundle?,
    ) = Unit

    override fun onSegmentResults(segmentResults: Bundle) = Unit

    override fun onLanguageDetection(results: Bundle) = Unit

    private fun createRecognizer(): SpeechRecognizer {
        val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer.setRecognitionListener(this)
        return recognizer
    }

    private fun availabilityState(): VoiceInputState {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            return VoiceInputState.Unavailable("Microphone permission not granted")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return VoiceInputState.Unavailable("On-device speech requires Android 12 or newer")
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return VoiceInputState.Unavailable("Speech recognition service unavailable")
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return VoiceInputState.Unavailable("On-device speech recognition unavailable")
        }
        return VoiceInputState.Available
    }
}
