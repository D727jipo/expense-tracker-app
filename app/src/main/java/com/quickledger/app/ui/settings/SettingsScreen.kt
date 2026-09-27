package com.quickledger.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quickledger.app.LedgerApp
import com.quickledger.app.ThemePrefs
import com.quickledger.app.data.Prefs
import com.quickledger.app.notify.LedgerNotificationListener
import com.quickledger.app.shizuku.ShizukuHelper

@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity

    var themeMode by remember {
        mutableStateOf(LedgerApp.prefs(context).getInt(LedgerApp.KEY_THEME, ThemePrefs.MODE_SYSTEM))
    }

    // 权限状态（进入或从系统设置返回时刷新）
    var refreshKey by remember { mutableStateOf(0) }
    val listenerGranted by produceState(false, refreshKey) {
        value = LedgerNotificationListener.isListenerEnabled(context)
    }
    val postNotificationGranted by produceState(true, refreshKey) {
        value = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val allGranted = listenerGranted && postNotificationGranted

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    val batteryIgnored by produceState(false, refreshKey) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        value = pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    val accessibilityEnabled by produceState(false, refreshKey) {
        value = ShizukuHelper.isAccessibilityEnabled(context)
    }
    val shizukuInstalled by produceState(false, refreshKey) {
        value = ShizukuHelper.installed(context)
    }

    var captureMethod by remember { mutableStateOf(Prefs.captureMethod(context)) }
    var autoSave by remember { mutableStateOf(Prefs.autoSave(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // —— 权限状态 ——
        Text(
            "权限",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        AnimatedVisibility(visible = !allGranted) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable {
                        if (!listenerGranted) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } else if (android.os.Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Column {
                        Text(
                            "权限未授予",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            listOfNotNull(
                                "通知使用权".takeIf { !listenerGranted },
                                "发送通知".takeIf { !postNotificationGranted },
                            ).joinToString("、") + " 未开启，点击去授权",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        PermissionRow(
            title = "通知使用权",
            description = "读取微信、支付宝的支付通知，用于自动捕获账单金额和备注",
            granted = listenerGranted,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )
        PermissionRow(
            title = "发送通知",
            description = "自动保存提示或“保存 / 忽略”确认通知",
            granted = postNotificationGranted,
            onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        PermissionRow(
            title = "留存后台",
            description = "电池优化豁免，避免捕获服务被系统清理导致漏记账单",
            granted = batteryIgnored,
            onClick = {
                try {
                    batteryLauncher.launch(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
        )

        // —— 捕获方式 ——
        Text(
            "捕获方式",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptureMethodOption(
                    title = "通知权限",
                    badge = "精度最低",
                    badgeColor = Color(0xFFB08968),
                    selected = captureMethod == Prefs.CAPTURE_NOTIFICATION,
                    status = if (listenerGranted) "已开启" else "未开启",
                    statusColor = if (listenerGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    onClick = {
                        captureMethod = Prefs.CAPTURE_NOTIFICATION
                        Prefs.setCaptureMethod(context, Prefs.CAPTURE_NOTIFICATION)
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
                CaptureMethodOption(
                    title = "无障碍 + Shizuku 权限",
                    badge = "推荐",
                    badgeColor = Color(0xFF2E9E5B),
                    selected = captureMethod == Prefs.CAPTURE_ACCESSIBILITY,
                    status = if (accessibilityEnabled) "已开启" else "未开启",
                    statusColor = if (accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    onClick = {
                        captureMethod = Prefs.CAPTURE_ACCESSIBILITY
                        Prefs.setCaptureMethod(context, Prefs.CAPTURE_ACCESSIBILITY)
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                ) {
                    if (shizukuInstalled) {
                        Text(
                            "点此选择后可用 Shizuku 自动开启无障碍",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CaptureMethodOption(
                    title = "LSPosed 模块",
                    badge = "精度最高 · 不推荐",
                    badgeColor = Color(0xFFE0524F),
                    selected = captureMethod == Prefs.CAPTURE_LSPOSED,
                    status = if (captureMethod == Prefs.CAPTURE_LSPOSED) "已选择" else "未选择",
                    statusColor = if (captureMethod == Prefs.CAPTURE_LSPOSED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        captureMethod = Prefs.CAPTURE_LSPOSED
                        Prefs.setCaptureMethod(context, Prefs.CAPTURE_LSPOSED)
                    },
                ) {
                    Text(
                        "需要 root：在 LSPosed 中启用本模块并勾选微信，重启微信",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // —— 记账行为 ——
        Text(
            "记账行为",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动保存", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "读取到支付后直接记账并发提示；关闭后改为弹窗询问是否保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoSave,
                    onCheckedChange = {
                        autoSave = it
                        Prefs.setAutoSave(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        // —— 外观 ——
        Text(
            "外观",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("界面背景", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemePrefs.MODE_SYSTEM to "跟随系统",
                        ThemePrefs.MODE_LIGHT to "浅色",
                        ThemePrefs.MODE_DARK to "深色",
                    ).forEach { (mode, label) ->
                        val selected = themeMode == mode
                        val bg by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            label = "themeBg",
                        )
                        val fg by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            label = "themeFg",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(bg, RoundedCornerShape(12.dp))
                                .clickable {
                                    themeMode = mode
                                    LedgerApp.prefs(context).edit()
                                        .putInt(LedgerApp.KEY_THEME, mode)
                                        .apply()
                                    onThemeChanged()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = fg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Text(
            "快捷记账DB · 本地记账，不上传任何数据\n支付信息通过系统通知读取，仅保存在本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun CaptureMethodOption(
    title: String,
    badge: String,
    badgeColor: Color,
    selected: Boolean,
    status: String,
    statusColor: Color,
    onClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(8.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.15f)) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(status, style = MaterialTheme.typography.labelLarge, color = statusColor)
        }
        extraContent?.invoke()
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .animateContentSize(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (granted) null else BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        (if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.15f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (granted) Icons.Filled.Check else Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (granted) "已开启" else "未开启",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
