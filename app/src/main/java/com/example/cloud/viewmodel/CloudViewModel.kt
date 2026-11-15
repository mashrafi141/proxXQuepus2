package com.example.cloud.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.cloud.core.CloudAccount
import com.example.cloud.core.CloudBreadcrumb
import com.example.cloud.core.CloudErrorCode
import com.example.cloud.core.CloudException
import com.example.cloud.core.CloudFile
import com.example.cloud.core.CloudFolderPage
import com.example.cloud.core.CloudMediaType
import com.example.cloud.core.CloudOAuthStart
import com.example.cloud.core.CloudPlaybackSource
import com.example.cloud.core.isReconnectRequired
import com.example.cloud.core.isRecoverableOffline
import com.example.cloud.terabox.api.TeraBoxApiClient
import com.example.cloud.terabox.auth.CloudAuthApi
import com.example.cloud.terabox.auth.SecureTokenStore
import com.example.cloud.terabox.database.CloudDatabase
import com.example.cloud.terabox.repository.TeraBoxRepository
import com.example.cloud.terabox.repository.toDomain
import com.example.cloud.terabox.settings.CloudPreferences
import com.example.cloud.terabox.thumbnails.CloudThumbnailLoader
import com.example.model.SortDirection
import com.example.model.SortField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class CloudSessionState {
    object Loading : CloudSessionState()
    object SetupRequired : CloudSessionState()
    object ConnectInProgress : CloudSessionState()
    data class Connected(val account: CloudAccount) : CloudSessionState()
    data class ReconnectRequired(val message: String) : CloudSessionState()
    data class ConfigError(val message: String) : CloudSessionState()
}

sealed class CloudScreen {
    object Browse : CloudScreen()
    object Collections : CloudScreen()
    object Settings : CloudScreen()
    object Search : CloudScreen()
    data class CollectionView(val title: String, val type: CloudCollectionType) : CloudScreen()
    data class Viewer(val currentIndex: Int) : CloudScreen()
}

enum class CloudCollectionType {
    ALL,
    VIDEOS,
    PHOTOS,
    GIFS,
    AUDIO,
    FAVORITES,
    RECENT
}

data class CloudDownloadState(
    val isDownloading: Boolean = false,
    val fileName: String = "",
    val message: String? = null,
    val isError: Boolean = false
)

class CloudViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CloudDatabase.getDatabase(application)
    private val preferences = CloudPreferences(application)
    private val tokenStore = SecureTokenStore(application)
    private val authApi = CloudAuthApi(BuildConfig.TERABOX_BACKEND_BASE_URL)
    private val apiClient = TeraBoxApiClient()
    val repository = TeraBoxRepository(
        application,
        database,
        preferences,
        tokenStore,
        authApi,
        apiClient
    )
    val thumbnailLoader = CloudThumbnailLoader(application)

    private val _sessionState = MutableStateFlow<CloudSessionState>(CloudSessionState.Loading)
    val sessionState: StateFlow<CloudSessionState> = _sessionState.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    private val _account = MutableStateFlow<CloudAccount?>(null)
    val account: StateFlow<CloudAccount?> = _account.asStateFlow()

    val accounts = repository.accounts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _screen = MutableStateFlow<CloudScreen>(CloudScreen.Browse)
    val screen: StateFlow<CloudScreen> = _screen.asStateFlow()
    private val screenStack = mutableListOf<CloudScreen>()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError: StateFlow<String?> = _setupError.asStateFlow()

    private val _authorizationUrl = MutableStateFlow<String?>(null)
    val authorizationUrl: StateFlow<String?> = _authorizationUrl.asStateFlow()

    private val _backendConfigured = MutableStateFlow<Boolean?>(null)
    val backendConfigured: StateFlow<Boolean?> = _backendConfigured.asStateFlow()

    private val _isRootPickerVisible = MutableStateFlow(false)
    val isRootPickerVisible: StateFlow<Boolean> = _isRootPickerVisible.asStateFlow()

    private val _rootPickerPath = MutableStateFlow("/")
    val rootPickerPath: StateFlow<String> = _rootPickerPath.asStateFlow()
    private val _rootPickerBreadcrumbs = MutableStateFlow(listOf(CloudBreadcrumb("TeraBox", "/")))
    val rootPickerBreadcrumbs: StateFlow<List<CloudBreadcrumb>> = _rootPickerBreadcrumbs.asStateFlow()
    private val _rootPickerFolders = MutableStateFlow<List<CloudFile>>(emptyList())
    val rootPickerFolders: StateFlow<List<CloudFile>> = _rootPickerFolders.asStateFlow()
    private val _rootPickerLoading = MutableStateFlow(false)
    val rootPickerLoading: StateFlow<Boolean> = _rootPickerLoading.asStateFlow()
    private val _rootPickerError = MutableStateFlow<String?>(null)
    val rootPickerError: StateFlow<String?> = _rootPickerError.asStateFlow()
    private val _rootPickerSelection = MutableStateFlow<CloudFile?>(null)
    val rootPickerSelection: StateFlow<CloudFile?> = _rootPickerSelection.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _breadcrumbs = MutableStateFlow(listOf(CloudBreadcrumb("TeraBox", "/")))
    val breadcrumbs: StateFlow<List<CloudBreadcrumb>> = _breadcrumbs.asStateFlow()

    private val _rootPath = MutableStateFlow("/")
    val rootPath: StateFlow<String> = _rootPath.asStateFlow()
    private val _rootName = MutableStateFlow("TeraBox")
    val rootName: StateFlow<String> = _rootName.asStateFlow()

    private val _sortField = MutableStateFlow(SortField.NAME)
    val sortField: StateFlow<SortField> = _sortField.asStateFlow()
    private val _sortDirection = MutableStateFlow(SortDirection.ASCENDING)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()
    private val _mediaTypeFilter = MutableStateFlow<CloudMediaType?>(null)
    val mediaTypeFilter: StateFlow<CloudMediaType?> = _mediaTypeFilter.asStateFlow()

    private val _cachedFolders = MutableStateFlow<List<CloudFile>>(emptyList())
    val cachedFolders: StateFlow<List<CloudFile>> = _cachedFolders.asStateFlow()
    private val _cachedFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    val cachedFiles: StateFlow<List<CloudFile>> = _cachedFiles.asStateFlow()

    val visibleFolders: StateFlow<List<CloudFile>> = _cachedFolders
        .map { list -> list.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleMedia: StateFlow<List<CloudFile>> = combine(
        _cachedFiles,
        _sortField,
        _sortDirection,
        _mediaTypeFilter
    ) { list, field, direction, filter ->
        val filtered = if (filter == null) list else list.filter { it.mediaType == filter }
        sortCloudFiles(filtered, field, direction)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    private val _browseError = MutableStateFlow<String?>(null)
    val browseError: StateFlow<String?> = _browseError.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    private var currentPage = 1

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<CloudFile>>(emptyList())
    val searchResults: StateFlow<List<CloudFile>> = _searchResults.asStateFlow()
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    private val _searchHasMore = MutableStateFlow(false)
    val searchHasMore: StateFlow<Boolean> = _searchHasMore.asStateFlow()
    private var searchPage = 1
    private var searchJob: Job? = null
    private var folderJob: Job? = null
    private var rootPickerJob: Job? = null

    private val _collectionType = MutableStateFlow<CloudCollectionType?>(null)
    private val _collectionTitle = MutableStateFlow("")
    val collectionTitle: StateFlow<String> = _collectionTitle.asStateFlow()

    val favoriteCount: StateFlow<Int> = countFlow { repository.observeFavoriteCount(it) }
    val recentCount: StateFlow<Int> = countFlow { repository.observeRecentCount(it) }
    val totalCount: StateFlow<Int> = countFlow { repository.observeMediaCount(it) }
    val videoCount: StateFlow<Int> = countFlow { repository.observeTypeCount(it, CloudMediaType.VIDEO.name) }
    val photoCount: StateFlow<Int> = countFlow { repository.observeTypeCount(it, CloudMediaType.PHOTO.name) }
    val gifCount: StateFlow<Int> = countFlow { repository.observeTypeCount(it, CloudMediaType.GIF.name) }
    val audioCount: StateFlow<Int> = countFlow { repository.observeTypeCount(it, CloudMediaType.AUDIO.name) }

    val collectionItems: StateFlow<List<CloudFile>> = combine(
        _activeAccountId,
        _collectionType
    ) { accountId, type ->
        accountId to type
    }.flatMapLatest { (accountId, type) ->
        if (accountId == null || type == null) {
            flowOf(emptyList())
        } else {
            when (type) {
                CloudCollectionType.ALL -> repository.observeAllMedia(accountId)
                CloudCollectionType.VIDEOS -> repository.observeFilesByType(accountId, CloudMediaType.VIDEO.name)
                CloudCollectionType.PHOTOS -> repository.observeFilesByType(accountId, CloudMediaType.PHOTO.name)
                CloudCollectionType.GIFS -> repository.observeFilesByType(accountId, CloudMediaType.GIF.name)
                CloudCollectionType.AUDIO -> repository.observeFilesByType(accountId, CloudMediaType.AUDIO.name)
                CloudCollectionType.FAVORITES -> repository.observeFavoriteFiles(accountId)
                CloudCollectionType.RECENT -> repository.observeRecentFiles(accountId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeViewerList = MutableStateFlow<List<CloudFile>>(emptyList())
    val activeViewerList: StateFlow<List<CloudFile>> = _activeViewerList.asStateFlow()
    private val _viewerIndex = MutableStateFlow(0)
    val viewerIndex: StateFlow<Int> = _viewerIndex.asStateFlow()
    private val _viewerFavorite = MutableStateFlow(false)
    val viewerFavorite: StateFlow<Boolean> = _viewerFavorite.asStateFlow()

    private val _downloadState = MutableStateFlow(CloudDownloadState())
    val downloadState: StateFlow<CloudDownloadState> = _downloadState.asStateFlow()

    init {
        viewModelScope.launch {
            _backendConfigured.value = authApi.checkConfiguration()
        }
        viewModelScope.launch {
            refreshFromPreferences()
        }
    }

    private fun countFlow(source: (String) -> kotlinx.coroutines.flow.Flow<Int>): StateFlow<Int> =
        _activeAccountId.flatMapLatest { accountId ->
            if (accountId == null) flowOf(0) else source(accountId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private suspend fun refreshFromPreferences() {
        val accountId = repository.getActiveAccountId()
        if (accountId == null) {
            _activeAccountId.value = null
            _account.value = null
            _sessionState.value = CloudSessionState.SetupRequired
            return
        }
        initializeAccount(accountId)
    }

    private suspend fun initializeAccount(accountId: String) {
        val entity = repository.getAccount(accountId)
        if (entity == null) {
            _sessionState.value = CloudSessionState.SetupRequired
            return
        }
        repository.setActiveAccount(accountId)
        _activeAccountId.value = accountId
        val account = entity.toDomain()
        _account.value = account
        if (!repository.hasStoredToken(accountId)) {
            _sessionState.value = CloudSessionState.ReconnectRequired(
                "The TeraBox token is missing. Reconnect the account."
            )
            return
        }
        _sessionState.value = CloudSessionState.Connected(account)
        val root = repository.getRoot(accountId)
        if (root == null) {
            _rootPath.value = "/"
            _rootName.value = "TeraBox"
            _isRootPickerVisible.value = true
            loadRootPicker("/")
        } else {
            _rootPath.value = root.rootPath
            _rootName.value = root.rootName
            openFolderPath(root.rootPath, replaceCrumbs = true)
        }
    }

    fun startConnect() {
        if (!authApi.isConfigurationPresent()) {
            _setupError.value =
                "Cloud backend is not configured in this build. Set TERABOX_BACKEND_BASE_URL to your deployed HTTPS backend."
            _sessionState.value = CloudSessionState.ConfigError(_setupError.value!!)
            return
        }
        viewModelScope.launch {
            _setupError.value = null
            _sessionState.value = CloudSessionState.ConnectInProgress
            try {
                val start: CloudOAuthStart = repository.beginAuthorization()
                _authorizationUrl.value = start.authorizationUrl
            } catch (e: CloudException) {
                _sessionState.value = CloudSessionState.SetupRequired
                _setupError.value = messageFor(e)
            }
        }
    }

    fun clearAuthorizationUrl() {
        _authorizationUrl.value = null
    }

    fun cancelConnect() {
        viewModelScope.launch {
            repository.clearOAuthState()
            _authorizationUrl.value = null
            val current = _account.value
            _sessionState.value = if (current != null) {
                CloudSessionState.Connected(current)
            } else {
                CloudSessionState.SetupRequired
            }
        }
    }

    fun handleOAuthIntent(uri: Uri) {
        viewModelScope.launch {
            val expected = repository.getOAuthState()
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                // Official Android flow: TeraBox returns ?code=... directly to the
                // registered custom scheme. Exchange it through the backend with
                // the pending signed state generated by /v1/auth/start.
                if (expected == null) {
                    _authorizationUrl.value = null
                    _setupError.value = "No pending TeraBox connection was found. Start the connection from the app."
                    _sessionState.value = CloudSessionState.SetupRequired
                    return@launch
                }
                try {
                    val result = repository.exchangeAuthorization(code, expected)
                    _authorizationUrl.value = null
                    _setupError.value = null
                    initializeAccount(result.account.id)
                } catch (e: CloudException) {
                    repository.clearOAuthState()
                    _authorizationUrl.value = null
                    _setupError.value = messageFor(e)
                    _sessionState.value = CloudSessionState.SetupRequired
                }
                return@launch
            }

            val providerError = uri.getQueryParameter("error")
            if (!providerError.isNullOrBlank()) {
                repository.clearOAuthState()
                _authorizationUrl.value = null
                _setupError.value = "TeraBox authorization was cancelled or rejected."
                _sessionState.value = CloudSessionState.SetupRequired
                return@launch
            }

            val state = uri.getQueryParameter("state").orEmpty()
            if (expected == null || state != expected) {
                repository.clearOAuthState()
                _authorizationUrl.value = null
                _setupError.value = "OAuth verification failed. Please start the connection again."
                _sessionState.value = CloudSessionState.SetupRequired
                return@launch
            }
            val status = uri.getQueryParameter("status")
            if (status != "ok") {
                repository.clearOAuthState()
                _authorizationUrl.value = null
                _setupError.value = "TeraBox authorization was cancelled or rejected."
                _sessionState.value = CloudSessionState.SetupRequired
                return@launch
            }
            val claim = uri.getQueryParameter("claim")
            if (claim.isNullOrBlank()) {
                repository.clearOAuthState()
                _authorizationUrl.value = null
                _setupError.value = "The authorization result is missing. Please reconnect."
                _sessionState.value = CloudSessionState.SetupRequired
                return@launch
            }
            try {
                val result = repository.claimAuthorization(claim)
                repository.clearOAuthState()
                _authorizationUrl.value = null
                _setupError.value = null
                initializeAccount(result.account.id)
            } catch (e: CloudException) {
                repository.clearOAuthState()
                _authorizationUrl.value = null
                _setupError.value = messageFor(e)
                _sessionState.value = CloudSessionState.SetupRequired
            }
        }
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            if (_activeAccountId.value == accountId) return@launch
            _selectedIds.value = emptySet()
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _collectionType.value = null
            _screen.value = CloudScreen.Browse
            screenStack.clear()
            initializeAccount(accountId)
        }
    }

    fun disconnectCurrent() {
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            repository.disconnect(accountId)
            thumbnailLoader.clear()
            _selectedIds.value = emptySet()
            _activeViewerList.value = emptyList()
            _collectionType.value = null
            _screen.value = CloudScreen.Browse
            screenStack.clear()
            refreshFromPreferences()
        }
    }

    fun refreshAccountState() {
        viewModelScope.launch { refreshFromPreferences() }
    }

    fun showRootPicker() {
        _isRootPickerVisible.value = true
        val accountId = _activeAccountId.value
        if (accountId == null) {
            loadRootPicker("/")
            return
        }
        viewModelScope.launch {
            val root = repository.getRoot(accountId)
            val selection = if (root != null && root.rootPath != "/") {
                CloudFile(
                    accountId = accountId,
                    id = root.rootFsId.orEmpty(),
                    name = root.rootName,
                    path = root.rootPath,
                    parentPath = root.rootPath.substringBeforeLast('/', "/").ifBlank { "/" },
                    isDirectory = true,
                    size = 0L,
                    modifiedAt = 0L,
                    category = 6,
                    mediaType = CloudMediaType.OTHER
                )
            } else null
            loadRootPicker(root?.rootPath ?: "/", selection)
        }
    }

    fun hideRootPicker() {
        _isRootPickerVisible.value = false
    }

    fun loadRootPicker(path: String, selection: CloudFile? = null) {
        rootPickerJob?.cancel()
        _rootPickerSelection.value = selection
        rootPickerJob = viewModelScope.launch {
            _rootPickerPath.value = path
            _rootPickerBreadcrumbs.value = pickerBreadcrumbsFor(path)
            _rootPickerLoading.value = true
            _rootPickerError.value = null
            try {
                val page = repository.listFolder(path, page = 1, pageSize = 100, order = "name", descending = false)
                _rootPickerFolders.value = page.items.filter { it.isDirectory }
            } catch (e: CloudException) {
                handleSessionError(e)
                _rootPickerError.value = messageFor(e)
            } finally {
                _rootPickerLoading.value = false
            }
        }
    }

    fun openRootPickerFolder(folder: CloudFile) {
        if (!folder.isDirectory) return
        loadRootPicker(folder.path, folder)
    }

    fun selectCurrentRoot() {
        val selected = _rootPickerSelection.value
        if (selected != null) {
            selectRoot(selected)
            return
        }
        val path = _rootPickerPath.value
        val accountId = _activeAccountId.value
        if (path != "/" && accountId != null) {
            selectRoot(
                CloudFile(
                    accountId = accountId,
                    id = "",
                    name = path.substringAfterLast('/').ifBlank { "Cloud folder" },
                    path = path,
                    parentPath = path.substringBeforeLast('/', "/").ifBlank { "/" },
                    isDirectory = true,
                    size = 0L,
                    modifiedAt = 0L,
                    category = 6,
                    mediaType = CloudMediaType.OTHER
                )
            )
        } else {
            selectRoot(null)
        }
    }

    fun rootPickerBack(): Boolean {
        val crumbs = _rootPickerBreadcrumbs.value
        if (crumbs.size > 1) {
            loadRootPicker(crumbs[crumbs.size - 2].path)
            return true
        }
        return false
    }

    fun selectRoot(folder: CloudFile?) {
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            try {
                repository.setRoot(accountId, folder)
                _rootPath.value = folder?.path ?: "/"
                _rootName.value = folder?.name ?: "TeraBox"
                _isRootPickerVisible.value = false
                _setupError.value = null
                _screen.value = CloudScreen.Browse
                screenStack.clear()
                openFolderPath(_rootPath.value, replaceCrumbs = true)
            } catch (e: CloudException) {
                _setupError.value = messageFor(e)
            }
        }
    }

    fun openFolder(folder: CloudFile) {
        if (!folder.isDirectory) return
        openFolderPath(folder.path, replaceCrumbs = false)
    }

    private fun openFolderPath(path: String, replaceCrumbs: Boolean) {
        _currentPath.value = path
        _selectedIds.value = emptySet()
        _mediaTypeFilter.value = null
        _breadcrumbs.value = if (replaceCrumbs) breadcrumbsFor(path) else {
            val current = _breadcrumbs.value.toMutableList()
            val last = current.lastOrNull()
            if (last?.path != path) {
                current.add(CloudBreadcrumb(path.substringAfterLast('/').ifBlank { "TeraBox" }, path))
            }
            current
        }
        loadCurrentFolder(reset = true)
    }

    fun navigateToBreadcrumb(index: Int) {
        val crumbs = _breadcrumbs.value
        if (index !in crumbs.indices) return
        _currentPath.value = crumbs[index].path
        _breadcrumbs.value = crumbs.take(index + 1)
        _selectedIds.value = emptySet()
        _mediaTypeFilter.value = null
        loadCurrentFolder(reset = true)
    }

    fun loadCurrentFolder(reset: Boolean = true) {
        folderJob?.cancel()
        val path = _currentPath.value
        val accountId = _activeAccountId.value ?: return
        folderJob = viewModelScope.launch {
            if (reset) {
                _isLoading.value = true
                _browseError.value = null
                currentPage = 1
            } else {
                _isLoadingMore.value = true
            }
            try {
                val page: CloudFolderPage = repository.listFolder(
                    path = path,
                    page = if (reset) 1 else currentPage + 1,
                    pageSize = 100,
                    order = if (_sortField.value == SortField.DATE) "time" else "name",
                    descending = _sortDirection.value == SortDirection.DESCENDING
                )
                if (reset) currentPage = 1 else currentPage = page.page
                _hasMore.value = page.hasMore
                _isOffline.value = false
                _browseError.value = null
                repository.saveLastPath(accountId, path)
                updateCachedFolder(accountId, path)
            } catch (e: CloudException) {
                handleSessionError(e)
                if (e.code.isRecoverableOffline()) {
                    _isOffline.value = true
                    _browseError.value = if (_cachedFiles.value.isEmpty() && _cachedFolders.value.isEmpty()) {
                        messageFor(e)
                    } else {
                        null
                    }
                } else if (!e.code.isReconnectRequired()) {
                    _browseError.value = messageFor(e)
                }
                updateCachedFolder(accountId, path)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun updateCachedFolder(accountId: String, path: String) {
        _cachedFolders.value = repository.observeCachedFolders(accountId, path).firstValue()
        _cachedFiles.value = repository.observeCachedFiles(accountId, path).firstValue()
    }

    fun refreshCurrentFolder() {
        _isRefreshing.value = true
        loadCurrentFolder(reset = true)
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        loadCurrentFolder(reset = false)
    }

    fun setSort(field: SortField, direction: SortDirection) {
        _sortField.value = field
        _sortDirection.value = direction
        loadCurrentFolder(reset = true)
    }

    fun setMediaTypeFilter(type: CloudMediaType?) {
        _mediaTypeFilter.value = type
    }

    fun toggleSelection(file: CloudFile) {
        val current = _selectedIds.value.toMutableSet()
        if (!current.add(file.id)) current.remove(file.id)
        _selectedIds.value = current
    }

    fun selectAll(files: List<CloudFile>) {
        _selectedIds.value = files.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectedFiles(): List<CloudFile> =
        _cachedFiles.value.filter { _selectedIds.value.contains(it.id) }

    fun deleteSelected() {
        val files = selectedFiles()
        if (files.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.delete(files)
                _selectedIds.value = emptySet()
                _actionMessage.value = "Deleted ${files.size} cloud item(s)"
                refreshCurrentFolder()
            } catch (e: CloudException) {
                handleSessionError(e)
                _actionMessage.value = messageFor(e)
            }
        }
    }

    fun renameSelected(newName: String) {
        val file = selectedFiles().singleOrNull() ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                repository.rename(file, trimmed)
                _selectedIds.value = emptySet()
                _actionMessage.value = "Renamed cloud item"
                refreshCurrentFolder()
            } catch (e: CloudException) {
                handleSessionError(e)
                _actionMessage.value = messageFor(e)
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _searchLoading.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            performSearch(query, reset = true)
        }
    }

    fun retrySearch() {
        if (_searchQuery.value.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(_searchQuery.value, reset = true) }
    }

    fun loadMoreSearch() {
        if (_searchLoading.value || !_searchHasMore.value) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(_searchQuery.value, reset = false) }
    }

    private suspend fun performSearch(query: String, reset: Boolean) {
        _searchLoading.value = true
        _searchError.value = null
        if (reset) {
            searchPage = 1
        }
        try {
            val page = repository.search(query, page = if (reset) 1 else searchPage + 1, pageSize = 100)
            searchPage = page.page
            _searchResults.value = if (reset) page.items else _searchResults.value + page.items
            _searchHasMore.value = page.hasMore
        } catch (e: CloudException) {
            handleSessionError(e)
            _searchError.value = messageFor(e)
        } finally {
            _searchLoading.value = false
        }
    }

    fun openCollection(type: CloudCollectionType, title: String) {
        _collectionType.value = type
        _collectionTitle.value = title
        setScreen(CloudScreen.CollectionView(title, type))
    }

    fun openViewer(items: List<CloudFile>, index: Int) {
        if (items.isEmpty()) return
        val safeIndex = index.coerceIn(0, items.lastIndex)
        _activeViewerList.value = items
        _viewerIndex.value = safeIndex
        setScreen(CloudScreen.Viewer(safeIndex))
        onViewerPageChanged(safeIndex)
    }

    fun onViewerPageChanged(index: Int) {
        val items = _activeViewerList.value
        if (index !in items.indices) return
        _viewerIndex.value = index
        val file = items[index]
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            repository.recordViewed(accountId, file)
            _viewerFavorite.value = repository.isFavorite(accountId, file)
        }
    }

    fun toggleViewerFavorite() {
        val accountId = _activeAccountId.value ?: return
        val index = _viewerIndex.value
        val file = _activeViewerList.value.getOrNull(index) ?: return
        viewModelScope.launch {
            val favorite = repository.toggleFavorite(accountId, file)
            _viewerFavorite.value = favorite
            _activeViewerList.value = _activeViewerList.value.map {
                if (it.id == file.id) it.copy(isFavorite = favorite) else it
            }
        }
    }

    suspend fun viewerFavoriteState(file: CloudFile): Boolean {
        val accountId = _activeAccountId.value ?: return false
        return repository.isFavorite(accountId, file)
    }

    suspend fun playbackPosition(file: CloudFile): Long {
        val accountId = _activeAccountId.value ?: return 0L
        return repository.getPlaybackPosition(accountId, file)
    }

    fun savePlaybackPosition(file: CloudFile, positionMs: Long) {
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            repository.savePlaybackPosition(accountId, file, positionMs)
        }
    }

    suspend fun resolvePlayback(file: CloudFile): CloudPlaybackSource =
        repository.resolvePlayback(file)

    suspend fun resolveStreamingFallback(file: CloudFile): CloudPlaybackSource =
        repository.resolveStreamingFallback(file)

    suspend fun loadImageBytes(file: CloudFile): ByteArray =
        repository.loadImageBytes(file)

    fun downloadTo(file: CloudFile, destination: Uri) {
        viewModelScope.launch {
            _downloadState.value = CloudDownloadState(
                isDownloading = true,
                fileName = file.name,
                message = "Downloading ${file.name}…",
                isError = false
            )
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val output = resolver.openOutputStream(destination)
                        ?: throw CloudException(CloudErrorCode.UNKNOWN, "Cannot open the selected destination")
                    output.use { repository.downloadTo(file, it) }
                }
                _downloadState.value = CloudDownloadState(
                    isDownloading = false,
                    fileName = file.name,
                    message = "Saved ${file.name}",
                    isError = false
                )
            } catch (e: Exception) {
                val message = if (e is CloudException) messageFor(e) else "Download failed"
                if (e is CloudException) handleSessionError(e)
                _downloadState.value = CloudDownloadState(
                    isDownloading = false,
                    fileName = file.name,
                    message = message,
                    isError = true
                )
            }
        }
    }

    fun clearDownloadState() {
        _downloadState.value = CloudDownloadState()
    }

    fun clearCloudCache() {
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            repository.clearCache(accountId)
            thumbnailLoader.clear()
            _actionMessage.value = "Cloud cache cleared. TeraBox files were not deleted."
            refreshCurrentFolder()
        }
    }

    fun setScreen(screen: CloudScreen) {
        if (_screen.value != screen) {
            screenStack.add(_screen.value)
            _screen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (_isRootPickerVisible.value) {
            return rootPickerBack()
        }
        val current = _screen.value
        if (current is CloudScreen.Viewer || current is CloudScreen.CollectionView || current is CloudScreen.Search) {
            _screen.value = screenStack.removeLastOrNull() ?: CloudScreen.Browse
            return true
        }
        if (current == CloudScreen.Settings && screenStack.isNotEmpty()) {
            _screen.value = screenStack.removeLastOrNull() ?: CloudScreen.Browse
            return true
        }
        val crumbs = _breadcrumbs.value
        if (crumbs.size > 1) {
            navigateToBreadcrumb(crumbs.size - 2)
            return true
        }
        if (current != CloudScreen.Browse) {
            _screen.value = CloudScreen.Browse
            return true
        }
        return false
    }

    fun openSearch() {
        setScreen(CloudScreen.Search)
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()

    private fun handleSessionError(e: CloudException) {
        if (e.code.isReconnectRequired()) {
            _sessionState.value = CloudSessionState.ReconnectRequired(messageFor(e))
        } else if (e.code == CloudErrorCode.NOT_CONFIGURED) {
            _sessionState.value = CloudSessionState.ConfigError(messageFor(e))
        }
    }

    private fun messageFor(e: CloudException): String = when (e.code) {
        CloudErrorCode.NOT_CONFIGURED -> "Cloud Mode is not configured. Deploy the backend and rebuild with TERABOX_BACKEND_BASE_URL."
        CloudErrorCode.NETWORK -> "You appear to be offline. Showing cached cloud metadata where available."
        CloudErrorCode.UNAUTHORIZED, CloudErrorCode.TOKEN_EXPIRED -> "The TeraBox connection has expired. Reconnect your account."
        CloudErrorCode.RATE_LIMITED -> "TeraBox is rate limiting requests. Try again in a moment."
        CloudErrorCode.NOT_FOUND -> "The requested cloud file or folder was not found."
        CloudErrorCode.PERMISSION_DENIED -> "TeraBox did not grant permission for this operation."
        CloudErrorCode.API_ERROR -> e.message ?: "TeraBox rejected the request."
        CloudErrorCode.PARSE_ERROR -> "TeraBox returned an unexpected response."
        CloudErrorCode.UNSUPPORTED -> e.message ?: "This cloud feature is not supported."
        CloudErrorCode.CANCELLED -> "Request cancelled."
        CloudErrorCode.UNKNOWN -> e.message ?: "Cloud request failed."
    }

    private fun breadcrumbsFor(path: String): List<CloudBreadcrumb> {
        val rootPath = _rootPath.value.ifBlank { "/" }
        val result = mutableListOf(CloudBreadcrumb(_rootName.value, rootPath))
        val relative = when {
            rootPath == "/" -> path
            path == rootPath -> ""
            path.startsWith("$rootPath/") -> path.removePrefix(rootPath)
            else -> path
        }
        if (relative.isBlank() || relative == "/") return result
        var accumulated = rootPath.trimEnd('/')
        relative.trim('/').split('/').filter { it.isNotBlank() }.forEach { segment ->
            accumulated += "/$segment"
            result.add(CloudBreadcrumb(segment, accumulated))
        }
        return result
    }

    private fun pickerBreadcrumbsFor(path: String): List<CloudBreadcrumb> {
        val result = mutableListOf(CloudBreadcrumb("TeraBox", "/"))
        if (path.isBlank() || path == "/") return result
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        var accumulated = ""
        segments.forEach { segment ->
            accumulated += "/$segment"
            result.add(CloudBreadcrumb(segment, accumulated))
        }
        return result
    }

    private fun sortCloudFiles(
        list: List<CloudFile>,
        field: SortField,
        direction: SortDirection
    ): List<CloudFile> {
        val comparator = when (field) {
            SortField.NAME -> compareBy<CloudFile> { it.name.lowercase() }
            SortField.DATE -> compareBy { it.modifiedAt }
            SortField.SIZE -> compareBy { it.size }
        }
        return if (direction == SortDirection.ASCENDING) list.sortedWith(comparator)
        else list.sortedWith(comparator.reversed())
    }
}
