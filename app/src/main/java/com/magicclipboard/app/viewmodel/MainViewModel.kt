package com.magicclipboard.app.viewmodel

import android.content.ClipData
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicclipboard.data.ClipboardRepository
import com.magicclipboard.data.model.AppSettings
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.model.ClipEntry
import com.magicclipboard.data.model.ThemeMode
import com.magicclipboard.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class MainTab {
    SCRATCHPAD,
    SETTINGS,
}

enum class ScratchpadFilter {
    ALL,
    PINNED,
    FAVORITES,
}

data class ClipboardPreview(
    val kind: ClipContentKind,
    val previewText: String,
)

data class PendingSharedPayload(
    val kind: ClipContentKind,
    val previewText: String,
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val clips: List<ClipEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: MainTab = MainTab.SCRATCHPAD,
    val selectedFilter: ScratchpadFilter = ScratchpadFilter.ALL,
    val saveSheetVisible: Boolean = false,
    val saveDraft: String = "",
    val foregroundClipboard: ClipboardPreview? = null,
    val pendingSharedPayload: PendingSharedPayload? = null,
    val noticeMessage: String? = null,
)

private sealed interface SharedSaveRequest {
    val preview: PendingSharedPayload

    data class Text(
        val text: String,
    ) : SharedSaveRequest {
        override val preview: PendingSharedPayload =
            PendingSharedPayload(
                kind = ClipContentKind.TEXT,
                previewText = text.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Shared text" },
            )
    }

    data class Clip(
        val clipData: ClipData,
        override val preview: PendingSharedPayload,
    ) : SharedSaveRequest
}

private data class MainControlsState(
    val searchQuery: String,
    val selectedTab: MainTab,
    val selectedFilter: ScratchpadFilter,
    val saveSheetVisible: Boolean,
    val saveDraft: String,
)

private data class MainFeedbackState(
    val foregroundClipboard: ClipboardPreview?,
    val pendingSharedPayload: PendingSharedPayload?,
    val noticeMessage: String?,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel @Inject constructor(
    private val clipboardRepository: ClipboardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedTab = MutableStateFlow(MainTab.SCRATCHPAD)
    private val selectedFilter = MutableStateFlow(ScratchpadFilter.ALL)
    private val saveSheetVisible = MutableStateFlow(false)
    private val saveDraft = MutableStateFlow("")
    private val foregroundClipboard = MutableStateFlow<ClipboardPreview?>(null)
    private val pendingSharedRequest = MutableStateFlow<SharedSaveRequest?>(null)
    private val noticeMessage = MutableStateFlow<String?>(null)

    private val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val clips = searchQuery
        .flatMapLatest(clipboardRepository::observeEntries)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val controlsState = combine(
        searchQuery,
        selectedTab,
        selectedFilter,
        saveSheetVisible,
        saveDraft,
    ) { query, tab, filter, isSaveSheetVisible, draft ->
        MainControlsState(
            searchQuery = query,
            selectedTab = tab,
            selectedFilter = filter,
            saveSheetVisible = isSaveSheetVisible,
            saveDraft = draft,
        )
    }

    private val feedbackState = combine(
        foregroundClipboard,
        pendingSharedRequest,
        noticeMessage,
    ) { clipboardPreview, sharedRequest, message ->
        MainFeedbackState(
            foregroundClipboard = clipboardPreview,
            pendingSharedPayload = sharedRequest?.preview,
            noticeMessage = message,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        settings,
        clips,
        controlsState,
        feedbackState,
    ) { appSettings, clipEntries, controls, feedback ->
        MainUiState(
            settings = appSettings,
            clips = clipEntries,
            searchQuery = controls.searchQuery,
            selectedTab = controls.selectedTab,
            selectedFilter = controls.selectedFilter,
            saveSheetVisible = controls.saveSheetVisible,
            saveDraft = controls.saveDraft,
            foregroundClipboard = feedback.foregroundClipboard,
            pendingSharedPayload = feedback.pendingSharedPayload,
            noticeMessage = feedback.noticeMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            settings.collect { appSettings ->
                clipboardRepository.pruneExpired(appSettings.retentionHours)
            }
        }
    }

    fun selectTab(tab: MainTab) {
        selectedTab.value = tab
    }

    fun selectFilter(filter: ScratchpadFilter) {
        selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun showSaveSheet() {
        saveSheetVisible.value = true
    }

    fun hideSaveSheet() {
        saveSheetVisible.value = false
    }

    fun updateSaveDraft(text: String) {
        saveDraft.value = text
    }

    fun setForegroundClipboardPreview(preview: ClipboardPreview?) {
        foregroundClipboard.value = preview
    }

    fun clearNoticeMessage() {
        noticeMessage.value = null
    }

    fun showNotice(message: String) {
        noticeMessage.value = message
    }

    fun togglePinned(id: Long) {
        viewModelScope.launch { clipboardRepository.togglePinned(id) }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { clipboardRepository.toggleFavorite(id) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { clipboardRepository.deleteEntry(id) }
    }

    fun clearAll() {
        viewModelScope.launch { clipboardRepository.clearAll() }
    }

    fun saveDraftText() {
        val draft = saveDraft.value.trim()
        if (draft.isBlank()) return

        viewModelScope.launch {
            val saved = clipboardRepository.saveText(draft)
            if (saved != null) {
                focusScratchpad()
                saveDraft.value = ""
                saveSheetVisible.value = false
                noticeMessage.value = "Saved to ClipKeeper"
            } else {
                noticeMessage.value = "Nothing was saved"
            }
        }
    }

    fun saveCurrentClipboard(clipData: ClipData?) {
        viewModelScope.launch {
            val saved = clipboardRepository.saveExplicitClip(clipData, sourcePackage = "Clipboard")
            if (saved != null) {
                focusScratchpad()
                saveSheetVisible.value = false
                noticeMessage.value = if (saved.kind == ClipContentKind.IMAGE) {
                    "Clipboard image saved"
                } else {
                    "Clipboard text saved"
                }
            } else {
                noticeMessage.value = "Nothing supported is on the clipboard"
            }
        }
    }

    fun importSharedText(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        processSharedRequest(SharedSaveRequest.Text(normalized))
    }

    fun importSharedClip(
        clipData: ClipData,
        previewText: String,
    ) {
        processSharedRequest(
            SharedSaveRequest.Clip(
                clipData = clipData,
                preview = PendingSharedPayload(
                    kind = ClipContentKind.IMAGE,
                    previewText = previewText,
                ),
            ),
        )
    }

    fun setRetentionHours(hours: Int) {
        viewModelScope.launch { settingsRepository.setRetentionHours(hours) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmBeforeDelete(enabled) }
    }

    suspend fun loadImagePreview(id: Long): Bitmap? = clipboardRepository.loadImagePreview(id)

    suspend fun exportImage(
        id: Long,
        cacheDir: File,
    ): File? = clipboardRepository.exportImage(id, cacheDir)

    private fun processSharedRequest(request: SharedSaveRequest) {
        focusScratchpad()
        saveSheetVisible.value = false
        saveDraft.value = ""
        pendingSharedRequest.value = request

        viewModelScope.launch {
            val saved = when (request) {
                is SharedSaveRequest.Text -> clipboardRepository.saveText(request.text)
                is SharedSaveRequest.Clip -> clipboardRepository.saveExplicitClip(request.clipData)
            }
            pendingSharedRequest.value = null
            noticeMessage.value = if (saved != null) {
                "Shared item saved to ClipKeeper"
            } else {
                "Nothing was saved from that share"
            }
        }
    }

    private fun focusScratchpad() {
        selectedTab.value = MainTab.SCRATCHPAD
        selectedFilter.value = ScratchpadFilter.ALL
        searchQuery.value = ""
    }
}
