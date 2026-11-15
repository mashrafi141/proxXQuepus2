package com.example.cloud.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.core.CloudAccount
import com.example.cloud.core.CloudFile
import com.example.cloud.core.CloudMediaType
import com.example.cloud.ui.components.CloudAvatar
import com.example.cloud.ui.components.CloudBottomBar
import com.example.cloud.ui.components.CloudFolderCard
import com.example.cloud.ui.components.CloudMediaCard
import com.example.cloud.viewmodel.CloudScreen
import com.example.cloud.viewmodel.CloudViewModel
import com.example.model.SortDirection
import com.example.model.SortField
import com.example.ui.theme.AccentWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CloudBrowseScreen(
    viewModel: CloudViewModel,
    account: CloudAccount,
    onSelectLocalMode: () -> Unit
) {
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val cachedFiles by viewModel.cachedFiles.collectAsState()
    val folders by viewModel.visibleFolders.collectAsState()
    val media by viewModel.visibleMedia.collectAsState()
    val filter by viewModel.mediaTypeFilter.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val browseError by viewModel.browseError.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var folderOptions by remember { mutableStateOf<CloudFile?>(null) }
    var downloadTarget by remember { mutableStateOf<CloudFile?>(null) }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = downloadTarget
        downloadTarget = null
        if (uri != null && target != null) {
            viewModel.downloadTo(target, uri)
        }
    }

    fun startDownload(file: CloudFile) {
        downloadTarget = file
        downloadLauncher.launch(file.name)
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }
    LaunchedEffect(downloadState.message, downloadState.isDownloading) {
        if (!downloadState.isDownloading && downloadState.message != null) {
            snackbarHostState.showSnackbar(downloadState.message!!)
            viewModel.clearDownloadState()
        }
    }

    val isSelectionMode = selectedIds.isNotEmpty()
    val allCount = cachedFiles.size
    val photoCount = remember(cachedFiles) { cachedFiles.count { it.mediaType == CloudMediaType.PHOTO } }
    val videoCount = remember(cachedFiles) { cachedFiles.count { it.mediaType == CloudMediaType.VIDEO } }
    val gifCount = remember(cachedFiles) { cachedFiles.count { it.mediaType == CloudMediaType.GIF } }
    val audioCount = remember(cachedFiles) { cachedFiles.count { it.mediaType == CloudMediaType.AUDIO } }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            "${selectedIds.size} selected",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll(media) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = CyanPrimary)
                        }
                        if (selectedIds.size == 1) {
                            val selected = viewModel.selectedFiles().singleOrNull()
                            IconButton(onClick = {
                                if (selected != null) {
                                    renameText = selected.name
                                    showRenameDialog = true
                                }
                            }) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename", tint = CyanPrimary)
                            }
                            IconButton(onClick = {
                                if (selected != null) startDownload(selected)
                                else scope.launch { snackbarHostState.showSnackbar("Select one file to download.") }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = CyanPrimary)
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = Color(0xFFFF5252))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("ProxXQuepus", color = CyanPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Cloud Mode", color = TextSecondary, fontSize = 9.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            CloudAvatar(
                                url = account.avatarUrl,
                                displayName = account.displayName,
                                imageLoader = viewModel.thumbnailLoader.imageLoader,
                                modifier = Modifier
                                    .size(25.dp)
                                    .clickable { viewModel.setScreen(CloudScreen.Settings) }
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.openSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name (A to Z)", color = TextPrimary) },
                                    onClick = {
                                        viewModel.setSort(SortField.NAME, SortDirection.ASCENDING)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z to A)", color = TextPrimary) },
                                    onClick = {
                                        viewModel.setSort(SortField.NAME, SortDirection.DESCENDING)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Newest first)", color = TextPrimary) },
                                    onClick = {
                                        viewModel.setSort(SortField.DATE, SortDirection.DESCENDING)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest first)", color = TextPrimary) },
                                    onClick = {
                                        viewModel.setSort(SortField.DATE, SortDirection.ASCENDING)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size (Largest first)", color = TextPrimary) },
                                    onClick = {
                                        viewModel.setSort(SortField.SIZE, SortDirection.DESCENDING)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.refreshCurrentFolder() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            }
        },
        bottomBar = {
            CloudBottomBar(
                currentScreen = CloudScreen.Browse,
                onBrowse = {},
                onCollections = { viewModel.setScreen(CloudScreen.Collections) },
                onSettings = { viewModel.setScreen(CloudScreen.Settings) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (downloadState.isDownloading) {
                LinearProgressIndicator(
                    color = CyanPrimary,
                    trackColor = DarkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isRefreshing || (isLoading && (folders.isNotEmpty() || media.isNotEmpty()))) {
                LinearProgressIndicator(
                    color = CyanPrimary,
                    trackColor = DarkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { index, crumb ->
                    Text(
                        text = crumb.name,
                        color = if (index == breadcrumbs.lastIndex) CyanPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.navigateToBreadcrumb(index) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    if (index < breadcrumbs.lastIndex) {
                        Text("›", color = TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 2.dp))
                    }
                }
            }

            if (isOffline) {
                CloudBanner(
                    text = browseError ?: "Offline — showing cached cloud metadata. Pull refresh when online.",
                    color = AccentWarning
                )
            } else if (browseError != null) {
                CloudBanner(text = browseError!!, color = Color(0xFFFF5252), actionText = "Retry") {
                    viewModel.refreshCurrentFolder()
                }
            }

            if (cachedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloudFilterChip("All", allCount, Icons.Default.ViewModule, filter == null) {
                        viewModel.setMediaTypeFilter(null)
                    }
                    if (photoCount > 0) {
                        CloudFilterChip("Photos", photoCount, Icons.Default.Photo, filter == CloudMediaType.PHOTO) {
                            viewModel.setMediaTypeFilter(if (filter == CloudMediaType.PHOTO) null else CloudMediaType.PHOTO)
                        }
                    }
                    if (videoCount > 0) {
                        CloudFilterChip("Videos", videoCount, Icons.Default.Movie, filter == CloudMediaType.VIDEO) {
                            viewModel.setMediaTypeFilter(if (filter == CloudMediaType.VIDEO) null else CloudMediaType.VIDEO)
                        }
                    }
                    if (gifCount > 0) {
                        CloudFilterChip("GIFs", gifCount, Icons.Default.Gif, filter == CloudMediaType.GIF) {
                            viewModel.setMediaTypeFilter(if (filter == CloudMediaType.GIF) null else CloudMediaType.GIF)
                        }
                    }
                    if (audioCount > 0) {
                        CloudFilterChip("Audio", audioCount, Icons.Default.MusicNote, filter == CloudMediaType.AUDIO) {
                            viewModel.setMediaTypeFilter(if (filter == CloudMediaType.AUDIO) null else CloudMediaType.AUDIO)
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (folders.isNotEmpty()) {
                        item(key = "cloud_header_folders", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "FOLDERS (${folders.size})",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(folders, key = { "cloud_folder_${it.id}" }, span = { GridItemSpan(3) }) { folder ->
                            CloudFolderCard(
                                folder = folder,
                                thumbnailLoader = viewModel.thumbnailLoader,
                                onClick = { viewModel.openFolder(folder) },
                                onLongClick = { folderOptions = folder }
                            )
                        }
                    }

                    if (media.isNotEmpty()) {
                        item(key = "cloud_header_media", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "MEDIA (${media.size})",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(media, key = { "cloud_media_${it.id}" }, span = { GridItemSpan(2) }) { file ->
                            CloudMediaCard(
                                file = file,
                                thumbnailLoader = viewModel.thumbnailLoader,
                                isSelected = selectedIds.contains(file.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(file)
                                    } else {
                                        viewModel.openViewer(media, media.indexOf(file))
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(file) }
                            )
                        }
                    }

                    if (isLoading && folders.isEmpty() && media.isEmpty()) {
                        item(key = "cloud_loading", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(34.dp))
                            }
                        }
                    }

                    if (!isLoading && folders.isEmpty() && media.isEmpty() && browseError == null) {
                        item(key = "cloud_empty", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        if (filter != null) "No ${filter!!.name.lowercase()} files in this folder"
                                        else "No cloud media in this folder",
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    if (filter == null && hasMore) {
                        item(key = "cloud_load_more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text("Load more", color = CyanPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete from TeraBox", color = TextPrimary) },
            text = {
                Text(
                    "This permanently deletes $count selected item(s) from your TeraBox account. " +
                        "This action cannot be undone and may require TeraBox write permission.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete from Cloud", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Cloud Item", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("New name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameDialog = false
                        viewModel.renameSelected(renameText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text("Rename", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

    folderOptions?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderOptions = null },
            title = {
                Text(folder.name, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = { Text(folder.path, color = TextSecondary, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    folderOptions = null
                    viewModel.openFolder(folder)
                }) {
                    Text("Open", color = CyanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    folderOptions = null
                    viewModel.selectRoot(folder)
                }) {
                    Text("Set as Cloud Root", color = AccentWarning)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }
}

@Composable
private fun CloudBanner(
    text: String,
    color: Color,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = color, fontSize = 11.sp, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CloudFilterChip(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.Black else CyanPrimary,
                modifier = Modifier.size(15.dp)
            )
        },
        label = {
            Text(
                "$label ($count)",
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.Black else TextPrimary
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanPrimary,
            containerColor = DarkSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) CyanPrimary else DarkSurfaceBorder
        )
    )
}
