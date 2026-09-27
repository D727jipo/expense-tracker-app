package com.quickledger.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import com.quickledger.app.data.Prefs
import com.quickledger.app.notify.LedgerNotificationListener
import com.quickledger.app.shizuku.ShizukuHelper

@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    var refreshKey by remember { mutableStateOf(0) }
    var captureMethod by remember {
        mutableIntStateOf(Prefs.captureMethod(context))
    }

    val listenerGranted by produceState(false, refreshKey) {
        value = LedgerNotificationListener.isListenerEnabled(context)
    }
    val postNotificationGranted by produceState(false, refreshKey) {
        value = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val accessibilityEnabled by produceState(false, refreshKey) {
        value = ShizukuHelper.isAccessibilityEnabled(context)
    }
    val batteryIgnored by produceState(false, refreshKey) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        value = pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    val shizukuInstalled by produceState(false, refreshKey) {
        value = ShizukuHelper.installed(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    // 进入时自动弹出通知权限申请
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "欢迎使用快捷记账DB",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "该应用处于 beta 版，可能有未知错误",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Text(
            "选择账单捕获方式（可在设置中更改）：",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        // —— 方式一：通知权限 ——
        MethodCard(
            index = 0,
            selected = captureMethod == 0,
            title = "通知权限",
            badge = "精度最低",
            badgeColor = Color(0xFFB08968),
            description = "读取微信、支付宝的支付通知，解析金额与备注。无需 root，最简单但依赖通知内容。",
            granted = listenerGranted,
            grantedText = "已开启",
            onClick = {
                captureMethod = Prefs.CAPTURE_NOTIFICATION
                Prefs.setCaptureMethod(context, Prefs.CAPTURE_NOTIFICATION)
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )
        Spacer(Modifier.height(10.dp))

        // —— 方式二：无障碍 + Shizuku ——
        MethodCard(
            index = 1,
            selected = captureMethod == 1,
            title = "无障碍 + Shizuku 权限",
            badge = "推荐",
            badgeColor = Color(0xFF2E9E5B),
            description = "无障碍直接读取微信支付结果页面的数据，比通知更完整；Shizuku 可一键自动开启无障碍。",
            granted = accessibilityEnabled,
            grantedText = "已开启",
            onClick = {
                captureMethod = Prefs.CAPTURE_ACCESSIBILITY
                Prefs.setCaptureMethod(context, Prefs.CAPTURE_ACCESSIBILITY)
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        ) {
            if (shizukuInstalled) {
                OutlinedButton(
                    onClick = {
                        if (ShizukuHelper.granted()) {
                            val ok = ShizukuHelper.enableAccessibilityViaShizuku(context)
                            refreshKey++
                        } else {
                            ShizukuHelper.requestPermission { refreshKey++ }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("用 Shizuku 自动开启无障碍") }
            } else {
                Text(
                    "安装 Shizuku 后可一键自动开启（可选）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // —— 方式三：LSPosed 模块 ——
        MethodCard(
            index = 2,
            selected = captureMethod == 2,
            title = "LSPosed 模块",
            badge = "精度最高 · 不推荐",
            badgeColor = Color(0xFFE0524F),
            description = "直接 Hook 微信支付数据，精度最高，但需要 root + LSPosed 环境。开启方法：在 LSPosed 管理器中启用「快捷记账DB」模块并勾选微信，重启微信即可。",
            granted = captureMethod == Prefs.CAPTURE_LSPOSED,
            grantedText = "已选择",
            onClick = {
                captureMethod = Prefs.CAPTURE_LSPOSED
                Prefs.setCaptureMethod(context, Prefs.CAPTURE_LSPOSED)
            },
        )

        Text(
            "通用权限：",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )

        PermissionCard(
            icon = { Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "发送通知（必需）",
            description = "自动保存成功后提示你，或在关闭自动保存时弹出「保存 / 忽略」确认通知。",
            granted = postNotificationGranted,
            onGrant = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        PermissionCard(
            icon = { Icon(Icons.Filled.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = "留存后台（必需）",
            description = "申请电池优化豁免，避免捕获服务被系统清理导致漏记账单。",
            granted = batteryIgnored,
            onGrant = {
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

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("开始使用")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MethodCard(
    index: Int,
    selected: Boolean,
    title: String,
    badge: String,
    badgeColor: Color,
    description: String,
    granted: Boolean,
    grantedText: String,
    onClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
) {
    val borderColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary
            granted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        },
        label = "methodBorder",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp),
            )
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = badgeColor.copy(alpha = 0.15f)) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (granted) grantedText else "未开启",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        extraContent?.invoke()
    }
}

@Composable
private fun PermissionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.error,
        label = "border",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onGrant)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (granted) "已授予" else "未授予",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
