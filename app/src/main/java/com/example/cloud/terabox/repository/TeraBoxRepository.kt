package com.example.cloud.terabox.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.cloud.core.CloudAccount
import com.example.cloud.core.CloudClaimResult
import com.example.cloud.core.CloudErrorCode
import com.example.cloud.core.CloudException
import com.example.cloud.core.CloudFile
import com.example.cloud.core.CloudFolderPage
import com.example.cloud.core.CloudOAuthStart
import com.example.cloud.core.CloudPlaybackKind
import com.example.cloud.core.CloudPlaybackSource
import com.example.cloud.core.CloudProvider
import com.example.cloud.core.CloudTokens
import com.example.cloud.core.isReconnectRequired
import com.example.cloud.terabox.api.TeraBoxApiClient
import com.example.cloud.terabox.auth.CloudAuthApi
import com.example.cloud.terabox.auth.SecureTokenStore
import com.example.cloud.terabox.database.CloudAccountEntity
import com.example.cloud.terabox.database.CloudDatabase
import com.example.cloud.terabox.database.CloudFileEntity
import com.example.cloud.terabox.database.CloudMediaStateEntity
import com.example.cloud.terabox.database.CloudRootEntity
import com.example.cloud.terabox.database.CloudSyncStateEntity
import com.example.cloud.terabox.settings.CloudPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream

class TeraBoxRepository(
    private val context: Context,
    private val database: CloudDatabase,
    private val preferences: CloudPreferences,
    private val tokenStore: SecureTokenStore,
    val authApi: CloudAuthApi,
    val apiClient: TeraBoxApiClient
) : CloudProvider {

    private data class Session(
        val accountId: String,
        val tokens: CloudTokens
    )

    private val sessionMutex = Mutex()

    val accounts: Flow<List<CloudAccountEntity>> = database.accountDao().getAccounts()

    val activeAccountId: Flow<String?> = preferences.activeAccountId

    fun observeRoot(accountId: String): Flow<CloudRootEntity?> =
        database.rootDao().getRoot(accountId)

    suspend fun getAccount(accountId: String): CloudAccountEntity? =
        database.accountDao().getAccount(accountId)

    suspend fun getRoot(accountId: String): CloudRootEntity? =
        database.rootDao().getRootDirect(accountId)

    suspend fun getActiveAccountId(): String? = preferences.getActiveAccountId()

    suspend fun hasStoredToken(accountId: String): Boolean =
        withContext(Dispatchers.IO) { tokenStore.load(accountId) != null }

    suspend fun getLastPath(accountId: String): String? = preferences.getLastPath(accountId)

    suspend fun saveLastPath(accountId: String, path: String) = preferences.saveLastPath(accountId, path)

    suspend fun setActiveAccount(accountId: String?) = preferences.setActiveAccountId(accountId)

    suspend fun beginAuthorization(): CloudOAuthStart {
        val start = authApi.start()
        preferences.saveOAuthState(start.state)
        return start
    }

    suspend fun getOAuthState(): String? = preferences.getOAuthState()

    suspend fun clearOAuthState() = preferences.clearOAuthState()

    suspend fun exchangeAuthorization(code: String, state: String): CloudClaimResult {
        val result = authApi.exchange(code, state)
        persistClaim(result)
        return result
    }

    suspend fun claimAuthorization(claimCode: String): CloudClaimResult {
        val result = authApi.claim(claimCode)
        persistClaim(result)
        return result
    }

    suspend fun setRoot(accountId: String, root: CloudFile?) {
        database.rootDao().setRoot(
            CloudRootEntity(
                accountId = accountId,
                rootFsId = root?.id?.takeIf { it.isNotBlank() },
                rootPath = root?.path ?: "/",
                rootName = root?.name ?: "TeraBox",
                selectedAt = System.currentTimeMillis()
            )
        )
        preferences.saveLastPath(accountId, root?.path ?: "/")
    }

    fun observeCachedFolders(accountId: String, path: String): Flow<List<CloudFile>> =
        database.fileDao().getFolders(accountId, path).map { list -> list.map { it.toDomain() } }

    fun observeCachedFiles(accountId: String, path: String): Flow<List<CloudFile>> =
        database.fileDao().getFiles(accountId, path).map { list -> list.map { it.toDomain() } }

    fun observeFavoriteFiles(accountId: String): Flow<List<CloudFile>> =
        database.mediaStateDao().getFavorites(accountId).map { list ->
            list.map { it.toDomain(isFavorite = true) }
        }

    fun observeRecentFiles(accountId: String): Flow<List<CloudFile>> =
        database.mediaStateDao().getRecent(accountId).map { list ->
            list.map { it.toDomain(isFavorite = true) }
        }

    fun observeFilesByType(accountId: String, mediaType: String): Flow<List<CloudFile>> =
        database.fileDao().getByType(accountId, mediaType).map { list ->
            list.map { it.toDomain() }
        }

    fun observeAllMedia(accountId: String): Flow<List<CloudFile>> =
        database.fileDao().getAllMedia(accountId).map { list -> list.map { it.toDomain() } }

    fun observeFavoriteCount(accountId: String): Flow<Int> =
        database.mediaStateDao().getFavoriteCount(accountId)

    fun observeRecentCount(accountId: String): Flow<Int> =
        database.mediaStateDao().getRecentCount(accountId)

    fun observeMediaCount(accountId: String): Flow<Int> =
        database.fileDao().getMediaCount(accountId)

    fun observeTypeCount(accountId: String, mediaType: String): Flow<Int> =
        database.fileDao().getCountByType(accountId, mediaType)

    fun observeFavoriteState(accountId: String, fsId: String): Flow<Boolean> {
        return database.mediaStateDao().getFavorites(accountId).map { list ->
            list.any { it.fsId == fsId }
        }
    }

    override suspend fun listFolder(
        path: String,
        page: Int,
        pageSize: Int,
        order: String,
        descending: Boolean
    ): CloudFolderPage {
        val accountId = requireActiveAccountId()
        return withSession { session ->
            val result = apiClient.listFolder(
                accountId = accountId,
                accessToken = session.tokens.accessToken,
                apiDomain = session.tokens.apiDomain,
                path = path,
                page = page,
                pageSize = pageSize,
                order = order,
                descending = descending
            )
            persistFolder(accountId, path, result.items, reset = page == 1)
            result
        }
    }

    override suspend fun search(query: String, page: Int, pageSize: Int): CloudFolderPage {
        val accountId = requireActiveAccountId()
        return try {
            withSession { session ->
                val result = apiClient.search(
                    accountId = accountId,
                    accessToken = session.tokens.accessToken,
                    apiDomain = session.tokens.apiDomain,
                    query = query,
                    page = page,
                    pageSize = pageSize
                )
                if (result.items.isNotEmpty()) {
                    database.fileDao().upsertFiles(result.items.map { it.toEntity() })
                }
                result
            }
        } catch (e: CloudException) {
            if (e.code == CloudErrorCode.NETWORK || e.code == CloudErrorCode.API_ERROR) {
                val cached = database.fileDao().searchCached(accountId, query)
                if (cached.isNotEmpty()) {
                    CloudFolderPage(cached.map { it.toDomain() }, page, false)
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    override suspend fun resolvePlayback(file: CloudFile): CloudPlaybackSource {
        return withSession { session ->
            try {
                val url = apiClient.resolveDownloadUrl(
                    accessToken = session.tokens.accessToken,
                    apiDomain = session.tokens.apiDomain,
                    fsId = file.id
                )
                CloudPlaybackSource(
                    url = url,
                    kind = CloudPlaybackKind.DIRECT_DLINK,
                    expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3600L
                )
            } catch (e: CloudException) {
                if (e.code == CloudErrorCode.PERMISSION_DENIED ||
                    e.code == CloudErrorCode.API_ERROR ||
                    e.code == CloudErrorCode.UNSUPPORTED
                ) {
                    CloudPlaybackSource(
                        url = apiClient.streamingUrl(
                            accessToken = session.tokens.accessToken,
                            apiDomain = session.tokens.apiDomain,
                            path = file.path
                        ),
                        kind = CloudPlaybackKind.HLS_STREAM,
                        expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3600L
                    )
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun resolveDownload(file: CloudFile): CloudPlaybackSource {
        return withSession { session ->
            CloudPlaybackSource(
                url = apiClient.resolveDownloadUrl(
                    accessToken = session.tokens.accessToken,
                    apiDomain = session.tokens.apiDomain,
                    fsId = file.id
                ),
                kind = CloudPlaybackKind.DIRECT_DLINK,
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3600L
            )
        }
    }

    suspend fun resolveStreamingFallback(file: CloudFile): CloudPlaybackSource {
        return withSession { session ->
            CloudPlaybackSource(
                url = apiClient.streamingUrl(
                    accessToken = session.tokens.accessToken,
                    apiDomain = session.tokens.apiDomain,
                    path = file.path
                ),
                kind = CloudPlaybackKind.HLS_STREAM,
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3600L
            )
        }
    }

    override suspend fun loadBytes(file: CloudFile, maxBytes: Long): ByteArray {
        val source = resolveDownload(file)
        return apiClient.loadBytes(source.url, maxBytes)
    }

    suspend fun loadImageBytes(file: CloudFile, maxBytes: Long = 48L * 1024L * 1024L): ByteArray =
        loadBytes(file, maxBytes)

    suspend fun downloadTo(file: CloudFile, output: OutputStream) {
        val source = resolveDownload(file)
        apiClient.downloadTo(source.url, output)
    }

    override suspend fun rename(file: CloudFile, newName: String): Boolean {
        val accountId = requireActiveAccountId()
        return withSession { session ->
            val ok = apiClient.rename(
                accessToken = session.tokens.accessToken,
                apiDomain = session.tokens.apiDomain,
                file = file,
                newName = newName
            )
            if (ok) {
                database.fileDao().deleteFiles(accountId, listOf(file.id))
            }
            ok
        }
    }

    override suspend fun delete(files: List<CloudFile>): Boolean {
        if (files.isEmpty()) return true
        val accountId = requireActiveAccountId()
        return withSession { session ->
            val ok = apiClient.delete(
                accessToken = session.tokens.accessToken,
                apiDomain = session.tokens.apiDomain,
                files = files
            )
            if (ok) {
                database.fileDao().deleteFiles(accountId, files.map { it.id })
            }
            ok
        }
    }

    suspend fun toggleFavorite(accountId: String, file: CloudFile): Boolean {
        database.mediaStateDao().insertState(
            CloudMediaStateEntity(accountId = accountId, fsId = file.id)
        )
        val current = database.mediaStateDao().getState(accountId, file.id)?.isFavorite ?: false
        val next = !current
        database.mediaStateDao().setFavorite(accountId, file.id, next)
        return next
    }

    suspend fun isFavorite(accountId: String, file: CloudFile): Boolean {
        return database.mediaStateDao().getState(accountId, file.id)?.isFavorite ?: false
    }

    suspend fun recordViewed(accountId: String, file: CloudFile) {
        database.mediaStateDao().insertState(
            CloudMediaStateEntity(accountId = accountId, fsId = file.id)
        )
        database.mediaStateDao().setLastViewed(accountId, file.id, System.currentTimeMillis())
    }

    suspend fun savePlaybackPosition(accountId: String, file: CloudFile, positionMs: Long) {
        database.mediaStateDao().insertState(
            CloudMediaStateEntity(accountId = accountId, fsId = file.id)
        )
        database.mediaStateDao().setPlaybackPosition(accountId, file.id, positionMs)
    }

    suspend fun getPlaybackPosition(accountId: String, file: CloudFile): Long {
        return database.mediaStateDao().getState(accountId, file.id)?.playbackPositionMs ?: 0L
    }

    suspend fun disconnect(accountId: String) {
        sessionMutex.withLock {
            database.withTransaction {
                database.fileDao().deleteAllForAccount(accountId)
                database.mediaStateDao().deleteAllForAccount(accountId)
                database.rootDao().deleteRoot(accountId)
                database.syncStateDao().deleteAllForAccount(accountId)
                database.accountDao().deleteAccount(accountId)
            }
            tokenStore.delete(accountId)
            if (preferences.getActiveAccountId() == accountId) {
                val remaining = database.accountDao().getAccountsDirect().firstOrNull()
                preferences.setActiveAccountId(remaining?.accountId)
            }
        }
    }

    suspend fun clearCache(accountId: String) {
        database.fileDao().deleteAllForAccount(accountId)
        database.syncStateDao().deleteAllForAccount(accountId)
    }

    suspend fun clearAllCache() {
        database.fileDao().clearAll()
        database.syncStateDao().clearAll()
    }

    suspend fun getMediaCountDirect(accountId: String): Int =
        database.fileDao().getFolderContents(accountId, "/").size

    private suspend fun persistClaim(result: CloudClaimResult) {
        tokenStore.save(result.tokens)
        database.accountDao().upsertAccount(
            CloudAccountEntity(
                accountId = result.account.id,
                displayName = result.account.displayName,
                avatarUrl = result.account.avatarUrl,
                vipType = result.account.vipType,
                userId = result.account.userId,
                apiDomain = result.tokens.apiDomain
            )
        )
        preferences.setActiveAccountId(result.account.id)
        preferences.clearOAuthState()
    }

    private suspend fun persistFolder(
        accountId: String,
        path: String,
        items: List<CloudFile>,
        reset: Boolean
    ) {
        database.withTransaction {
            if (reset) {
                database.fileDao().deleteFolderCache(accountId, path)
            }
            database.fileDao().upsertFiles(items.map { it.toEntity() })
            database.syncStateDao().setSyncState(
                CloudSyncStateEntity(
                    accountId = accountId,
                    path = path,
                    lastSyncAt = System.currentTimeMillis()
                )
            )
            database.accountDao().updateLastSync(accountId, System.currentTimeMillis())
        }
    }

    private suspend fun requireActiveAccountId(): String {
        return preferences.getActiveAccountId()
            ?: throw CloudException(CloudErrorCode.UNAUTHORIZED, "No TeraBox account is connected")
    }

    private suspend fun <T> withSession(block: suspend (Session) -> T): T {
        val accountId = requireActiveAccountId()
        return sessionMutex.withLock {
            var session = loadSession(accountId)
            try {
                block(session)
            } catch (e: CloudException) {
                if (!e.code.isReconnectRequired()) throw e
                session = refreshSession(accountId, session)
                block(session)
            }
        }
    }

    private suspend fun loadSession(accountId: String): Session {
        var tokens = tokenStore.load(accountId)
            ?: throw CloudException(CloudErrorCode.UNAUTHORIZED, "The TeraBox account token is missing")
        if (tokens.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
            tokens = refreshTokens(tokens)
        }
        return Session(accountId, tokens)
    }

    private suspend fun refreshSession(accountId: String, old: Session): Session {
        val tokens = refreshTokens(old.tokens)
        return Session(accountId, tokens)
    }

    private suspend fun refreshTokens(tokens: CloudTokens): CloudTokens = withContext(Dispatchers.IO) {
        try {
            val result = authApi.refresh(tokens.refreshToken)
            val updated = result.tokens.copy(accountId = tokens.accountId)
            tokenStore.save(updated)
            val existing = database.accountDao().getAccount(tokens.accountId)
            database.accountDao().upsertAccount(
                CloudAccountEntity(
                    accountId = tokens.accountId,
                    displayName = existing?.displayName ?: result.account.displayName,
                    avatarUrl = existing?.avatarUrl ?: result.account.avatarUrl,
                    vipType = result.account.vipType,
                    userId = existing?.userId ?: result.account.userId,
                    apiDomain = updated.apiDomain,
                    connectedAt = existing?.connectedAt ?: System.currentTimeMillis(),
                    lastSyncAt = existing?.lastSyncAt ?: 0L
                )
            )
            updated
        } catch (e: CloudException) {
            if (e.code.isReconnectRequired() || e.code == CloudErrorCode.NOT_CONFIGURED) {
                throw CloudException(
                    CloudErrorCode.UNAUTHORIZED,
                    "The TeraBox connection has expired; reconnect the account",
                    e
                )
            }
            throw e
        }
    }

    suspend fun accountFor(accountId: String): CloudAccount? =
        database.accountDao().getAccount(accountId)?.toDomain()
}
