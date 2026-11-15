package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cloud.ui.CloudModeApp
import com.example.cloud.viewmodel.CloudViewModel
import com.example.core.storage.StorageMode
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.CollectionsScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ProxXQuepusTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()
    private val viewModel: MainViewModel by viewModels()
    private val cloudViewModel: CloudViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)

        setContent {
            ProxXQuepusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val storageMode by appViewModel.storageMode.collectAsState()

                    when (storageMode) {
                        null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DarkBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        StorageMode.CLOUD -> {
                            CloudModeApp(
                                viewModel = cloudViewModel,
                                onSelectLocalMode = { appViewModel.setStorageMode(StorageMode.LOCAL) },
                                onExit = { finish() }
                            )
                        }
                        StorageMode.LOCAL -> {
                            LocalModeApp(
                                viewModel = viewModel,
                                onStorageModeChange = { mode ->
                                    if (mode == StorageMode.CLOUD) {
                                        viewModel.pauseScan()
                                    } else {
                                        viewModel.rootEntity.value?.let { root ->
                                            viewModel.startBackgroundScan(Uri.parse(root.treeUriString))
                                        }
                                    }
                                    appViewModel.setStorageMode(mode)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == BuildConfig.TERABOX_CALLBACK_SCHEME &&
            data.host == BuildConfig.TERABOX_CALLBACK_HOST
        ) {
            if (appViewModel.storageMode.value == StorageMode.LOCAL) {
                viewModel.pauseScan()
            }
            appViewModel.setStorageMode(StorageMode.CLOUD)
            cloudViewModel.handleOAuthIntent(data)
        }
    }

    @Composable
    private fun LocalModeApp(
        viewModel: MainViewModel,
        onStorageModeChange: (StorageMode) -> Unit
    ) {
        val isAppLocked by viewModel.isAppLocked.collectAsState()
        val currentScreen by viewModel.currentScreen.collectAsState()
        val isScanning by viewModel.isScanning.collectAsState()

        LaunchedEffect(Unit) {
            val root = viewModel.rootEntity.value
            if (root != null && !isScanning) {
                viewModel.startBackgroundScan(Uri.parse(root.treeUriString))
            }
        }

        if (isAppLocked) {
            LockScreen(viewModel = viewModel)
        } else {
            BackHandler(enabled = true) {
                val handled = viewModel.navigateBack()
                if (!handled) {
                    finish()
                }
            }

            when (val screen = currentScreen) {
                is Screen.Browse -> {
                    BrowseScreen(viewModel = viewModel)
                }
                is Screen.Collections -> {
                    CollectionsScreen(viewModel = viewModel)
                }
                is Screen.CollectionView -> {
                    CollectionDetailScreen(
                        title = screen.title,
                        type = screen.type,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        storageMode = StorageMode.LOCAL,
                        onStorageModeChange = onStorageModeChange
                    )
                }
                is Screen.Search -> {
                    SearchScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
                is Screen.MediaViewer -> {
                    val activeList by viewModel.activeViewerList.collectAsState()
                    MediaViewerScreen(
                        initialIndex = screen.currentIndex,
                        mediaList = activeList,
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
        }
    }
}
