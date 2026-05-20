package com.magicclipboard.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.magicclipboard.app.ui.MagicClipboardApp
import com.magicclipboard.app.ui.theme.MagicClipboardTheme
import com.magicclipboard.app.viewmodel.ClipboardPreview
import com.magicclipboard.app.viewmodel.MainTab
import com.magicclipboard.app.viewmodel.MainViewModel
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.model.ClipEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private lateinit var clipboardManager: ClipboardManager

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        refreshForegroundClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        clipboardManager = getSystemService(ClipboardManager::class.java)
        refreshForegroundClipboard()

        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            MagicClipboardTheme(themeMode = state.settings.themeMode) {
                MagicClipboardApp(
                    state = state,
                    onSearchChange = viewModel::setSearchQuery,
                    onTogglePinned = viewModel::togglePinned,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDeleteEntry = viewModel::deleteEntry,
                    onClearAll = viewModel::clearAll,
                    onRetentionChange = viewModel::setRetentionHours,
                    onThemeModeChange = viewModel::setThemeMode,
                    onConfirmBeforeDeleteChange = viewModel::setConfirmBeforeDelete,
                    onSelectTab = viewModel::selectTab,
                    onSelectFilter = viewModel::selectFilter,
                    onShowSaveSheet = viewModel::showSaveSheet,
                    onHideSaveSheet = viewModel::hideSaveSheet,
                    onSaveDraftChange = viewModel::updateSaveDraft,
                    onSaveDraft = viewModel::saveDraftText,
                    onShowEditSheet = viewModel::showEditSheet,
                    onHideEditSheet = viewModel::hideEditSheet,
                    onEditDraftChange = viewModel::updateEditDraft,
                    onSaveEdit = viewModel::saveEditedText,
                    onSaveCurrentClipboard = {
                        viewModel.saveCurrentClipboard(clipboardManager.primaryClip)
                    },
                    onCopyClip = ::copyClipToClipboard,
                    onShareClip = ::shareClip,
                    onNoticeShown = viewModel::clearNoticeMessage,
                    loadBitmap = viewModel::loadImagePreview,
                )
            }
        }

        handleShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        refreshForegroundClipboard()
    }

    override fun onResume() {
        super.onResume()
        refreshForegroundClipboard()
    }

    override fun onStop() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun refreshForegroundClipboard() {
        viewModel.setForegroundClipboardPreview(buildClipboardPreview(clipboardManager.primaryClip))
    }

    private fun buildClipboardPreview(clipData: ClipData?): ClipboardPreview? {
        if (clipData == null || clipData.itemCount == 0) return null

        val item = clipData.getItemAt(0)
        val uri = item.uri
        if (uri != null) {
            val mimeType = contentResolver.getType(uri).orEmpty()
            if (mimeType.startsWith("image/")) {
                return ClipboardPreview(
                    kind = ClipContentKind.IMAGE,
                    previewText = "Image ready to save",
                )
            }
        }

        val text = item.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return null
        val summary = text.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Clipboard text" }
        return ClipboardPreview(
            kind = ClipContentKind.TEXT,
            previewText = summary,
        )
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return

        when {
            intent.type?.startsWith("image/") == true -> {
                val clipData = resolveSharedImageClip(intent) ?: return
                viewModel.selectTab(MainTab.SCRATCHPAD)
                viewModel.importSharedClip(clipData, previewText = "Shared image")
            }
            !intent.getSharedText().isNullOrBlank() -> {
                viewModel.selectTab(MainTab.SCRATCHPAD)
                viewModel.importSharedText(intent.getSharedText().orEmpty())
            }
        }

        setIntent(Intent(this, MainActivity::class.java))
    }

    private fun resolveSharedImageClip(intent: Intent): ClipData? {
        intent.clipData?.takeIf { it.itemCount > 0 }?.let { return it }
        val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?: run {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        val uri = streamUri ?: intent.data
        return uri?.let { ClipData.newUri(contentResolver, "Shared image", it) }
    }

    private fun Intent.getSharedText(): String? {
        val extraText = getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!extraText.isNullOrBlank()) return extraText
        return clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this@MainActivity)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun copyClipToClipboard(clip: ClipEntry) {
        val text = clip.text?.takeIf { it.isNotBlank() } ?: run {
            viewModel.showNotice("Only text clips can be copied")
            return
        }
        clipboardManager.setPrimaryClip(ClipData.newPlainText("ClipKeeper clip", text))
        refreshForegroundClipboard()
        viewModel.showNotice("Copied to clipboard")
    }

    private fun shareClip(clip: ClipEntry) {
        lifecycleScope.launch {
            val intent = when (clip.kind) {
                ClipContentKind.TEXT -> createTextShareIntent(clip)
                ClipContentKind.IMAGE -> createImageShareIntent(clip)
            } ?: run {
                viewModel.showNotice("Nothing available to share")
                return@launch
            }

            try {
                startActivity(Intent.createChooser(intent, "Share clip"))
            } catch (_: ActivityNotFoundException) {
                viewModel.showNotice("No app available to share this clip")
            }
        }
    }

    private fun createTextShareIntent(clip: ClipEntry): Intent? {
        val text = clip.text?.takeIf { it.isNotBlank() } ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private suspend fun createImageShareIntent(clip: ClipEntry): Intent? {
        val exportedImage = viewModel.exportImage(clip.id, cacheDir) ?: return null
        val imageUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            exportedImage,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = clip.mimeType ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = ClipData.newUri(contentResolver, "Shared image", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
