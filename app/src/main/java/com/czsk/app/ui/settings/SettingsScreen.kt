package com.czsk.app.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

private val PageBgColor = Color(0xFFF4EBD8)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    coordinateVisible: Boolean,
    onCoordinateVisibleChange: (Boolean) -> Unit,
    ringtoneName: String,
    ringtoneUri: String?,
    onRingtoneSelected: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var showAutoStartGuide by remember { mutableStateOf(false) }
    var autoStartJumpMessage by remember { mutableStateOf<String?>(null) }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimization(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    val notificationGranted = remember(refreshKey) { isNotificationPermissionGranted() }
    val exactAlarmGranted = remember(refreshKey) { canScheduleExactAlarms() }
    val batteryOptimizationIgnored = remember(refreshKey) { isIgnoringBatteryOptimization() }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshKey++
        }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            onRingtoneSelected("系统铃声", uri.toString())
        }
        refreshKey++
    }

    val customAudioLauncher = rememberLauncherForActivityResult(
        contract = GetContent()
    ) { uri ->
        if (uri != null) {
            onRingtoneSelected("自定义音频", uri.toString())
        }
        refreshKey++
    }

    Scaffold(
        containerColor = PageBgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PageBgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(" ")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBgColor)
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingCard(
                title = "震动提醒",
                checked = vibrationEnabled,
                onCheckedChange = onVibrationChange
            )

            SettingCard(
                title = "显示坐标",
                checked = coordinateVisible,
                onCheckedChange = onCoordinateVisibleChange
            )

            StatusCard(
                title = "通知权限",
                statusText = if (notificationGranted) "已开启" else "未开启",
                actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "申请权限" else "打开通知设置",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
            )

            StatusCard(
                title = "精确定时权限",
                statusText = if (exactAlarmGranted) "已开启" else "未开启",
                actionText = "打开系统设置",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            StatusCard(
                title = "电池优化豁免",
                statusText = if (batteryOptimizationIgnored) "已忽略优化" else "未忽略优化",
                actionText = "请求忽略优化",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching {
                            context.startActivity(intent)
                        }.onFailure {
                            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(fallback)
                        }
                    }
                }
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "自启动 / 后台管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "用于尽量提高后台被清理后仍能提醒的成功率。如果不打开，清理掉后台之后极大可能不会有消息提醒。",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "会先尝试跳转；如果失败，会提示你手动去系统设置里开启。因为不同手机型号接口不同，很难完全兼容，可能需要手动去设置打开。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val success = openAutoStartSettings(context)
                            autoStartJumpMessage = if (success) {
                                null
                            } else {
                                "跳转失败，请去系统设置里手动打开自启动 / 后台运行。"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("打开相关设置")
                    }

                    if (!autoStartJumpMessage.isNullOrBlank()) {
                        Text(
                            text = autoStartJumpMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8A5A44)
                        )
                    }

                    TextButton(
                        onClick = { showAutoStartGuide = !showAutoStartGuide },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showAutoStartGuide) "收起路径说明" else "查看路径说明")
                    }

                    if (showAutoStartGuide) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF2EAD8)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "常见机型路径参考",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = "OPPO / 一加 / realme：应用 > 自启动",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = "vivo / iQOO：应用与权限 > 权限管理 > 权限 > 自启动\n或 隐私 > 权限管理 > 权限 > 自启动",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = "小米 / Redmi / POCO：应用设置 > 自启动管理",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = "如果找不到，也可以先点“打开应用详情页”，再从系统的应用设置、电池、后台运行、自启动等入口继续找。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            StatusCard(
                title = "应用详情页",
                statusText = "用于查看通知、电池、后台限制等系统设置",
                actionText = "打开应用详情页",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "铃声设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "当前：$ringtoneName",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!ringtoneUri.isNullOrBlank()) {
                        Text(
                            text = "URI：$ringtoneUri",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = ringtoneName == "系统默认通知音",
                            onClick = {
                                onRingtoneSelected(
                                    "系统默认通知音",
                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString()
                                )
                                refreshKey++
                            },
                            label = { Text("系统默认通知音") }
                        )

                        FilterChip(
                            selected = ringtoneName == "系统默认闹钟音",
                            onClick = {
                                onRingtoneSelected(
                                    "系统默认闹钟音",
                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
                                )
                                refreshKey++
                            },
                            label = { Text("系统默认闹钟音") }
                        )
                    }

                    Button(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            }
                            ringtonePickerLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("选择系统铃声")
                    }

                    Button(
                        onClick = {
                            customAudioLauncher.launch("audio/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("选择自定义音频（mp3 / ogg / wav 等）")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "后台提醒说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "建议同时完成：通知权限、精确定时、电池优化、自启动/后台运行。",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "部分机型的“无限制”只能由你在系统设置里手动打开，应用无法直接替你改成那个状态。",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { refreshKey++ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("刷新当前状态")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun openAutoStartSettings(context: Context): Boolean {
    val brand = Build.BRAND.lowercase(Locale.ROOT)
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val pm = context.packageManager

    fun launchIfResolvable(intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    if (
        brand.contains("oppo") ||
        brand.contains("oneplus") ||
        brand.contains("realme") ||
        manufacturer.contains("oppo")
    ) {
        return false
    }

    if (
        brand.contains("xiaomi") ||
        brand.contains("redmi") ||
        brand.contains("poco") ||
        manufacturer.contains("xiaomi")
    ) {
        val miuiIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        if (launchIfResolvable(miuiIntent)) return true
    }

    if (
        brand.contains("vivo") ||
        brand.contains("iqoo") ||
        manufacturer.contains("vivo")
    ) {
        val vivoIntents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            }
        )
        if (vivoIntents.any { launchIfResolvable(it) }) return true
    }

    if (
        brand.contains("huawei") ||
        brand.contains("honor") ||
        manufacturer.contains("huawei") ||
        manufacturer.contains("honor")
    ) {
        val huaweiIntents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
        )
        if (huaweiIntents.any { launchIfResolvable(it) }) return true
    }

    return false
}

@Composable
private fun StatusCard(
    title: String,
    statusText: String,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}