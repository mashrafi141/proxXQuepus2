package com.example.cloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.viewmodel.CloudScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.GlassSurfaceHigh
import com.example.ui.theme.TextSecondary

@Composable
fun CloudBottomBar(
    currentScreen: CloudScreen,
    onBrowse: () -> Unit,
    onCollections: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(GlassSurfaceHigh)
                .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(30.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isBrowse = currentScreen is CloudScreen.Browse
            val isCollections = currentScreen is CloudScreen.Collections ||
                currentScreen is CloudScreen.CollectionView
            val isSettings = currentScreen is CloudScreen.Settings
            CloudNavItem(
                title = "Browse",
                icon = if (isBrowse) Icons.Default.Folder else Icons.Outlined.Folder,
                selected = isBrowse,
                onClick = onBrowse
            )
            CloudNavItem(
                title = "Collections",
                icon = if (isCollections) Icons.Default.GridView else Icons.Outlined.GridView,
                selected = isCollections,
                onClick = onCollections
            )
            CloudNavItem(
                title = "Settings",
                icon = if (isSettings) Icons.Default.Settings else Icons.Outlined.Settings,
                selected = isSettings,
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun CloudNavItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0x3300E5FF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) CyanPrimary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        if (selected) {
            Text(
                text = title,
                color = CyanPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
