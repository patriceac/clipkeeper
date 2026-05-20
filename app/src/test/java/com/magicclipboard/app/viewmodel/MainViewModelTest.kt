package com.magicclipboard.app.viewmodel

import android.content.ClipData
import com.magicclipboard.data.ClipboardRepository
import com.magicclipboard.data.model.AppSettings
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.model.ClipEntry
import com.magicclipboard.data.model.ThemeMode
import com.magicclipboard.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save draft text updates saved items state`() = runTest(dispatcher) {
        val repository = FakeClipboardRepository()
        val viewModel = MainViewModel(repository, FakeSettingsRepository())
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.showSaveSheet()
        viewModel.updateSaveDraft("Keep this snippet")
        viewModel.saveDraftText()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.saveSheetVisible)
        assertEquals("", viewModel.uiState.value.saveDraft)
        assertEquals("Saved to ClipKeeper", viewModel.uiState.value.noticeMessage)
        assertTrue(viewModel.uiState.value.clips.any { it.text == "Keep this snippet" })

        collectJob.cancel()
    }

    @Test
    fun `edit text preloads existing content and updates saved item`() = runTest(dispatcher) {
        val originalEntry = ClipEntry(
            id = 7L,
            kind = ClipContentKind.TEXT,
            createdAt = 1L,
            text = "First line\nSecond line\nFull stored text",
        )
        val repository = FakeClipboardRepository(initialEntries = listOf(originalEntry))
        val viewModel = MainViewModel(repository, FakeSettingsRepository())
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.showEditSheet(originalEntry)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.editSheetVisible)
        assertEquals(originalEntry.text, viewModel.uiState.value.editDraft)

        viewModel.updateEditDraft("Updated full text")
        viewModel.saveEditedText()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.editSheetVisible)
        assertEquals("", viewModel.uiState.value.editDraft)
        assertEquals("Updated in ClipKeeper", viewModel.uiState.value.noticeMessage)
        assertTrue(viewModel.uiState.value.clips.any { it.id == 7L && it.text == "Updated full text" })

        collectJob.cancel()
    }

    @Test
    fun `import shared text focuses saved items and stores the clip`() = runTest(dispatcher) {
        val repository = FakeClipboardRepository(
            initialEntries = listOf(
                ClipEntry(
                    id = 1L,
                    kind = ClipContentKind.TEXT,
                    createdAt = 1L,
                    text = "Older clip",
                    isFavorite = true,
                ),
            ),
        )
        val viewModel = MainViewModel(repository, FakeSettingsRepository())
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.selectTab(MainTab.SETTINGS)
        viewModel.selectFilter(ScratchpadFilter.FAVORITES)
        viewModel.setSearchQuery("older")
        viewModel.importSharedText("123456")
        advanceUntilIdle()

        assertEquals(MainTab.SCRATCHPAD, viewModel.uiState.value.selectedTab)
        assertEquals(ScratchpadFilter.ALL, viewModel.uiState.value.selectedFilter)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals("Shared item saved to ClipKeeper", viewModel.uiState.value.noticeMessage)
        assertTrue(viewModel.uiState.value.clips.any { it.text == "123456" })
        assertEquals(null, viewModel.uiState.value.pendingSharedPayload)

        collectJob.cancel()
    }

    @Test
    fun `set theme mode updates settings state`() = runTest(dispatcher) {
        val viewModel = MainViewModel(FakeClipboardRepository(), FakeSettingsRepository())
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)

        collectJob.cancel()
    }

    @Test
    fun `set confirm before delete updates settings state`() = runTest(dispatcher) {
        val viewModel = MainViewModel(FakeClipboardRepository(), FakeSettingsRepository())
        val collectJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.setConfirmBeforeDelete(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.settings.confirmBeforeDelete)

        collectJob.cancel()
    }
}

private class FakeClipboardRepository(
    initialEntries: List<ClipEntry> = emptyList(),
) : ClipboardRepository {
    private val entries = MutableStateFlow(initialEntries)
    private var nextId = (initialEntries.maxOfOrNull(ClipEntry::id) ?: 0L) + 1L

    override fun observeEntries(query: String): Flow<List<ClipEntry>> {
        val normalizedQuery = query.trim().lowercase()
        return entries.map { current ->
            current.filter { clip ->
                normalizedQuery.isBlank() || clip.previewText.lowercase().contains(normalizedQuery)
            }
        }
    }

    override suspend fun saveText(
        text: String,
        sourcePackage: String?,
    ): ClipEntry {
        val entry = ClipEntry(
            id = nextId++,
            kind = ClipContentKind.TEXT,
            createdAt = nextId,
            text = text,
            sourcePackage = sourcePackage,
        )
        entries.value = listOf(entry) + entries.value
        return entry
    }

    override suspend fun updateText(
        id: Long,
        text: String,
    ): ClipEntry? {
        var updatedEntry: ClipEntry? = null
        entries.value = entries.value.map { clip ->
            if (clip.id == id && clip.kind == ClipContentKind.TEXT) {
                clip.copy(text = text).also { updatedEntry = it }
            } else {
                clip
            }
        }
        return updatedEntry
    }

    override suspend fun saveExplicitClip(
        clipData: ClipData?,
        sourcePackage: String?,
    ): ClipEntry? = null

    override suspend fun togglePinned(id: Long) {
        entries.value = entries.value.map { clip ->
            if (clip.id == id) clip.copy(isPinned = !clip.isPinned) else clip
        }
    }

    override suspend fun toggleFavorite(id: Long) {
        entries.value = entries.value.map { clip ->
            if (clip.id == id) clip.copy(isFavorite = !clip.isFavorite) else clip
        }
    }

    override suspend fun deleteEntry(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        entries.value = emptyList()
    }

    override suspend fun pruneExpired(retentionHours: Int) = Unit

    override suspend fun loadImagePreview(id: Long) = null

    override suspend fun exportImage(id: Long, cacheDir: java.io.File) = null
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings())

    override val settings: Flow<AppSettings> = mutableSettings

    override suspend fun setRetentionHours(hours: Int) {
        mutableSettings.value = mutableSettings.value.copy(retentionHours = hours)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        mutableSettings.value = mutableSettings.value.copy(themeMode = mode)
    }

    override suspend fun setConfirmBeforeDelete(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(confirmBeforeDelete = enabled)
    }
}
