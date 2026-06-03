package com.magicclipboard.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magicclipboard.app.viewmodel.ClipboardPreview
import com.magicclipboard.app.viewmodel.MainTab
import com.magicclipboard.app.viewmodel.MainUiState
import com.magicclipboard.app.viewmodel.PendingSharedPayload
import com.magicclipboard.app.viewmodel.ScratchpadFilter
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.model.ClipEntry
import com.magicclipboard.data.model.ThemeMode
import kotlin.math.abs
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(8.dp)
private val ControlShape = RoundedCornerShape(12.dp)
private val MediaShape = RoundedCornerShape(8.dp)
private val FavoriteActiveContainer = Color(0xFFD29A2E)
private val FavoriteActiveContent = Color(0xFFFFFFFF)
private val PinActiveContainer = Color(0xFF66B8A7)
private val PinActiveContent = Color(0xFFFFFFFF)
private const val HoursPerDay = 24
private const val RetentionHourLimit = 48
private val RetentionSliderStops = listOf(1, 3, 6, 12, 24, 48, 72, 120, 168)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicClipboardApp(
    state: MainUiState,
    onSearchChange: (String) -> Unit,
    onTogglePinned: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    onRetentionChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onConfirmBeforeDeleteChange: (Boolean) -> Unit,
    onSelectTab: (MainTab) -> Unit,
    onSelectFilter: (ScratchpadFilter) -> Unit,
    onShowSaveSheet: () -> Unit,
    onHideSaveSheet: () -> Unit,
    onSaveDraftChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onShowEditSheet: (ClipEntry) -> Unit,
    onHideEditSheet: () -> Unit,
    onEditDraftChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onSaveCurrentClipboard: () -> Unit,
    onCopyClip: (ClipEntry) -> Unit,
    onShareClip: (ClipEntry) -> Unit,
    onNoticeShown: () -> Unit,
    loadBitmap: suspend (Long) -> Bitmap?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val requestDeleteEntry: (Long) -> Unit = { id ->
        if (state.settings.confirmBeforeDelete) {
            pendingDeleteEntryId = id
        } else {
            onDeleteEntry(id)
        }
    }

    LaunchedEffect(state.noticeMessage) {
        val message = state.noticeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onNoticeShown()
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    title = {
                        Text(
                            text = if (state.selectedTab == MainTab.SCRATCHPAD) "ClipKeeper" else "Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                ) {
                    NavigationBarItem(
                        selected = state.selectedTab == MainTab.SCRATCHPAD,
                        onClick = { onSelectTab(MainTab.SCRATCHPAD) },
                        icon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
                        label = { Text("Clipboard") },
                    )
                    NavigationBarItem(
                        selected = state.selectedTab == MainTab.SETTINGS,
                        onClick = { onSelectTab(MainTab.SETTINGS) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            },
            floatingActionButton = {
                if (state.selectedTab == MainTab.SCRATCHPAD) {
                    ExtendedFloatingActionButton(
                        onClick = onShowSaveSheet,
                        shape = PanelShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("Save") },
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 18.dp),
            ) {
                when (state.selectedTab) {
                    MainTab.SCRATCHPAD -> ScratchpadScreen(
                        state = state,
                        onSearchChange = onSearchChange,
                        onSelectFilter = onSelectFilter,
                        onTogglePinned = onTogglePinned,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteEntry = requestDeleteEntry,
                        onClearAll = onClearAll,
                        onCopyClip = onCopyClip,
                        onShareClip = onShareClip,
                        onEditClip = onShowEditSheet,
                        loadBitmap = loadBitmap,
                    )
                    MainTab.SETTINGS -> SettingsScreen(
                        themeMode = state.settings.themeMode,
                        onThemeModeChange = onThemeModeChange,
                        retentionHours = state.settings.retentionHours,
                        onRetentionChange = onRetentionChange,
                        confirmBeforeDelete = state.settings.confirmBeforeDelete,
                        onConfirmBeforeDeleteChange = onConfirmBeforeDeleteChange,
                    )
                }
            }
        }
    }

    if (state.saveSheetVisible) {
        SaveSheet(
            draft = state.saveDraft,
            foregroundClipboard = state.foregroundClipboard,
            onDismiss = onHideSaveSheet,
            onDraftChange = onSaveDraftChange,
            onSaveDraft = onSaveDraft,
            onSaveCurrentClipboard = onSaveCurrentClipboard,
        )
    }

    if (state.editSheetVisible) {
        EditTextSheet(
            draft = state.editDraft,
            onDismiss = onHideEditSheet,
            onDraftChange = onEditDraftChange,
            onSave = onSaveEdit,
        )
    }

    if (pendingDeleteEntryId != null) {
        DeleteConfirmationSheet(
            onDismiss = { pendingDeleteEntryId = null },
            onConfirmDelete = {
                val entryId = pendingDeleteEntryId ?: return@DeleteConfirmationSheet
                pendingDeleteEntryId = null
                onDeleteEntry(entryId)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScratchpadScreen(
    state: MainUiState,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (ScratchpadFilter) -> Unit,
    onTogglePinned: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    onCopyClip: (ClipEntry) -> Unit,
    onShareClip: (ClipEntry) -> Unit,
    onEditClip: (ClipEntry) -> Unit,
    loadBitmap: suspend (Long) -> Bitmap?,
) {
    val filteredClips = remember(state.clips, state.selectedFilter) {
        when (state.selectedFilter) {
            ScratchpadFilter.ALL -> state.clips
            ScratchpadFilter.PINNED -> state.clips.filter { it.isPinned }
            ScratchpadFilter.FAVORITES -> state.clips.filter { it.isFavorite }
        }
    }
    val pinnedClips = remember(filteredClips, state.selectedFilter) {
        when (state.selectedFilter) {
            ScratchpadFilter.ALL -> filteredClips.filter { it.isPinned }
            ScratchpadFilter.PINNED -> filteredClips
            ScratchpadFilter.FAVORITES -> filteredClips.filter { it.isPinned }
        }
    }
    val secondaryClips = remember(filteredClips, state.selectedFilter) {
        when (state.selectedFilter) {
            ScratchpadFilter.ALL -> filteredClips.filterNot { it.isPinned }
            ScratchpadFilter.PINNED -> emptyList()
            ScratchpadFilter.FAVORITES -> filteredClips.filterNot { it.isPinned }
        }
    }
    val visibleCountLabel = if (filteredClips.size == 1) {
        "1 visible item"
    } else {
        "${filteredClips.size} visible items"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.pendingSharedPayload?.let { payload ->
            SharedImportCard(payload = payload)
        }
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = ControlShape,
            label = { Text("Search saved items") },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScratchpadFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    shape = ControlShape,
                    label = { Text(filter.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = visibleCountLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onClearAll,
                enabled = state.clips.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Clear all")
            }
        }
        if (filteredClips.isEmpty()) {
            EmptyState(
                title = state.emptyTitle,
                body = state.emptyBody,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pinnedClips.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Pinned")
                    }
                    items(pinnedClips, key = { it.id }) { clip ->
                        ClipCard(
                            clip = clip,
                            onTogglePinned = { onTogglePinned(clip.id) },
                            onToggleFavorite = { onToggleFavorite(clip.id) },
                            onDelete = { onDeleteEntry(clip.id) },
                            onCopy = { onCopyClip(clip) },
                            onShare = { onShareClip(clip) },
                            onEdit = { onEditClip(clip) },
                            loadBitmap = loadBitmap,
                        )
                    }
                }
                if (secondaryClips.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = when (state.selectedFilter) {
                                ScratchpadFilter.ALL -> "Recent"
                                ScratchpadFilter.PINNED -> "Pinned"
                                ScratchpadFilter.FAVORITES -> "Favorites"
                            },
                        )
                    }
                    items(secondaryClips, key = { it.id }) { clip ->
                        ClipCard(
                            clip = clip,
                            onTogglePinned = { onTogglePinned(clip.id) },
                            onToggleFavorite = { onToggleFavorite(clip.id) },
                            onDelete = { onDeleteEntry(clip.id) },
                            onCopy = { onCopyClip(clip) },
                            onShare = { onShareClip(clip) },
                            onEdit = { onEditClip(clip) },
                            loadBitmap = loadBitmap,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedImportCard(
    payload: PendingSharedPayload,
) {
    StatusCard(
        title = "Saving shared item",
        body = when (payload.kind) {
            ClipContentKind.TEXT -> payload.previewText
            ClipContentKind.IMAGE -> "Importing a shared image."
        },
    )
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
) {
    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = PanelShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipCard(
    clip: ClipEntry,
    onTogglePinned: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    loadBitmap: suspend (Long) -> Bitmap?,
) {
    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(10.dp),
                    ) {
                        Icon(
                            imageVector = if (clip.kind == ClipContentKind.TEXT) Icons.Outlined.EditNote else Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column {
                        Text(
                            text = if (clip.kind == ClipContentKind.TEXT) "Text snippet" else "Saved image",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = clip.sourcePackage ?: "Saved locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onToggleFavorite,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (clip.isFavorite) FavoriteActiveContainer else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (clip.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (clip.isFavorite) "Remove favorite" else "Favorite",
                                tint = if (clip.isFavorite) FavoriteActiveContent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onTogglePinned,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (clip.isPinned) PinActiveContainer else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = if (clip.isPinned) "Unpin" else "Pin",
                                tint = if (clip.isPinned) PinActiveContent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onDelete,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (clip.kind == ClipContentKind.IMAGE) {
                val bitmap by produceState<Bitmap?>(initialValue = null, key1 = clip.id) {
                    value = loadBitmap(clip.id)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Saved image preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(MediaShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                        alignment = Alignment.Center,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium,
                    )
                } else {
                    EmptyState(
                        title = "Preview unavailable",
                        body = "The image is stored securely and can still be pasted or shared again later.",
                    )
                }
            } else {
                Text(
                    text = clip.previewText,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (clip.kind == ClipContentKind.TEXT && !clip.text.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = ControlShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = onCopy,
                        shape = ControlShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Copy")
                    }
                }
                OutlinedButton(
                    onClick = onShare,
                    shape = ControlShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Share")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTextSheet(
    draft: String,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Edit text",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Review and update the full saved text.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 8,
                shape = ControlShape,
                label = { Text("Saved text") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = ControlShape,
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = draft.isNotBlank(),
                    shape = ControlShape,
                ) {
                    Text("Save changes")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(
    draft: String,
    foregroundClipboard: ClipboardPreview?,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSaveCurrentClipboard: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Save to ClipKeeper",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Save a note or stash the current clipboard.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = ControlShape,
                label = { Text("Type something to keep") },
            )
            Button(
                onClick = onSaveDraft,
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.isNotBlank(),
                shape = ControlShape,
            ) {
                Text("Save note")
            }
            if (foregroundClipboard != null) {
                ClipboardPreviewCard(
                    preview = foregroundClipboard,
                    onSaveCurrentClipboard = onSaveCurrentClipboard,
                )
            } else {
                OutlinedCard(shape = PanelShape) {
                    Text(
                        text = "Copy text or an image first if you want to stash the current clipboard.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmationSheet(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = "Delete snippet?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "This action can't be undone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = ControlShape,
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirmDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ClipboardPreviewCard(
    preview: ClipboardPreview,
    onSaveCurrentClipboard: () -> Unit,
) {
    OutlinedCard(shape = PanelShape) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (preview.kind == ClipContentKind.TEXT) Icons.Outlined.ContentPaste else Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "Current clipboard",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = preview.previewText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedButton(
                onClick = onSaveCurrentClipboard,
                modifier = Modifier.fillMaxWidth(),
                shape = ControlShape,
            ) {
                Text("Save current clipboard")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    retentionHours: Int,
    onRetentionChange: (Int) -> Unit,
    confirmBeforeDelete: Boolean,
    onConfirmBeforeDeleteChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            shape = PanelShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Use the system palette or choose a fixed theme.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = ControlShape,
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }
        Card(
            shape = PanelShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Saved item behavior",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Pinned and favorite items stay. Unprotected recents expire automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Confirm before delete",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Ask before deleting a snippet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = confirmBeforeDelete,
                        onCheckedChange = onConfirmBeforeDeleteChange,
                    )
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Retention for unpinned recents",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = retentionWindowLabel(retentionHours),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = retentionSliderPosition(retentionHours),
                        onValueChange = {
                            val nextRetentionHours = retentionHoursFromSliderValue(it)
                            if (nextRetentionHours != retentionHours) {
                                onRetentionChange(nextRetentionHours)
                            }
                        },
                        valueRange = 0f..RetentionSliderStops.lastIndex.toFloat(),
                        steps = RetentionSliderStops.size - 2,
                    )
                }
            }
        }
        Card(
            shape = PanelShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Local-first by default",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Saved items stay on-device, whether added manually, from the clipboard, or by sharing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text(
                    text = "Pinned and favorite items are durable. Recents without either mark follow the retention window above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val ScratchpadFilter.label: String
    get() = when (this) {
        ScratchpadFilter.ALL -> "All"
        ScratchpadFilter.PINNED -> "Pinned"
        ScratchpadFilter.FAVORITES -> "Favorites"
    }

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

internal fun retentionWindowLabel(retentionHours: Int): String {
    val hours = retentionHours.coerceAtLeast(1)
    if (hours < RetentionHourLimit && hours % HoursPerDay != 0) {
        return quantityLabel(hours, "hour")
    }

    val days = hours / HoursPerDay
    val remainingHours = hours % HoursPerDay

    return if (remainingHours == 0) {
        quantityLabel(days, "day")
    } else {
        "${quantityLabel(days, "day")} ${quantityLabel(remainingHours, "hour")}"
    }
}

internal fun retentionSliderPosition(retentionHours: Int): Float {
    val hours = retentionHours.coerceIn(RetentionSliderStops.first(), RetentionSliderStops.last())
    val exactIndex = RetentionSliderStops.indexOf(hours)
    if (exactIndex >= 0) {
        return exactIndex.toFloat()
    }

    return RetentionSliderStops.indices
        .minByOrNull { index -> abs(RetentionSliderStops[index] - hours) }
        ?.toFloat()
        ?: 0f
}

internal fun retentionHoursFromSliderValue(value: Float): Int {
    val index = value.roundToInt().coerceIn(0, RetentionSliderStops.lastIndex)
    return RetentionSliderStops[index]
}

private fun quantityLabel(value: Int, unit: String): String =
    "$value $unit${if (value == 1) "" else "s"}"

private val MainUiState.emptyTitle: String
    get() = when {
        clips.isEmpty() && selectedFilter == ScratchpadFilter.ALL && searchQuery.isBlank() -> "Nothing saved yet"
        searchQuery.isNotBlank() -> "No matching items"
        selectedFilter == ScratchpadFilter.PINNED -> "No pinned items yet"
        selectedFilter == ScratchpadFilter.FAVORITES -> "No favorites yet"
        else -> "Nothing here yet"
    }

private val MainUiState.emptyBody: String
    get() = when {
        clips.isEmpty() && selectedFilter == ScratchpadFilter.ALL && searchQuery.isBlank() ->
            "Notes, links, codes, and shared images will appear here."
        searchQuery.isNotBlank() ->
            "Try a different keyword or clear the search to browse the rest of your saved items."
        selectedFilter == ScratchpadFilter.PINNED ->
            "Pin important items to keep them at the top and protected from cleanup."
        selectedFilter == ScratchpadFilter.FAVORITES ->
            "Favorite snippets you return to often so they stay easy to find and protected from cleanup."
        else ->
            "Save a note or share something in to get started."
    }
