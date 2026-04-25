package com.magicclipboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.magicclipboard.data.ClipboardRepository
import com.magicclipboard.data.clip.SensitiveContentPolicy
import com.magicclipboard.data.model.AppSettings
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.model.ClipEntry
import com.magicclipboard.data.prefs.SettingsRepository
import com.magicclipboard.ime.view.ClipAdapter
import com.magicclipboard.ime.view.KeySpec
import com.magicclipboard.ime.view.KeyboardAction
import com.magicclipboard.ime.view.MagicKeyboardView
import com.magicclipboard.ime.voice.AndroidVoiceInputController
import com.magicclipboard.ime.voice.VoiceInputState
import com.magicclipboard.typing.GestureDecoder
import com.magicclipboard.typing.SuggestionEngine
import com.magicclipboard.typing.model.LanguageMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MagicInputMethodService : InputMethodService(), MagicKeyboardView.Listener {
    @Inject lateinit var clipboardRepository: ClipboardRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var suggestionEngine: SuggestionEngine
    @Inject lateinit var gestureDecoder: GestureDecoder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val voiceController by lazy { AndroidVoiceInputController(this) }

    private lateinit var clipboardManager: ClipboardManager

    private lateinit var suggestionStrip: LinearLayout
    private lateinit var clipStripRecycler: RecyclerView
    private lateinit var sheetContainer: LinearLayout
    private lateinit var sheetSearchField: EditText
    private lateinit var sheetRecycler: RecyclerView
    private lateinit var sheetEmptyLabel: TextView
    private lateinit var voiceStateLabel: TextView
    private lateinit var keyboardView: MagicKeyboardView

    private lateinit var stripAdapter: ClipAdapter
    private lateinit var sheetAdapter: ClipAdapter

    private var clipJob: Job? = null
    private var currentSettings = AppSettings()
    private var currentLanguage = LanguageMode.ENGLISH
    private var keyboardMode = KeyboardMode.LETTERS
    private var shifted = false
    private var currentSearchQuery = ""
    private val composing = StringBuilder()

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        capturePrimaryClip()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(ClipboardManager::class.java)
        stripAdapter = ClipAdapter(
            imageLoader = clipboardRepository::loadImagePreview,
            onClipTapped = ::handleClipTap,
        )
        sheetAdapter = ClipAdapter(
            imageLoader = clipboardRepository::loadImagePreview,
            onClipTapped = ::handleClipTap,
        )

        serviceScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                currentSettings = settings
                val languages = settings.enabledLanguages.ifEmpty { setOf(LanguageMode.ENGLISH) }
                if (!languages.contains(currentLanguage)) {
                    currentLanguage = languages.first()
                }
                suggestionEngine.seedLearnedWords(LanguageMode.ENGLISH, settings.learnedEnglishWords)
                suggestionEngine.seedLearnedWords(LanguageMode.FRENCH, settings.learnedFrenchWords)
                clipboardRepository.pruneExpired(settings.retentionHours)
                renderKeyboard()
                renderVoiceState(voiceController.state.value)
            }
        }

        serviceScope.launch {
            voiceController.state.collectLatest(::renderVoiceState)
        }
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())
            setPadding(0, 12.dp, 0, 16.dp)
        }

        suggestionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 0, 16.dp, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        clipStripRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MagicInputMethodService, RecyclerView.HORIZONTAL, false)
            adapter = stripAdapter
            clipToPadding = false
            setPadding(16.dp, 0, 16.dp, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                84.dp,
            )
        }

        voiceStateLabel = TextView(this).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFF475569.toInt())
            setPadding(16.dp, 0, 16.dp, 0)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 8.dp
            }
        }

        sheetSearchField = EditText(this).apply {
            hint = "Search clipboard"
            setSingleLine()
            background = getDrawable(android.R.drawable.edit_text)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    currentSearchQuery = s?.toString().orEmpty()
                    collectClips()
                }
            })
        }

        sheetEmptyLabel = TextView(this).apply {
            text = "No matching clips yet"
            gravity = Gravity.CENTER
            setPadding(24.dp, 24.dp, 24.dp, 24.dp)
            visibility = View.GONE
        }

        sheetRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MagicInputMethodService)
            adapter = sheetAdapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                220.dp,
            )
        }

        sheetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 0, 16.dp, 0)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 8.dp }
            addView(sheetSearchField)
            addView(sheetEmptyLabel)
            addView(sheetRecycler)
        }

        keyboardView = MagicKeyboardView(this).apply {
            listener = this@MagicInputMethodService
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 12.dp }
        }

        root.addView(suggestionStrip)
        root.addView(clipStripRecycler)
        root.addView(voiceStateLabel)
        root.addView(sheetContainer)
        root.addView(keyboardView)

        renderKeyboard()
        renderSuggestions()
        collectClips()
        return root
    }

    override fun onStartInput(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
        composing.clear()
        shifted = false
        keyboardMode = KeyboardMode.LETTERS
        renderKeyboard()
        renderSuggestions()
    }

    override fun onStartInputView(
        info: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(info, restarting)
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        renderKeyboard()
        renderSuggestions()
        collectClips()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        composing.clear()
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        voiceController.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onKeyAction(
        action: KeyboardAction,
        anchorView: View?,
    ) {
        when (action) {
            is KeyboardAction.Text -> handleTextInput(action)
            KeyboardAction.Backspace -> handleBackspace()
            KeyboardAction.Clipboard -> toggleClipboardSheet()
            KeyboardAction.Enter -> handleEnter()
            KeyboardAction.LanguageSwitch -> cycleLanguage()
            KeyboardAction.Mic -> handleVoiceInput()
            is KeyboardAction.ModeSwitch -> {
                keyboardMode = action.target
                shifted = false
                renderKeyboard()
            }
            KeyboardAction.Shift -> {
                shifted = !shifted
                renderKeyboard()
            }
            KeyboardAction.Space -> handleSpace()
        }
    }

    override fun onGesture(trace: com.magicclipboard.typing.model.GestureTrace) {
        val suggestion = gestureDecoder.decode(trace, currentLanguage).firstOrNull() ?: return
        commitCurrentComposition(replacement = suggestion.text)
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onAlternatesRequested(
        keySpec: KeySpec,
        anchorView: View,
    ) {
        PopupMenu(this, anchorView).apply {
            keySpec.alternates.forEachIndexed { index, alt ->
                menu.add(0, index, index, alt)
            }
            setOnMenuItemClickListener { item ->
                handleTextInput(KeyboardAction.Text(item.title.toString()))
                true
            }
        }.show()
    }

    private fun collectClips() {
        clipJob?.cancel()
        clipJob = serviceScope.launch {
            clipboardRepository.observeEntries(currentSearchQuery).collectLatest { clips ->
                stripAdapter.submitList(clips.take(8))
                sheetAdapter.submitList(clips)
                sheetEmptyLabel.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun renderKeyboard() {
        if (::keyboardView.isInitialized) {
            keyboardView.render(currentLanguage, keyboardMode, shifted)
        }
    }

    private fun renderSuggestions() {
        if (!::suggestionStrip.isInitialized) return
        suggestionStrip.removeAllViews()
        val suggestions = suggestionEngine.suggest(
            contextBeforeCursor = currentInputConnection?.getTextBeforeCursor(48, 0)?.toString().orEmpty(),
            composingText = composing.toString(),
            languageMode = currentLanguage,
        )
        suggestions.forEach { candidate ->
            suggestionStrip.addView(
                TextView(this).apply {
                    text = candidate.text
                    gravity = Gravity.CENTER
                    setTextColor(if (candidate.isAutocorrect) 0xFF0F766E.toInt() else 0xFF0F172A.toInt())
                    setPadding(24.dp, 24.dp, 24.dp, 24.dp)
                    background = getDrawable(android.R.drawable.btn_default_small)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = 8.dp
                    }
                    setOnClickListener { commitSuggestion(candidate.text, addTrailingSpace = composing.isEmpty()) }
                },
            )
        }
        if (suggestionStrip.childCount == 0) {
            suggestionStrip.addView(
                TextView(this).apply {
                    text = "Clipboard and suggestions stay on-device"
                    setTextColor(0xFF64748B.toInt())
                    setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                },
            )
        }
    }

    private fun renderVoiceState(state: VoiceInputState) {
        if (!::voiceStateLabel.isInitialized) return
        val disabledBySettings = !currentSettings.voiceEnabled
        val message = when {
            disabledBySettings -> "Voice input is disabled in settings."
            state is VoiceInputState.Listening -> "Listening…"
            state is VoiceInputState.Processing -> "Processing speech on-device…"
            state is VoiceInputState.Unavailable -> state.reason
            else -> ""
        }
        voiceStateLabel.text = message
        voiceStateLabel.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun handleTextInput(action: KeyboardAction.Text) {
        val value = if (shifted && action.value.length == 1) action.value.uppercase() else action.value
        if (action.useComposing && value.length == 1 && value.first().isLetter()) {
            composing.append(value)
            currentInputConnection?.commitText(value, 1)
            shifted = false
            renderKeyboard()
        } else {
            commitCurrentComposition(replacement = pendingAutocorrect())
            currentInputConnection?.commitText(value, 1)
        }
        renderSuggestions()
    }

    private fun handleBackspace() {
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            currentInputConnection?.deleteSurroundingText(1, 0)
            renderSuggestions()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleSpace() {
        commitCurrentComposition(replacement = pendingAutocorrect())
        currentInputConnection?.commitText(" ", 1)
    }

    private fun handleEnter() {
        commitCurrentComposition(replacement = pendingAutocorrect())
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.commitText("\n", 1)
        }
    }

    private fun handleVoiceInput() {
        if (!currentSettings.voiceEnabled) {
            toast("Voice is disabled in settings")
            return
        }
        if (voiceController.state.value is VoiceInputState.Listening) {
            voiceController.stop()
            return
        }
        voiceController.start(currentLanguage.localeTag) { spokenText ->
            commitCurrentComposition()
            currentInputConnection?.commitText("$spokenText ", 1)
            serviceScope.launch {
                settingsRepository.addLearnedWord(currentLanguage, spokenText)
            }
        }
    }

    private fun handleClipTap(clip: ClipEntry) {
        when (clip.kind) {
            ClipContentKind.TEXT -> {
                commitCurrentComposition()
                currentInputConnection?.commitText(clip.text.orEmpty(), 1)
            }
            ClipContentKind.IMAGE -> commitImage(clip)
        }
    }

    private fun commitImage(clip: ClipEntry) {
        val editorInfo = currentInputEditorInfo ?: run {
            toast("Open a field before pasting an image")
            return
        }
        val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val acceptsImages = mimeTypes.isEmpty() || mimeTypes.any { type ->
            type.startsWith("image/") || type == "*/*"
        }
        if (!acceptsImages) {
            toast("This field does not accept images")
            return
        }

        serviceScope.launch {
            val exported = clipboardRepository.exportImage(clip.id, cacheDir) ?: run {
                toast("Unable to prepare the image clip")
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                this@MagicInputMethodService,
                "${packageName}.fileprovider",
                exported,
            )
            val info = InputContentInfoCompat(
                uri,
                ClipDescription("ClipKeeper image", arrayOf(clip.mimeType ?: "image/png")),
                null,
            )
            val committed = currentInputConnection?.let { inputConnection ->
                InputConnectionCompat.commitContent(
                    inputConnection,
                    editorInfo,
                    info,
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                    Bundle(),
                )
            } ?: false
            if (!committed) {
                toast("Image paste failed")
            }
        }
    }

    private fun commitSuggestion(
        suggestion: String,
        addTrailingSpace: Boolean,
    ) {
        commitCurrentComposition(replacement = suggestion)
        if (addTrailingSpace) {
            currentInputConnection?.commitText(" ", 1)
        }
    }

    private fun commitCurrentComposition(replacement: String? = null) {
        if (composing.isEmpty() && replacement == null) return
        val text = replacement ?: composing.toString()
        if (replacement != null && composing.isNotEmpty()) {
            currentInputConnection?.deleteSurroundingText(composing.length, 0)
        } else if (replacement == null && composing.isNotEmpty()) {
            composing.clear()
            renderSuggestions()
            return
        }
        currentInputConnection?.commitText(text, 1)
        composing.clear()
        serviceScope.launch {
            if (text.length >= 3) {
                settingsRepository.addLearnedWord(currentLanguage, text)
            }
        }
        renderSuggestions()
    }

    private fun pendingAutocorrect(): String? {
        return suggestionEngine.suggest(
            contextBeforeCursor = currentInputConnection?.getTextBeforeCursor(48, 0)?.toString().orEmpty(),
            composingText = composing.toString(),
            languageMode = currentLanguage,
        ).firstOrNull { it.isAutocorrect }?.text
    }

    private fun cycleLanguage() {
        val ordered = listOf(LanguageMode.ENGLISH, LanguageMode.FRENCH).filter(currentSettings.enabledLanguages::contains)
        if (ordered.isEmpty()) return
        val currentIndex = ordered.indexOf(currentLanguage).takeIf { it >= 0 } ?: 0
        currentLanguage = ordered[(currentIndex + 1) % ordered.size]
        shifted = false
        keyboardMode = KeyboardMode.LETTERS
        renderKeyboard()
        renderSuggestions()
    }

    private fun toggleClipboardSheet() {
        sheetContainer.visibility = if (sheetContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun capturePrimaryClip() {
        if (currentSettings.privateMode) return
        serviceScope.launch {
            clipboardRepository.captureClip(
                clipData = clipboardManager.primaryClip,
                sourcePackage = currentInputEditorInfo?.packageName,
                sensitiveContext = SensitiveContentPolicy.isSensitiveEditor(currentInputEditorInfo),
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
