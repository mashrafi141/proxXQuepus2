package com.example.cloud.terabox.repository

import com.example.cloud.core.CloudAccount
import com.example.cloud.core.CloudFile
import com.example.cloud.core.CloudMediaType
import com.example.cloud.terabox.database.CloudAccountEntity
import com.example.cloud.terabox.database.CloudFileEntity

internal fun CloudFileEntity.toDomain(isFavorite: Boolean = false): CloudFile = CloudFile(
    accountId = accountId,
    id = fsId,
    name = name,
    path = path,
    parentPath = parentPath,
    isDirectory = isDirectory,
    size = size,
    modifiedAt = modifiedAt,
    category = category,
    mediaType = parseMediaType(mediaType),
    durationMs = durationMs,
    width = width,
    height = height,
    thumbnailUrl = thumbnailUrl,
    thumbnailUrlSmall = thumbnailUrlSmall,
    thumbnailUrlLarge = thumbnailUrlLarge,
    md5 = md5,
    cachedAt = cachedAt,
    isFavorite = isFavorite
)

internal fun CloudFile.toEntity(): CloudFileEntity = CloudFileEntity(
    accountId = accountId,
    fsId = id,
    name = name,
    path = path,
    parentPath = parentPath,
    isDirectory = isDirectory,
    size = size,
    modifiedAt = modifiedAt,
    category = category,
    mediaType = mediaType.name,
    durationMs = durationMs,
    width = width,
    height = height,
    thumbnailUrl = thumbnailUrl,
    thumbnailUrlSmall = thumbnailUrlSmall,
    thumbnailUrlLarge = thumbnailUrlLarge,
    md5 = md5,
    cachedAt = cachedAt
)

internal fun CloudAccountEntity.toDomain(): CloudAccount = CloudAccount(
    id = accountId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    vipType = vipType,
    userId = userId
)

internal fun parseMediaType(value: String): CloudMediaType = try {
    CloudMediaType.valueOf(value)
} catch (e: IllegalArgumentException) {
    CloudMediaType.OTHER
}
