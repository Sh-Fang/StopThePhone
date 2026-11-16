package com.stopthephone.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.SettingsStore
import com.stopthephone.system.Permissions
import com.stopthephone.ui.common.GroupCard
import com.stopthephone.ui.common.GroupDivider
import com.stopthephone.ui.common.GroupRow
import com.stopthephone.ui.common.SectionLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val defaultReleaseMinutes: StateFlow<Int?> = settingsStore.defaultReleaseMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun changeDefaultReleaseMinutes(delta: Int) {
        val current = defaultReleaseMinutes.value ?: return
        val next = (current + delta).coerceIn(SettingsStore.MIN_RELEASE_MINUTES, SettingsStore.MAX_RELEASE_MINUTES)
        viewModelScope.launch { settingsStore.setDefaultReleaseMinutes(next) }
    }
}

private class PermissionItem(val name: String, val granted: Boolean?, val onOpen: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val minutes by viewModel.defaultReleaseMinutes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 从系统设置页回来时刷新权限状态
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }
    var notificationAsked by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshKey++ }
    val permissions = remember(refreshKey) {
        permissionItems(context, onRequestNotification = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked) {
                notificationAsked = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startSafely(Permissions.notificationSettings(context))
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val value = minutes ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                GroupCard(Modifier.padding(top = 8.dp)) {
                    GroupRow(
                        title = "默认放行时长",
                        trailing = {
                            FilledTonalIconButton(
                                onClick = { viewModel.changeDefaultReleaseMinutes(-SettingsStore.RELEASE_MINUTES_STEP) },
                                enabled = value > SettingsStore.MIN_RELEASE_MINUTES,
                                modifier = Modifier.size(36.dp),
                            ) { Icon(Icons.Default.Remove, contentDescription = "减少") }
                            Text(
                                "$value 分钟",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 64.dp),
                            )
                            FilledTonalIconButton(
                                onClick = { viewModel.changeDefaultReleaseMinutes(SettingsStore.RELEASE_MINUTES_STEP) },
                                enabled = value < SettingsStore.MAX_RELEASE_MINUTES,
                                modifier = Modifier.size(36.dp),
                            ) { Icon(Icons.Default.Add, contentDescription = "增加") }
                        },
                    )
                }
            }
            item { SectionLabel("权限") }
            item {
                GroupCard {
                    permissions.forEachIndexed { index, permission ->
                        if (index > 0) {
                            GroupDivider()
                        }
                        GroupRow(
                            title = permission.name,
                            onClick = if (permission.granted == true) null else permission.onOpen,
                            trailing = {
                                if (permission.granted == true) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "已开启", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text("去开启", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun permissionItems(context: Context, onRequestNotification: () -> Unit): List<PermissionItem> {
    val items = mutableListOf(
        PermissionItem("管控服务", Permissions.isAccessibilityEnabled(context)) { context.startSafely(Permissions.accessibilitySettings()) },
        PermissionItem("使用情况访问", Permissions.hasUsageAccess(context)) { context.startSafely(Permissions.usageAccessSettings(context)) },
        PermissionItem("通知", Permissions.areNotificationsEnabled(context), onRequestNotification),
        PermissionItem("不限制后台耗电", Permissions.isIgnoringBatteryOptimizations(context)) {
            context.startSafely(Permissions.batteryOptimizationRequest(context))
        },
    )
    if (Permissions.isXiaomi()) {
        items.add(PermissionItem("自启动", Permissions.isXiaomiAutoStartAllowed(context)) { context.startSafely(Permissions.xiaomiAutoStartSettings()) })
    }
    return items
}

/**
 * 打开系统设置页，ROM 不支持该入口时退回到本应用的应用信息页。
 */
fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure { startActivity(Permissions.appDetailsSettings(this)) }
}
