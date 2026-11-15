package com.example.cloud.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.cloud.core.CloudFile
import com.example.cloud.core.CloudMediaType
import com.example.cloud.terabox.thumbnails.CloudThumbnailLoader
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.MediaDetector

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudFolderCard(
    folder: CloudFile,
    thumbnailLoader: CloudThumbnailLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val request = thumbnailLoader.thumbnailRequest(folder)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .background(Color(0xFF0F131A)),
            contentAlignment = Alignment.Center
        ) {
            if (request != null) {
                AsyncImage(
                    model = request,
                    imageLoader = thumbnailLoader.imageLoader,
                    contentDescription = folder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = CyanPrimary.copy(alpha = 0.75f),
                    modifier = Modifier.size(40.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0A0C10))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("Folder", color = CyanPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = folder.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (folder.modifiedAt > 0) formatCloudDate(folder.modifiedAt) else "Cloud folder",
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudMediaCard(
    file: CloudFile,
    thumbnailLoader: CloudThumbnailLoader,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val request = thumbnailLoader.thumbnailRequest(file)
    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, CyanPrimary, RoundedCornerShape(10.dp))
    } else {
        Modifier.border(1.dp, DarkSurfaceBorder, RoundedCornerShape(10.dp))
    }
    Column(
        modifier = modifier
            .testTag("cloud_media_${file.name}")
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(borderModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF0F131A))
        ) {
            if (request != null) {
                AsyncImage(
                    model = request,
                    imageLoader = thumbnailLoader.imageLoader,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconFor(file.mediaType),
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            if (file.mediaType == CloudMediaType.VIDEO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .padding(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (file.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            MediaDetector.formatDuration(file.durationMs),
                            color = Color.White,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isSelected) CyanPrimary else Color(0x80000000),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                )
            }
            if (file.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .size(16.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = file.name,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatCloudSize(file.size),
                    color = TextSecondary,
                    fontSize = 9.sp
                )
                if (file.mediaType != CloudMediaType.OTHER) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = file.mediaType.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CloudAvatar(
    url: String?,
    displayName: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0x3300E5FF)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                imageLoader = imageLoader,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = displayName.take(1).uppercase(),
                color = CyanPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

private fun iconFor(type: CloudMediaType) = when (type) {
    CloudMediaType.VIDEO -> Icons.Default.Movie
    CloudMediaType.PHOTO -> Icons.Default.Photo
    CloudMediaType.GIF -> Icons.Default.Gif
    CloudMediaType.AUDIO -> Icons.Default.MusicNote
    CloudMediaType.OTHER -> Icons.Default.Photo
}

fun formatCloudSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}"
    else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}

fun formatCloudDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val formatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
    return formatter.format(java.util.Date(epochMillis))
}
