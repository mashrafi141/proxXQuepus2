package com.example.cloud.terabox.thumbnails

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.example.cloud.core.CloudFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CloudThumbnailLoader(private val context: Context) {

    private val cacheDirectory = File(context.cacheDir, "proxxquepus_cloud_thumbs").apply {
        if (!exists()) mkdirs()
    }

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDirectory)
                .maxSizeBytes(64L * 1024L * 1024L)
                .build()
        }
        .build()

    fun thumbnailRequest(file: CloudFile, large: Boolean = false): ImageRequest? {
        val url = when {
            large -> file.thumbnailUrlLarge ?: file.thumbnailUrl ?: file.thumbnailUrlSmall
            else -> file.thumbnailUrl ?: file.thumbnailUrlSmall ?: file.thumbnailUrlLarge
        } ?: return null
        val key = "cloud_thumb_${file.accountId}_${file.id}_${file.modifiedAt}_${if (large) "l" else "s"}"
        return ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .crossfade(true)
            .build()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { imageLoader.memoryCache?.clear() }
        runCatching { imageLoader.diskCache?.clear() }
        runCatching {
            cacheDirectory.deleteRecursively()
            cacheDirectory.mkdirs()
        }
    }
}
