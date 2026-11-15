package com.example.cloud.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.cloud.core.CloudFile
import com.example.cloud.core.CloudException
import com.example.cloud.core.CloudMediaType
import com.example.player.PlayerGestureDetector
import com.example.player.PlayerGestureFeedback
import com.example.player.VideoPlayerController
import com.example.cloud.ui.components.formatCloudSize
import com.example.cloud.viewmodel.CloudViewModel
import com.example.player.FeedbackIcon
import com.example.ui.components.ZoomablePhoto
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.MediaDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

@Composable
fun CloudViewerScreen(
    viewModel: CloudViewModel,
    onBack: () -> Unit
) {
    val files by viewModel.activeViewerList.collectAsState()
    val viewerIndex by viewModel.viewerIndex.collectAsState()
    val isFavorite by viewModel.viewerFavorite.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    if (files.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = viewerIndex.coerceIn(0, files.lastIndex),
        pageCount = { files.size }
    )
    var isPhotoZoomed by remember { mutableStateOf(false) }
    var downloadTarget by remember { mutableStateOf<CloudFile?>(null) }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = downloadTarget
        downloadTarget = null
        if (uri != null && target != null) viewModel.downloadTo(target, uri)
    }

    LaunchedEffect(downloadState.message, downloadState.isDownloading) {
        if (!downloadState.isDownloading && downloadState.message != null) {
            snackbarHostState.showSnackbar(downloadState.message!!)
            viewModel.clearDownloadState()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isPhotoZoomed = false
        viewModel.onViewerPageChanged(pagerState.currentPage)
    }

    val currentFile = files.getOrNull(pagerState.currentPage) ?: files.first()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            userScrollEnabled = !isPhotoZoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val file = files[page]
            val isCurrent = page == pagerState.currentPage
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                when (file.mediaType) {
                    CloudMediaType.VIDEO, CloudMediaType.AUDIO -> {
                        if (isCurrent) {
                            CloudVideoPage(viewModel = viewModel, file = file)
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                    CloudMediaType.PHOTO -> {
                        CloudPhotoPage(
                            viewModel = viewModel,
                            file = file,
                            isCurrent = isCurrent,
                            onZoomChanged = { isPhotoZoomed = it }
                        )
                    }
                    CloudMediaType.GIF -> {
                        CloudGifPage(viewModel = viewModel, file = file)
                    }
                    CloudMediaType.OTHER -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This cloud file type is not supported in the viewer", color = TextPrimary)
                        }
                    }
                }
            }
        }

        CloudViewerTopBar(
            file = currentFile,
            currentIndex = pagerState.currentPage,
            totalCount = files.size,
            isFavorite = isFavorite,
            onBack = onBack,
            onToggleFavorite = { viewModel.toggleViewerFavorite() },
            onDownload = {
                downloadTarget = currentFile
                downloadLauncher.launch(currentFile.name)
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun CloudViewerTopBar(
    file: CloudFile,
    currentIndex: Int,
    totalCount: Int,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x99000000))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                file.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${currentIndex + 1} / $totalCount  •  ${formatCloudSize(file.size)}",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x99000000))
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Color(0xFFFF5252) else Color.White
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onDownload,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x99000000))
        ) {
            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CloudVideoPage(
    viewModel: CloudViewModel,
    file: CloudFile
) {
    val context = LocalContext.current
    val controller = remember(file.id) { VideoPlayerController(context) }
    val uiState by controller.uiState.collectAsState()
    var areControlsVisible by remember { mutableStateOf(true) }
    var gestureFeedback by remember { mutableStateOf(PlayerGestureFeedback()) }
    var doubleTapSide by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }
    var retries by remember(file.id) { mutableIntStateOf(0) }
    var usedStreamingFallback by remember(file.id) { mutableStateOf(false) }
    var lastPosition by remember(file.id) { mutableLongStateOf(0L) }
    var sliderValue by remember { mutableFloatStateOf(-1f) }

    suspend fun loadSource(startAt: Long) {
        try {
            val source = viewModel.resolvePlayback(file)
            val mimeType = if (source.kind == com.example.cloud.core.CloudPlaybackKind.HLS_STREAM) {
                androidx.media3.common.MimeTypes.APPLICATION_M3U8
            } else null
            controller.setSource(source.url, startAt, mimeType)
            errorMessage = null
        } catch (e: CloudException) {
            errorMessage = e.message ?: "Could not resolve cloud playback"
        }
    }

    LaunchedEffect(file.id, retryNonce) {
        errorMessage = null
        val startAt = viewModel.playbackPosition(file)
        loadSource(startAt)
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        val position = controller.uiState.value.currentPositionMs
        when {
            retries < 1 -> {
                retries++
                delay(300)
                loadSource(position)
            }
            !usedStreamingFallback -> {
                usedStreamingFallback = true
                try {
                    val fallback = viewModel.resolveStreamingFallback(file)
                    controller.setSource(
                        fallback.url,
                        position,
                        androidx.media3.common.MimeTypes.APPLICATION_M3U8
                    )
                    errorMessage = null
                } catch (e: CloudException) {
                    errorMessage = "Playback failed: $message"
                }
            }
            else -> errorMessage = "Playback failed: $message"
        }
    }

    LaunchedEffect(uiState.currentPositionMs) {
        if (uiState.currentPositionMs > 0) lastPosition = uiState.currentPositionMs
    }

    LaunchedEffect(areControlsVisible, uiState.isPlaying) {
        if (areControlsVisible && uiState.isPlaying) {
            delay(4000)
            areControlsVisible = false
        }
    }

    DisposableEffect(file.id) {
        onDispose {
            if (lastPosition > 0) viewModel.savePlaybackPosition(file, lastPosition)
            controller.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerGestureDetector(
            currentPositionMs = uiState.currentPositionMs,
            durationMs = uiState.durationMs,
            onSeek = { controller.seekTo(it) },
            onToggleControls = { areControlsVisible = !areControlsVisible },
            onDoubleTapSeek = { forward ->
                controller.seekRelative(if (forward) 10000L else -10000L)
                doubleTapSide = forward
            },
            onFeedbackUpdate = { gestureFeedback = it },
            onFeedbackDismiss = { gestureFeedback = gestureFeedback.copy(isVisible = false) }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = controller.getPlayer()
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (gestureFeedback.isVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC0A0C10))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (gestureFeedback.iconType == FeedbackIcon.SEEK_FORWARD)
                            Icons.Default.PlayArrow else Icons.Default.Movie,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(gestureFeedback.text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (doubleTapSide != null) {
            LaunchedEffect(doubleTapSide) {
                delay(500)
                doubleTapSide = null
            }
            Text(
                text = if (doubleTapSide == true) "+10s" else "-10s",
                color = CyanPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(if (doubleTapSide == true) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 38.dp)
            )
        }

        if (uiState.isBuffering) {
            CircularProgressIndicator(
                color = CyanPrimary,
                modifier = Modifier.align(Alignment.Center).size(38.dp)
            )
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xDD0A0C10))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(errorMessage!!, color = TextPrimary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = {
                    retries = 0
                    usedStreamingFallback = false
                    retryNonce++
                }) {
                    Text("Resolve a fresh playback URL and retry", color = CyanPrimary, fontSize = 12.sp)
                }
            }
        }

        if (areControlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { controller.togglePlayPause() }) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { controller.toggleMute() }) {
                        Icon(
                            imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeMute
                            else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute",
                            tint = if (uiState.isMuted) Color(0xFFFF5252) else Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (uiState.isEnded) {
                        IconButton(onClick = { controller.replay() }) {
                            Icon(
                                Icons.Default.Replay,
                                contentDescription = "Replay",
                                tint = CyanPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    val duration = uiState.durationMs.coerceAtLeast(1L)
                    val value = if (sliderValue >= 0f) sliderValue else uiState.currentPositionMs.toFloat()
                    Slider(
                        value = value.coerceIn(0f, duration.toFloat()),
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            controller.seekTo(sliderValue.toLong())
                            sliderValue = -1f
                        },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanPrimary,
                            activeTrackColor = CyanPrimary,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                        val next = speeds[(speeds.indexOfFirst { it == uiState.playbackSpeed }.coerceAtLeast(0) + 1) % speeds.size]
                        controller.setPlaybackSpeed(next)
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("${uiState.playbackSpeed}x", color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        MediaDetector.formatDuration(uiState.currentPositionMs),
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        MediaDetector.formatDuration(uiState.durationMs),
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudPhotoPage(
    viewModel: CloudViewModel,
    file: CloudFile,
    isCurrent: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    var bitmap by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(file.id) { mutableStateOf(true) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(file.id, retry) {
        loading = true
        error = null
        try {
            val bytes = viewModel.loadImageBytes(file)
            bitmap = decodeCloudBitmap(bytes, 2048)
            if (bitmap == null) error = "Could not decode this cloud image"
        } catch (e: Exception) {
            error = e.message ?: "Could not load this cloud image"
        } finally {
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> {
                ZoomablePhoto(
                    bitmap = bitmap!!,
                    contentDescription = file.name,
                    uriKey = file.id,
                    onZoomStateChanged = { if (isCurrent) onZoomChanged(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            loading -> CircularProgressIndicator(color = CyanPrimary)
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = TextPrimary, fontSize = 13.sp)
                TextButton(onClick = { retry++ }) {
                    Text("Retry", color = CyanPrimary)
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
private fun CloudGifPage(
    viewModel: CloudViewModel,
    file: CloudFile
) {
    val context = LocalContext.current
    var drawable by remember(file.id) { mutableStateOf<Drawable?>(null) }
    var bitmap by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(file.id) { mutableStateOf(true) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(file.id, retry) {
        loading = true
        error = null
        try {
            val bytes = viewModel.loadImageBytes(file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                drawable = ImageDecoder.decodeDrawable(source)
            } else {
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
            if (drawable == null && bitmap == null) error = "Could not decode this cloud GIF"
        } catch (e: Exception) {
            error = e.message ?: "Could not load this cloud GIF"
        } finally {
            loading = false
        }
    }

    DisposableEffect(drawable) {
        val anim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) drawable as? AnimatedImageDrawable else null
        anim?.start()
        onDispose { anim?.stop() }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            drawable != null -> AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { view ->
                    view.setImageDrawable(drawable)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        (drawable as? AnimatedImageDrawable)?.start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            loading -> CircularProgressIndicator(color = CyanPrimary)
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = TextPrimary, fontSize = 13.sp)
                TextButton(onClick = { retry++ }) {
                    Text("Retry", color = CyanPrimary)
                }
            }
        }
    }
}

private suspend fun decodeCloudBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / (sample * 2) >= maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
