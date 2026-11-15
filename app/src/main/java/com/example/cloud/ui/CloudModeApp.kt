package com.example.cloud.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cloud.core.CloudAccount
import com.example.cloud.ui.screens.CloudBrowseScreen
import com.example.cloud.ui.screens.CloudCollectionViewScreen
import com.example.cloud.ui.screens.CloudCollectionsScreen
import com.example.cloud.ui.screens.CloudReconnectScreen
import com.example.cloud.ui.screens.CloudRootPickerScreen
import com.example.cloud.ui.screens.CloudSearchScreen
import com.example.cloud.ui.screens.CloudSettingsScreen
import com.example.cloud.ui.screens.CloudSetupScreen
import com.example.cloud.ui.screens.CloudViewerScreen
import com.example.cloud.viewmodel.CloudScreen
import com.example.cloud.viewmodel.CloudSessionState
import com.example.cloud.viewmodel.CloudViewModel
import com.example.security.SecurityManager
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun CloudModeApp(
    viewModel: CloudViewModel,
    onSelectLocalMode: () -> Unit,
    onExit: () -> Unit
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val screen by viewModel.screen.collectAsState()
    val rootPickerVisible by viewModel.isRootPickerVisible.collectAsState()
    val context = LocalContext.current
    val securityManager = remember(context) { SecurityManager(context.applicationContext) }
    val lockEnabled by securityManager.isLockEnabled.collectAsState(initial = null)
    var cloudUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(lockEnabled) {
        cloudUnlocked = lockEnabled != true
    }

    BackHandler(enabled = lockEnabled != true || cloudUnlocked) {
        if (!viewModel.navigateBack() && !rootPickerVisible) onExit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when {
            lockEnabled == null -> CloudLoadingScreen()
            lockEnabled == true && !cloudUnlocked -> CloudUnlockScreen(
                securityManager = securityManager,
                onUnlocked = { cloudUnlocked = true }
            )
            else -> when (val session = sessionState) {
                is CloudSessionState.Loading -> CloudLoadingScreen()
                is CloudSessionState.SetupRequired,
                is CloudSessionState.ConnectInProgress,
                is CloudSessionState.ConfigError -> {
                    CloudSetupScreen(
                        viewModel = viewModel,
                        onSelectLocalMode = onSelectLocalMode,
                        onExit = onExit
                    )
                }
                is CloudSessionState.ReconnectRequired -> {
                    CloudReconnectScreen(
                        viewModel = viewModel,
                        message = session.message,
                        onSelectLocalMode = onSelectLocalMode
                    )
                }
                is CloudSessionState.Connected -> {
                    if (rootPickerVisible) {
                        CloudRootPickerScreen(
                            viewModel = viewModel,
                            account = session.account,
                            onSelectLocalMode = onSelectLocalMode
                        )
                    } else {
                        when (val current = screen) {
                            is CloudScreen.Browse -> CloudBrowseScreen(
                                viewModel = viewModel,
                                account = session.account,
                                onSelectLocalMode = onSelectLocalMode
                            )
                            is CloudScreen.Collections -> CloudCollectionsScreen(
                                viewModel = viewModel
                            )
                            is CloudScreen.CollectionView -> CloudCollectionViewScreen(
                                viewModel = viewModel,
                                title = current.title,
                                onBack = { viewModel.navigateBack() }
                            )
                            is CloudScreen.Settings -> CloudSettingsScreen(
                                viewModel = viewModel,
                                account = session.account,
                                onSelectLocalMode = onSelectLocalMode
                            )
                            is CloudScreen.Search -> CloudSearchScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                            is CloudScreen.Viewer -> CloudViewerScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudUnlockScreen(
    securityManager: SecurityManager,
    onUnlocked: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cloud Mode Locked", color = CyanPrimary, fontSize = 20.sp)
            Text(
                "Enter your ProxXQuepus PIN to open Cloud Mode.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    if (value.length <= 6 && value.all { it.isDigit() }) {
                        pin = value
                        error = null
                    }
                },
                label = { Text("PIN") },
                singleLine = true,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Text(error!!, color = Color(0xFFFF5252), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Button(
                onClick = {
                    if (checking || pin.length < 4) return@Button
                    checking = true
                    scope.launch {
                        val valid = securityManager.verifyPin(pin)
                        checking = false
                        if (valid) {
                            onUnlocked()
                        } else {
                            error = "Incorrect PIN"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(46.dp)
            ) {
                if (checking) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                } else {
                    Text("Unlock Cloud Mode", color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun CloudLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(42.dp))
            Text(
                text = "Preparing Cloud Mode…",
                color = TextSecondary,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}
