package com.example.cloud.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.cloud.core.CloudAccount
import com.example.cloud.ui.components.CloudAvatar
import com.example.cloud.ui.components.CloudBottomBar
import com.example.cloud.ui.components.CloudFolderCard
import com.example.cloud.viewmodel.CloudScreen
import com.example.cloud.viewmodel.CloudSessionState
import com.example.cloud.viewmodel.CloudViewModel
import com.example.ui.theme.AccentError
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.AccentWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSetupScreen(
    viewModel: CloudViewModel,
    onSelectLocalMode: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val setupError by viewModel.setupError.collectAsState()
    val authorizationUrl by viewModel.authorizationUrl.collectAsState()
    val backendConfigured by viewModel.backendConfigured.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val isConnecting = sessionState is CloudSessionState.ConnectInProgress

    fun openBrowser(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    LaunchedEffect(authorizationUrl) {
        authorizationUrl?.let { openBrowser(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Mode", color = CyanPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(22.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0x3300E5FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Connect your TeraBox account",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Cloud Mode uses the official TeraBox Open Platform OAuth flow. " +
                            "ProxXQuepus never asks for your TeraBox password and never stores it.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            CloudStatusRow(
                label = "Cloud backend",
                value = when (backendConfigured) {
                    true -> "Reachable"
                    false -> "Not configured or unreachable"
                    null -> "Checking…"
                },
                color = when (backendConfigured) {
                    true -> AccentSuccess
                    false -> AccentWarning
                    null -> TextSecondary
                }
            )

            if (backendConfigured == false) {
                CloudInfoCard(
                    title = "Backend setup required",
                    body = "Cloud Mode requires a deployed FastAPI backend with official TeraBox developer credentials, " +
                        "and the Android build must include that HTTPS backend URL. See docs/RENDER_BACKEND_SETUP.md.",
                    tint = AccentWarning
                )
            }

            if (!setupError.isNullOrBlank()) {
                CloudInfoCard(
                    title = "Cloud setup error",
                    body = setupError!!,
                    tint = AccentError
                )
            }

            if (isConnecting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (authorizationUrl == null) {
                        CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        if (authorizationUrl == null) "Starting secure authorization…"
                        else "Complete the TeraBox login in your browser, then return to the app.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (authorizationUrl != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { openBrowser(authorizationUrl!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Open TeraBox login again", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.cancelConnect() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.startConnect() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect TeraBox Account", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            CloudInfoCard(
                title = "Security",
                body = "Authorization runs on TeraBox's official page. The backend exchanges the one-time authorization code " +
                    "without exposing developer secrets to the APK. The app stores tokens with Android Keystore encryption. " +
                    "Disconnecting removes local tokens and cached metadata only; it never deletes files from your TeraBox account.",
                tint = VioletSecondary
            )

            TextButton(
                onClick = onSelectLocalMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Use Local Mode instead", color = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudReconnectScreen(
    viewModel: CloudViewModel,
    message: String,
    onSelectLocalMode: () -> Unit
) {
    val account by viewModel.account.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var showDisconnect by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Connection", color = CyanPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = AccentWarning,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "TeraBox reconnection required",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = TextSecondary, fontSize = 13.sp)
                    if (account != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(account!!.displayName, color = CyanPrimary, fontSize = 14.sp)
                    }
                }
            }

            Button(
                onClick = { viewModel.startConnect() },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reconnect TeraBox", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            if (accounts.size > 1) {
                TextButton(onClick = { viewModel.switchAccount(accounts.first { it.accountId != account?.id }.accountId) }) {
                    Text("Switch to another connected account", color = CyanPrimary)
                }
            }

            OutlinedButton(
                onClick = { showDisconnect = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = AccentError, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Disconnect account", color = AccentError)
            }

            TextButton(onClick = onSelectLocalMode, modifier = Modifier.fillMaxWidth()) {
                Text("Use Local Mode instead", color = TextSecondary)
            }
        }
    }

    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Disconnect TeraBox account", color = TextPrimary) },
            text = {
                Text(
                    "This removes the encrypted token, selected cloud root and cached cloud metadata from this device. " +
                        "Your TeraBox files are not deleted.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnect = false
                        viewModel.disconnectCurrent()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentError)
                ) {
                    Text("Disconnect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnect = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudRootPickerScreen(
    viewModel: CloudViewModel,
    account: CloudAccount,
    onSelectLocalMode: () -> Unit
) {
    val path by viewModel.rootPickerPath.collectAsState()
    val breadcrumbs by viewModel.rootPickerBreadcrumbs.collectAsState()
    val folders by viewModel.rootPickerFolders.collectAsState()
    val loading by viewModel.rootPickerLoading.collectAsState()
    val error by viewModel.rootPickerError.collectAsState()
    val selection by viewModel.rootPickerSelection.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Choose Cloud Root", color = CyanPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Only this folder and its children will be browsed",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.rootPickerBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.selectRoot(null) }) {
                        Icon(Icons.Default.Cloud, contentDescription = "Use TeraBox root", tint = CyanPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { viewModel.selectCurrentRoot() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            selection != null -> "Use \"${selection!!.name}\" as Cloud Root"
                            path != "/" -> "Use current folder as Cloud Root"
                            else -> "Use TeraBox root"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onSelectLocalMode, modifier = Modifier.fillMaxWidth()) {
                    Text("Use Local Mode instead", color = TextSecondary, fontSize = 12.sp)
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloudAvatar(
                    url = account.avatarUrl,
                    displayName = account.displayName,
                    imageLoader = viewModel.thumbnailLoader.imageLoader,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(account.displayName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { index, crumb ->
                    Text(
                        text = crumb.name,
                        color = if (index == breadcrumbs.lastIndex) CyanPrimary else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.loadRootPicker(crumb.path) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                    if (index < breadcrumbs.lastIndex) {
                        Text("›", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 2.dp))
                    }
                }
            }

            Text(
                text = when {
                    selection != null -> "Current selection: ${selection!!.path}"
                    path != "/" -> "Current folder: $path"
                    else -> "Current selection: TeraBox root (/)"
                },
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )

            if (error != null) {
                CloudInfoCard(
                    title = "Could not load folders",
                    body = error!!,
                    tint = AccentError,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(folders, key = { it.id }) { folder ->
                        CloudFolderCard(
                            folder = folder,
                            thumbnailLoader = viewModel.thumbnailLoader,
                            onClick = { viewModel.openRootPickerFolder(folder) },
                            onLongClick = { viewModel.openRootPickerFolder(folder) }
                        )
                    }
                    if (folders.isEmpty() && !loading && error == null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("No subfolders here", color = TextSecondary, fontSize = 13.sp)
                                TextButton(onClick = { viewModel.loadRootPicker(path) }) {
                                    Text("Retry", color = CyanPrimary)
                                }
                            }
                        }
                    }
                }
                if (loading) {
                    CircularProgressIndicator(
                        color = CyanPrimary,
                        modifier = Modifier.align(Alignment.Center).size(34.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    viewModel: CloudViewModel,
    account: CloudAccount,
    onSelectLocalMode: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val backendConfigured by viewModel.backendConfigured.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Settings", color = CyanPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        bottomBar = {
            CloudBottomBar(
                currentScreen = CloudScreen.Settings,
                onBrowse = { viewModel.setScreen(CloudScreen.Browse) },
                onCollections = { viewModel.setScreen(CloudScreen.Collections) },
                onSettings = {}
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CloudSettingsHeader("STORAGE MODE", Icons.Default.Storage)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StorageModeRow(
                    title = "Local Mode",
                    description = "Browse device storage with the existing local experience.",
                    selected = false,
                    onClick = onSelectLocalMode
                )
                StorageModeRow(
                    title = "Cloud Mode (TeraBox)",
                    description = "Browse the cloud account connected through official TeraBox OAuth.",
                    selected = true,
                    onClick = {}
                )
            }

            CloudSettingsHeader("CONNECTED ACCOUNT", Icons.Default.AccountCircle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CloudAvatar(
                        url = account.avatarUrl,
                        displayName = account.displayName,
                        imageLoader = viewModel.thumbnailLoader.imageLoader,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(account.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("TeraBox account", color = TextSecondary, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                }

                Button(
                    onClick = { viewModel.showRootPicker() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change Cloud Root Folder", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.startConnect() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Another TeraBox Account", color = CyanPrimary, fontSize = 12.sp)
                }

                TextButton(
                    onClick = { showDisconnect = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = AccentError, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Disconnect this account", color = AccentError, fontSize = 12.sp)
                }
            }

            if (accounts.size > 1) {
                CloudSettingsHeader("SWITCH ACCOUNT", Icons.Default.AccountCircle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    accounts.forEach { entity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (entity.accountId != account.id) {
                                        viewModel.switchAccount(entity.accountId)
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CloudAvatar(
                                url = entity.avatarUrl,
                                displayName = entity.displayName,
                                imageLoader = viewModel.thumbnailLoader.imageLoader,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                entity.displayName,
                                color = if (entity.accountId == account.id) CyanPrimary else TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entity.accountId == account.id) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = CyanPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            CloudSettingsHeader("CONNECTION & CACHE", Icons.Default.Cached)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CloudStatusRow(
                    label = "Cloud backend",
                    value = when (backendConfigured) {
                        true -> "Reachable"
                        false -> "Not configured or unreachable"
                        null -> "Checking…"
                    },
                    color = when (backendConfigured) {
                        true -> AccentSuccess
                        false -> AccentWarning
                        null -> TextSecondary
                    }
                )
                Button(
                    onClick = { viewModel.refreshCurrentFolder() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh Cloud Folder", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.clearCloudCache() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Cached, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Cloud Cache", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Clearing the cache removes cloud metadata and thumbnails stored on this device. It never deletes files from TeraBox.",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }

            CloudSettingsHeader("APPLICATION INFO", Icons.Default.Settings)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("ProxXQuepus Cloud", color = CyanPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Version ${BuildConfig.VERSION_NAME} (native release)", color = TextPrimary, fontSize = 13.sp)
                Text(
                    "Connected through the official TeraBox Open Platform. Live cloud operations require TeraBox developer approval " +
                        "and an authorized account. Upload and move-to-folder are not exposed in this release.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }

    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Disconnect TeraBox account", color = TextPrimary) },
            text = {
                Text(
                    "This clears the encrypted token, cloud root selection and cached metadata for this account on this device. " +
                        "Files in TeraBox are not deleted.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnect = false
                        viewModel.disconnectCurrent()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentError)
                ) {
                    Text("Disconnect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnect = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }
}

@Composable
private fun CloudSettingsHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StorageModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0x2200E5FF) else Color.Transparent)
            .border(
                1.dp,
                if (selected) CyanPrimary else DarkSurfaceBorder,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (selected) CyanPrimary else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Icon(
            imageVector = if (selected) Icons.Default.Check else Icons.Default.Cloud,
            contentDescription = null,
            tint = if (selected) CyanPrimary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CloudStatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CloudInfoCard(
    title: String,
    body: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(body, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
