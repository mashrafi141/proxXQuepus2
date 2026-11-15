package com.example.cloud.core

enum class CloudMediaType {
    VIDEO,
    PHOTO,
    GIF,
    AUDIO,
    OTHER
}

enum class CloudErrorCode {
    NOT_CONFIGURED,
    NETWORK,
    UNAUTHORIZED,
    TOKEN_EXPIRED,
    RATE_LIMITED,
    NOT_FOUND,
    PERMISSION_DENIED,
    API_ERROR,
    PARSE_ERROR,
    UNSUPPORTED,
    CANCELLED,
    UNKNOWN
}

class CloudException(
    val code: CloudErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

data class CloudAccount(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val vipType: Int,
    val userId: String?
)

data class CloudFile(
    val accountId: String,
    val id: String,
    val name: String,
    val path: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val category: Int,
    val mediaType: CloudMediaType,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailUrl: String? = null,
    val thumbnailUrlSmall: String? = null,
    val thumbnailUrlLarge: String? = null,
    val md5: String? = null,
    val cachedAt: Long = 0L,
    val isFavorite: Boolean = false
)

data class CloudBreadcrumb(
    val name: String,
    val path: String
)

data class CloudTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val apiDomain: String,
    val accountId: String
)

data class CloudFolderPage(
    val items: List<CloudFile>,
    val page: Int,
    val hasMore: Boolean
)

enum class CloudPlaybackKind {
    DIRECT_DLINK,
    HLS_STREAM
}

data class CloudPlaybackSource(
    val url: String,
    val kind: CloudPlaybackKind,
    val expiresAtEpochSeconds: Long? = null
)

data class CloudClaimResult(
    val account: CloudAccount,
    val tokens: CloudTokens
)

data class CloudOAuthStart(
    val authorizationUrl: String,
    val state: String,
    val expiresIn: Int
)

interface CloudProvider {
    suspend fun listFolder(path: String, page: Int = 1, pageSize: Int = 100, order: String = "name", descending: Boolean = false): CloudFolderPage
    suspend fun search(query: String, page: Int = 1, pageSize: Int = 100): CloudFolderPage
    suspend fun resolvePlayback(file: CloudFile): CloudPlaybackSource
    suspend fun resolveDownload(file: CloudFile): CloudPlaybackSource
    suspend fun loadBytes(file: CloudFile, maxBytes: Long): ByteArray
    suspend fun rename(file: CloudFile, newName: String): Boolean
    suspend fun delete(files: List<CloudFile>): Boolean
}

fun CloudErrorCode.isReconnectRequired(): Boolean =
    this == CloudErrorCode.UNAUTHORIZED || this == CloudErrorCode.TOKEN_EXPIRED

fun CloudErrorCode.isRecoverableOffline(): Boolean =
    this == CloudErrorCode.NETWORK || this == CloudErrorCode.RATE_LIMITED
