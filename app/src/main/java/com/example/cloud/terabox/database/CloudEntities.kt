package com.example.cloud.terabox.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cloud_accounts")
data class CloudAccountEntity(
    @PrimaryKey val accountId: String,
    val displayName: String,
    val avatarUrl: String?,
    val vipType: Int,
    val userId: String?,
    val apiDomain: String,
    val connectedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = 0L
)

@Entity(
    tableName = "cloud_files",
    primaryKeys = ["accountId", "fsId"],
    indices = [
        Index(value = ["accountId", "parentPath"]),
        Index(value = ["accountId", "path"]),
        Index(value = ["accountId", "mediaType"]),
        Index(value = ["accountId", "modifiedAt"])
    ]
)
data class CloudFileEntity(
    val accountId: String,
    val fsId: String,
    val name: String,
    val path: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val category: Int,
    val mediaType: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailUrl: String? = null,
    val thumbnailUrlSmall: String? = null,
    val thumbnailUrlLarge: String? = null,
    val md5: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cloud_roots", primaryKeys = ["accountId"])
data class CloudRootEntity(
    val accountId: String,
    val rootFsId: String?,
    val rootPath: String,
    val rootName: String,
    val selectedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cloud_media_state", primaryKeys = ["accountId", "fsId"])
data class CloudMediaStateEntity(
    val accountId: String,
    val fsId: String,
    val isFavorite: Boolean = false,
    val lastViewedAt: Long? = null,
    val playbackPositionMs: Long = 0L
)

@Entity(tableName = "cloud_sync_state", primaryKeys = ["accountId", "path"])
data class CloudSyncStateEntity(
    val accountId: String,
    val path: String,
    val lastSyncAt: Long = 0L
)
