package com.magicclipboard.ime.view

import com.magicclipboard.ime.KeyboardMode

sealed interface KeyboardAction {
    data class Text(
        val value: String,
        val useComposing: Boolean = true,
    ) : KeyboardAction

    data object Shift : KeyboardAction

    data object Backspace : KeyboardAction

    data object Space : KeyboardAction

    data object Enter : KeyboardAction

    data object Clipboard : KeyboardAction

    data object Mic : KeyboardAction

    data object LanguageSwitch : KeyboardAction

    data class ModeSwitch(
        val target: KeyboardMode,
    ) : KeyboardAction
}

data class KeySpec(
    val label: String,
    val action: KeyboardAction,
    val weight: Float = 1f,
    val alternates: List<String> = emptyList(),
    val gestureEnabled: Boolean = action is KeyboardAction.Text && label.length == 1 && label.first().isLetter(),
)

