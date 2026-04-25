package com.magicclipboard.ime.voice

import kotlinx.coroutines.flow.StateFlow

interface VoiceInputController {
    val state: StateFlow<VoiceInputState>

    fun start(
        localeTag: String,
        onResult: (String) -> Unit,
    )

    fun stop()

    fun destroy()
}

