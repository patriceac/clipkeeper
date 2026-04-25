package com.magicclipboard.ime.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.magicclipboard.ime.KeyboardMode
import com.magicclipboard.typing.model.GestureTrace
import com.magicclipboard.typing.model.LanguageMode
import kotlin.math.abs

class MagicKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    interface Listener {
        fun onKeyAction(
            action: KeyboardAction,
            anchorView: View?,
        )

        fun onGesture(trace: GestureTrace)

        fun onAlternatesRequested(
            keySpec: KeySpec,
            anchorView: View,
        )
    }

    var listener: Listener? = null

    private val rowContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    private val keyViews = linkedMapOf<KeySpec, KeyboardKeyView>()
    private val tmpRect = Rect()
    private val tmpLocation = IntArray(2)

    private var gesturePath = mutableListOf<KeySpec>()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    init {
        addView(rowContainer)
    }

    fun render(
        languageMode: LanguageMode,
        keyboardMode: KeyboardMode,
        shifted: Boolean,
    ) {
        keyViews.clear()
        rowContainer.removeAllViews()
        buildRows(languageMode, keyboardMode, shifted).forEach { row ->
            rowContainer.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.FILL_HORIZONTAL
                    isBaselineAligned = false
                    weightSum = row.sumOf { it.weight.toDouble() }.toFloat()
                    layoutParams = LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    )
                    row.forEach { spec ->
                        val keyView = KeyboardKeyView(context).apply {
                            bind(spec)
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                54.dp,
                                spec.weight,
                            )
                        }
                        keyViews[spec] = keyView
                        addView(keyView)
                    }
                },
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                gesturePath.clear()
                selectKey(findKeyAt(event.rawX, event.rawY))
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                selectKey(findKeyAt(event.rawX, event.rawY))
                return true
            }

            MotionEvent.ACTION_UP -> {
                val finalKey = findKeyAt(event.rawX, event.rawY) ?: gesturePath.lastOrNull()
                val movedEnough = abs(event.rawX - downX) > 20.dp || abs(event.rawY - downY) > 20.dp
                val distinctGestureKeys = gesturePath
                    .filter(KeySpec::gestureEnabled)
                    .distinctBy { it.label }

                if (distinctGestureKeys.size >= 3 && movedEnough) {
                    listener?.onGesture(
                        GestureTrace(
                            letters = distinctGestureKeys.mapNotNull { it.label.firstOrNull() },
                            elapsedMs = event.eventTime - downTime,
                        ),
                    )
                } else {
                    finalKey?.let { keySpec ->
                        val keyView = keyViews[keySpec]
                        val isLongPress = event.eventTime - downTime > 360L
                        if (isLongPress && keySpec.alternates.isNotEmpty() && keyView != null) {
                            listener?.onAlternatesRequested(keySpec, keyView)
                        } else {
                            listener?.onKeyAction(keySpec.action, keyView)
                        }
                    }
                }
                clearPressedState()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                clearPressedState()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun selectKey(keySpec: KeySpec?) {
        if (keySpec == null || gesturePath.lastOrNull() == keySpec) return
        gesturePath += keySpec
        keyViews[keySpec]?.isActivated = true
    }

    private fun clearPressedState() {
        keyViews.values.forEach { it.isActivated = false }
        gesturePath.clear()
    }

    private fun findKeyAt(
        x: Float,
        y: Float,
    ): KeySpec? {
        return keyViews.entries.firstOrNull { (_, view) ->
            view.getLocationOnScreen(tmpLocation)
            tmpRect.set(
                tmpLocation[0],
                tmpLocation[1],
                tmpLocation[0] + view.width,
                tmpLocation[1] + view.height,
            )
            tmpRect.contains(x.toInt(), y.toInt())
        }?.key
    }

    private fun buildRows(
        languageMode: LanguageMode,
        keyboardMode: KeyboardMode,
        shifted: Boolean,
    ): List<List<KeySpec>> {
        return when (keyboardMode) {
            KeyboardMode.LETTERS -> letterRows(languageMode, shifted)
            KeyboardMode.SYMBOLS -> symbolRows()
            KeyboardMode.EMOJI -> emojiRows()
        }
    }

    private fun letterRows(
        languageMode: LanguageMode,
        shifted: Boolean,
    ): List<List<KeySpec>> {
        val row1 = when (languageMode) {
            LanguageMode.ENGLISH -> "qwertyuiop"
            LanguageMode.FRENCH -> "azertyuiop"
        }
        val row2 = when (languageMode) {
            LanguageMode.ENGLISH -> "asdfghjkl"
            LanguageMode.FRENCH -> "qsdfghjklm"
        }
        val row3 = when (languageMode) {
            LanguageMode.ENGLISH -> "zxcvbnm"
            LanguageMode.FRENCH -> "wxcvbn"
        }

        return listOf(
            row1.map { letterKey(it, shifted) },
            row2.map { letterKey(it, shifted) },
            listOf(KeySpec("Shift", KeyboardAction.Shift, weight = 1.35f)) +
                row3.map { letterKey(it, shifted) } +
                listOf(KeySpec("⌫", KeyboardAction.Backspace, weight = 1.35f)),
            listOf(
                KeySpec("123", KeyboardAction.ModeSwitch(KeyboardMode.SYMBOLS), weight = 1.1f),
                KeySpec("😊", KeyboardAction.ModeSwitch(KeyboardMode.EMOJI), weight = 1f),
                KeySpec("Clip", KeyboardAction.Clipboard, weight = 1f),
                KeySpec("Mic", KeyboardAction.Mic, weight = 1f),
                KeySpec(languageMode.shortLabel(), KeyboardAction.LanguageSwitch, weight = 1f),
                KeySpec("space", KeyboardAction.Space, weight = 2.6f),
                KeySpec("↵", KeyboardAction.Enter, weight = 1f),
            ),
        )
    }

    private fun symbolRows(): List<List<KeySpec>> {
        return listOf(
            "1234567890".map { KeySpec(it.toString(), KeyboardAction.Text(it.toString(), useComposing = false)) },
            listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
                .map { KeySpec(it, KeyboardAction.Text(it, useComposing = false)) },
            listOf(KeySpec("ABC", KeyboardAction.ModeSwitch(KeyboardMode.LETTERS), weight = 1.35f)) +
                listOf(".", ",", "?", "!", "'", "#", "%")
                    .map { KeySpec(it, KeyboardAction.Text(it, useComposing = false)) } +
                listOf(KeySpec("⌫", KeyboardAction.Backspace, weight = 1.35f)),
            listOf(
                KeySpec("😊", KeyboardAction.ModeSwitch(KeyboardMode.EMOJI), weight = 1f),
                KeySpec("Clip", KeyboardAction.Clipboard, weight = 1f),
                KeySpec("Mic", KeyboardAction.Mic, weight = 1f),
                KeySpec("Lang", KeyboardAction.LanguageSwitch, weight = 1f),
                KeySpec("space", KeyboardAction.Space, weight = 2.6f),
                KeySpec("↵", KeyboardAction.Enter, weight = 1f),
            ),
        )
    }

    private fun emojiRows(): List<List<KeySpec>> {
        return listOf(
            listOf("😀", "😂", "😍", "😭", "🙏", "🔥", "👏", "👍").map {
                KeySpec(it, KeyboardAction.Text(it, useComposing = false))
            },
            listOf("🎉", "✅", "🚀", "❤️", "🎯", "🤝", "💡", "📌").map {
                KeySpec(it, KeyboardAction.Text(it, useComposing = false))
            },
            listOf(
                KeySpec("ABC", KeyboardAction.ModeSwitch(KeyboardMode.LETTERS), weight = 1.5f),
                KeySpec("123", KeyboardAction.ModeSwitch(KeyboardMode.SYMBOLS), weight = 1.2f),
                KeySpec("⌫", KeyboardAction.Backspace, weight = 1.2f),
                KeySpec("space", KeyboardAction.Space, weight = 2.2f),
                KeySpec("↵", KeyboardAction.Enter, weight = 1f),
            ),
        )
    }

    private fun letterKey(
        letter: Char,
        shifted: Boolean,
    ): KeySpec {
        val label = if (shifted) letter.uppercaseChar().toString() else letter.lowercaseChar().toString()
        val alternates = when (letter.lowercaseChar()) {
            'a' -> listOf("à", "á", "â", "ä", "æ")
            'c' -> listOf("ç")
            'e' -> listOf("é", "è", "ê", "ë")
            'i' -> listOf("î", "ï")
            'o' -> listOf("ô", "ö")
            'u' -> listOf("ù", "û", "ü")
            else -> emptyList()
        }
        return KeySpec(label, KeyboardAction.Text(label), alternates = alternates)
    }

    private fun LanguageMode.shortLabel(): String = when (this) {
        LanguageMode.ENGLISH -> "EN"
        LanguageMode.FRENCH -> "FR"
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private class KeyboardKeyView(
        context: Context,
    ) : AppCompatTextView(context) {
        private val backgroundShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 0f
            setStroke(1.dp, 0xFFD7DEE5.toInt())
        }

        fun bind(spec: KeySpec) {
            text = spec.label
            gravity = Gravity.CENTER
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF111827.toInt())
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            background = backgroundShape.constantState?.newDrawable()?.mutate()?.apply {
                if (this is GradientDrawable) {
                    setColor(0xFFFFFFFF.toInt())
                }
            }
            isClickable = false
            isFocusable = false
        }

        override fun setActivated(activated: Boolean) {
            super.setActivated(activated)
            (background as? GradientDrawable)?.setColor(
                if (activated) 0xFFDCEFEA.toInt() else 0xFFFFFFFF.toInt(),
            )
        }

        private val Int.dp: Int
            get() = (this * resources.displayMetrics.density).toInt()
    }
}
