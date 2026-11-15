package com.example.cloud.terabox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudAccountDao {

    @Query("SELECT * FROM cloud_accounts ORDER BY connectedAt ASC")
    fun getAccounts(): Flow<List<CloudAccountEntity>>

    @Query("SELECT * FROM cloud_accounts ORDER BY connectedAt ASC")
    suspend fun getAccountsDirect(): List<CloudAccountEntity>

    @Query("SELECT * FROM cloud_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getAccount(accountId: String): CloudAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: CloudAccountEntity)

    @Query("UPDATE cloud_accounts SET lastSyncAt = :timestamp WHERE accountId = :accountId")
    suspend fun updateLastSync(accountId: String, timestamp: Long)

    @Query("DELETE FROM cloud_accounts WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: String)
}

@Dao
interface CloudFileDao {

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND parentPath = :parentPath AND isDirectory = 1 ORDER BY name COLLATE NOCASE ASC")
    fun getFolders(accountId: String, parentPath: String): Flow<List<CloudFileEntity>>

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND parentPath = :parentPath AND isDirectory = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getFiles(accountId: String, parentPath: String): Flow<List<CloudFileEntity>>

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND parentPath = :parentPath ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFolderContents(accountId: String, parentPath: String): List<CloudFileEntity>

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND fsId = :fsId LIMIT 1")
    suspend fun getFile(accountId: String, fsId: String): CloudFileEntity?

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND path = :path LIMIT 1")
    suspend fun getFileByPath(accountId: String, path: String): CloudFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFiles(files: List<CloudFileEntity>)

    @Query("DELETE FROM cloud_files WHERE accountId = :accountId AND parentPath = :parentPath AND fsId NOT IN (:currentFsIds)")
    suspend fun removeMissingFromFolder(accountId: String, parentPath: String, currentFsIds: List<String>)

    @Query("DELETE FROM cloud_files WHERE accountId = :accountId AND parentPath = :parentPath")
    suspend fun deleteFolderCache(accountId: String, parentPath: String)

    @Query("DELETE FROM cloud_files WHERE accountId = :accountId AND fsId IN (:fsIds)")
    suspend fun deleteFiles(accountId: String, fsIds: List<String>)

    @Query("DELETE FROM cloud_files WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("DELETE FROM cloud_files")
    suspend fun clearAll()

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND mediaType = :mediaType AND isDirectory = 0 ORDER BY modifiedAt DESC")
    fun getByType(accountId: String, mediaType: String): Flow<List<CloudFileEntity>>

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND isDirectory = 0 ORDER BY modifiedAt DESC")
    fun getAllMedia(accountId: String): Flow<List<CloudFileEntity>>

    @Query("SELECT COUNT(*) FROM cloud_files WHERE accountId = :accountId AND isDirectory = 0")
    fun getMediaCount(accountId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cloud_files WHERE accountId = :accountId AND mediaType = :mediaType AND isDirectory = 0")
    fun getCountByType(accountId: String, mediaType: String): Flow<Int>

    @Query("SELECT * FROM cloud_files WHERE accountId = :accountId AND isDirectory = 0 AND (name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%') ORDER BY modifiedAt DESC LIMIT 200")
    suspend fun searchCached(accountId: String, query: String): List<CloudFileEntity>
}

@Dao
interface CloudRootDao {

    @Query("SELECT * FROM cloud_roots WHERE accountId = :accountId LIMIT 1")
    fun getRoot(accountId: String): Flow<CloudRootEntity?>

    @Query("SELECT * FROM cloud_roots WHERE accountId = :accountId LIMIT 1")
    suspend fun getRootDirect(accountId: String): CloudRootEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRoot(root: CloudRootEntity)

    @Query("DELETE FROM cloud_roots WHERE accountId = :accountId")
    suspend fun deleteRoot(accountId: String)
}

@Dao
interface CloudMediaStateDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertState(state: CloudMediaStateEntity)

    @Query("UPDATE cloud_media_state SET isFavorite = :favorite WHERE accountId = :accountId AND fsId = :fsId")
    suspend fun setFavorite(accountId: String, fsId: String, favorite: Boolean)

    @Query("UPDATE cloud_media_state SET lastViewedAt = :timestamp WHERE accountId = :accountId AND fsId = :fsId")
    suspend fun setLastViewed(accountId: String, fsId: String, timestamp: Long)

    @Query("UPDATE cloud_media_state SET playbackPositionMs = :position WHERE accountId = :accountId AND fsId = :fsId")
    suspend fun setPlaybackPosition(accountId: String, fsId: String, position: Long)

    @Query("SELECT * FROM cloud_media_state WHERE accountId = :accountId AND fsId = :fsId LIMIT 1")
    suspend fun getState(accountId: String, fsId: String): CloudMediaStateEntity?

    @Query("SELECT f.* FROM cloud_files f INNER JOIN cloud_media_state s ON f.accountId = s.accountId AND f.fsId = s.fsId WHERE s.accountId = :accountId AND s.isFavorite = 1 AND f.isDirectory = 0 ORDER BY f.modifiedAt DESC")
    fun getFavorites(accountId: String): Flow<List<CloudFileEntity>>

    @Query("SELECT f.* FROM cloud_files f INNER JOIN cloud_media_state s ON f.accountId = s.accountId AND f.fsId = s.fsId WHERE s.accountId = :accountId AND s.lastViewedAt IS NOT NULL AND f.isDirectory = 0 ORDER BY s.lastViewedAt DESC LIMIT 100")
    fun getRecent(accountId: String): Flow<List<CloudFileEntity>>

    @Query("SELECT COUNT(*) FROM cloud_media_state WHERE accountId = :accountId AND isFavorite = 1")
    fun getFavoriteCount(accountId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cloud_media_state WHERE accountId = :accountId AND lastViewedAt IS NOT NULL")
    fun getRecentCount(accountId: String): Flow<Int>

    @Query("DELETE FROM cloud_media_state WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("DELETE FROM cloud_media_state")
    suspend fun clearAll()
}

@Dao
interface CloudSyncStateDao {

    @Query("SELECT * FROM cloud_sync_state WHERE accountId = :accountId AND path = :path LIMIT 1")
    suspend fun getSyncState(accountId: String, path: String): CloudSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSyncState(state: CloudSyncStateEntity)

    @Query("DELETE FROM cloud_sync_state WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("DELETE FROM cloud_sync_state")
    suspend fun clearAll()
}
