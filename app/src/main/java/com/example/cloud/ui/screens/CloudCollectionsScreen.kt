package com.example.cloud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.core.CloudFile
import com.example.cloud.ui.components.CloudBottomBar
import com.example.cloud.ui.components.CloudMediaCard
import com.example.cloud.viewmodel.CloudCollectionType
import com.example.cloud.viewmodel.CloudScreen
import com.example.cloud.viewmodel.CloudViewModel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudCollectionsScreen(
    viewModel: CloudViewModel
) {
    val totalCount by viewModel.totalCount.collectAsState()
    val videoCount by viewModel.videoCount.collectAsState()
    val photoCount by viewModel.photoCount.collectAsState()
    val gifCount by viewModel.gifCount.collectAsState()
    val audioCount by viewModel.audioCount.collectAsState()
    val favoriteCount by viewModel.favoriteCount.collectAsState()
    val recentCount by viewModel.recentCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Collections", color = CyanPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        bottomBar = {
            CloudBottomBar(
                currentScreen = CloudScreen.Collections,
                onBrowse = { viewModel.setScreen(CloudScreen.Browse) },
                onCollections = {},
                onSettings = { viewModel.setScreen(CloudScreen.Settings) }
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SAVED CLOUD COLLECTIONS", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CloudCollectionCard(
                    title = "Favorites",
                    count = favoriteCount,
                    icon = Icons.Default.Favorite,
                    iconColor = Color(0xFFFF5252),
                    onClick = { viewModel.openCollection(CloudCollectionType.FAVORITES, "Cloud Favorites") },
                    modifier = Modifier.weight(1f)
                )
                CloudCollectionCard(
                    title = "Recent",
                    count = recentCount,
                    icon = Icons.Default.History,
                    iconColor = VioletSecondary,
                    onClick = { viewModel.openCollection(CloudCollectionType.RECENT, "Recently Viewed") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("MEDIA TYPES (CACHED METADATA)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CloudCollectionCard(
                    title = "Videos",
                    count = videoCount,
                    icon = Icons.Default.Movie,
                    iconColor = CyanPrimary,
                    onClick = { viewModel.openCollection(CloudCollectionType.VIDEOS, "Cloud Videos") },
                    modifier = Modifier.weight(1f)
                )
                CloudCollectionCard(
                    title = "Photos",
                    count = photoCount,
                    icon = Icons.Default.Photo,
                    iconColor = Color(0xFF00E676),
                    onClick = { viewModel.openCollection(CloudCollectionType.PHOTOS, "Cloud Photos") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CloudCollectionCard(
                    title = "GIFs",
                    count = gifCount,
                    icon = Icons.Default.Gif,
                    iconColor = Color(0xFFFF9100),
                    onClick = { viewModel.openCollection(CloudCollectionType.GIFS, "Cloud GIFs") },
                    modifier = Modifier.weight(1f)
                )
                CloudCollectionCard(
                    title = "Audio",
                    count = audioCount,
                    icon = Icons.Default.MusicNote,
                    iconColor = Color(0xFF00E5FF),
                    onClick = { viewModel.openCollection(CloudCollectionType.AUDIO, "Cloud Audio") },
                    modifier = Modifier.weight(1f)
                )
            }

            CloudCollectionCard(
                title = "All Cached Media",
                count = totalCount,
                icon = Icons.Default.ViewModule,
                iconColor = Color.White,
                onClick = { viewModel.openCollection(CloudCollectionType.ALL, "All Cloud Media") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Cloud collections are stored per connected TeraBox account. Counts reflect cached cloud metadata on this device.",
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CloudCollectionCard(
    title: String,
    count: Int,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("$count items", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudCollectionViewScreen(
    viewModel: CloudViewModel,
    title: String,
    onBack: () -> Unit
) {
    val collectionFiles by viewModel.collectionItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = CyanPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        if (collectionFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No cached items in this cloud collection yet",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(collectionFiles, key = { it.id }) { file ->
                    CloudMediaCard(
                        file = file,
                        thumbnailLoader = viewModel.thumbnailLoader,
                        isSelected = false,
                        isSelectionMode = false,
                        onClick = { viewModel.openViewer(collectionFiles, collectionFiles.indexOf(file)) },
                        onLongClick = {}
                    )
                }
            }
        }
    }
}
