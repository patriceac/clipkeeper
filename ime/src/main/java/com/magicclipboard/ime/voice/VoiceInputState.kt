package com.magicclipboard.ime.voice

sealed interface VoiceInputState {
    data object Available : VoiceInputState

    data object Listening : VoiceInputState

    data object Processing : VoiceInputState

    data class Unavailable(val reason: String) : VoiceInputState
}

