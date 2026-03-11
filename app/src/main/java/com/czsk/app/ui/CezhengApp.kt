package com.czsk.app.ui

import android.media.RingtoneManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.czsk.app.model.PlanItem
import com.czsk.app.model.PlanStatus
import com.czsk.app.model.PlanType
import com.czsk.app.reminder.ReminderScheduler
import com.czsk.app.storage.AppStorage
import com.czsk.app.ui.add.AddPlanScreen
import com.czsk.app.ui.battle.KaMiaoBattleScreen // 修改：导入正确的卡秒出征组件
import com.czsk.app.ui.home.HomeScreen
import com.czsk.app.ui.importer.ImportPreviewItem
import com.czsk.app.ui.importer.ImportScreen
import com.czsk.app.ui.importer.ImportStage
import com.czsk.app.ui.importer.parseImportLines
import com.czsk.app.ui.importer.rebuildImportPreviewItemFromPlan
import com.czsk.app.ui.map.MapCoordinateScreen
import com.czsk.app.ui.more.MoreScreen
import com.czsk.app.ui.settings.SettingsScreen

// 枚举定义
private enum class AppScreen {
    HOME,
    ADD,
    IMPORT,
    SETTINGS,
    MAP,
    BATTLE,
    MORE
}

// 构建默认计划（保持不变）
private fun buildDefaultPlans(): List<PlanItem> {
    return listOf(
        PlanItem(
            id = 1L,
            title = "主队出征",
            type = PlanType.SIEGE,
            placeName = "郿乌",
            x = 352,
            y = 328,
            scheduledTimeMillis = System.currentTimeMillis() + 42 * 60 * 1000L,
            note = "主力队提前 5 分钟集结",
            reminderMode = com.czsk.app.model.ReminderMode.RING,
            reminderLeadMinutes = 10
        ),
        PlanItem(
            id = 2L,
            title = "分城征兵提醒",
            type = PlanType.RECRUIT,
            placeName = "分城",
            x = 256,
            y = 32,
            scheduledTimeMillis = System.currentTimeMillis() + 82 * 60 * 1000L,
            note = "征兵后检查预备兵",
            reminderMode = com.czsk.app.model.ReminderMode.NOTIFICATION,
            reminderLeadMinutes = 5
        ),
        PlanItem(
            id = 3L,
            title = "拆迁赶路",
            type = PlanType.MARCH,
            placeName = "河道口",
            x = 188,
            y = 417,
            scheduledTimeMillis = System.currentTimeMillis() + 132 * 60 * 1000L,
            note = "拆迁队和主力错峰到达",
            reminderMode = com.czsk.app.model.ReminderMode.NOTIFICATION,
            reminderLeadMinutes = 15
        ),
        PlanItem(
            id = 4L,
            title = "要塞修建完成检查",
            type = PlanType.BUILD,
            placeName = "西侧前线",
            x = 401,
            y = 220,
            scheduledTimeMillis = System.currentTimeMillis() + 600 * 60 * 1000L,
            note = "确认要塞队列和耐久",
            status = PlanStatus.DONE,
            reminderMode = com.czsk.app.model.ReminderMode.NONE,
            reminderLeadMinutes = null
        ),
        PlanItem(
            id = 5L,
            title = "体力恢复提醒",
            type = PlanType.STAMINA,
            placeName = "主城",
            x = null,
            y = null,
            scheduledTimeMillis = System.currentTimeMillis() + 190 * 60 * 1000L,
            note = "准备后续连续铺路",
            reminderMode = com.czsk.app.model.ReminderMode.NOTIFICATION,
            reminderLeadMinutes = 30
        ),
        PlanItem(
            id = 6L,
            title = "同盟事项补记",
            type = PlanType.OTHER,
            placeName = "议事厅",
            x = null,
            y = null,
            scheduledTimeMillis = System.currentTimeMillis() + 250 * 60 * 1000L,
            note = "记录明早接力安排",
            reminderMode = com.czsk.app.model.ReminderMode.NONE,
            reminderLeadMinutes = null
        )
    )
}

@Composable
fun CezhengApp() {
    val context = LocalContext.current

    val persistedPlans = remember {
        val loaded = AppStorage.loadPlans(context)
        if (loaded.isEmpty()) {
            val defaults = buildDefaultPlans()
            AppStorage.savePlans(context, defaults)
            defaults
        } else {
            loaded
        }
    }

    val plans = remember {
        mutableStateListOf<PlanItem>().apply {
            addAll(persistedPlans)
        }
    }

    val currentScreen = remember { androidx.compose.runtime.mutableStateOf(AppScreen.HOME) }
    val editingPlan = remember { androidx.compose.runtime.mutableStateOf<PlanItem?>(null) }

    val importStage = remember { androidx.compose.runtime.mutableStateOf(ImportStage.INPUT) }
    val importInputText = remember { androidx.compose.runtime.mutableStateOf("") }
    val importPreviewItems = remember { mutableStateListOf<ImportPreviewItem>() }
    val importEditingLineNumber = remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }

    val vibrationEnabled = remember {
        androidx.compose.runtime.mutableStateOf(AppStorage.loadVibrationEnabled(context))
    }
    val coordinateVisible = remember {
        androidx.compose.runtime.mutableStateOf(AppStorage.loadCoordinateVisible(context))
    }
    val ringtoneName = remember {
        androidx.compose.runtime.mutableStateOf(
            AppStorage.loadRingtoneName(context)
        )
    }
    val ringtoneUri = remember {
        androidx.compose.runtime.mutableStateOf(
            AppStorage.loadRingtoneUri(context)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString()
        )
    }

    BackHandler(enabled = currentScreen.value != AppScreen.HOME) {
        editingPlan.value = null
        importEditingLineNumber.value = null
        currentScreen.value = AppScreen.HOME
    }

    LaunchedEffect(Unit) {
        plans.forEach { plan ->
            if (plan.status == PlanStatus.PENDING) {
                ReminderScheduler.scheduleReminder(context, plan)
            } else {
                ReminderScheduler.cancelReminder(context, plan.id)
            }
        }
    }

    when (currentScreen.value) {
        AppScreen.HOME -> {
            HomeScreen(
                plans = plans,
                coordinateVisible = coordinateVisible.value,
                onNavigateAdd = {
                    editingPlan.value = null
                    importEditingLineNumber.value = null
                    currentScreen.value = AppScreen.ADD
                },
                onNavigateEdit = { item ->
                    editingPlan.value = item
                    importEditingLineNumber.value = null
                    currentScreen.value = AppScreen.ADD
                },
                onNavigateImport = { currentScreen.value = AppScreen.IMPORT },
                onNavigateSettings = { currentScreen.value = AppScreen.SETTINGS },
                onNavigateMap = { currentScreen.value = AppScreen.MAP },
                onNavigateBattle = { currentScreen.value = AppScreen.BATTLE },
                onNavigateMore = { currentScreen.value = AppScreen.MORE },
                onToggleDone = { item ->
                    val index = plans.indexOfFirst { it.id == item.id }
                    if (index != -1) {
                        val old = plans[index]
                        val updated = old.copy(
                            status = if (old.status == PlanStatus.DONE) {
                                PlanStatus.PENDING
                            } else {
                                PlanStatus.DONE
                            }
                        )
                        plans[index] = updated

                        AppStorage.savePlans(context, plans)

                        if (updated.status == PlanStatus.PENDING) {
                            ReminderScheduler.scheduleReminder(context, updated)
                        } else {
                            ReminderScheduler.cancelReminder(context, updated.id)
                        }
                    }
                },
                onDelete = { item ->
                    ReminderScheduler.cancelReminder(context, item.id)
                    plans.removeAll { it.id == item.id }
                    AppStorage.savePlans(context, plans)
                }
            )
        }

        AppScreen.ADD -> {
            AddPlanScreen(
                existingPlan = editingPlan.value,
                onBack = {
                    editingPlan.value = null
                    if (importEditingLineNumber.value != null) {
                        currentScreen.value = AppScreen.IMPORT
                    } else {
                        currentScreen.value = AppScreen.HOME
                    }
                },
                onSave = { plan ->
                    val editingImportLineNumber = importEditingLineNumber.value

                    if (editingImportLineNumber != null) {
                        val previewIndex = importPreviewItems.indexOfFirst {
                            it.lineNumber == editingImportLineNumber
                        }
                        if (previewIndex != -1) {
                            importPreviewItems[previewIndex] =
                                rebuildImportPreviewItemFromPlan(plan, editingImportLineNumber)
                        }
                        editingPlan.value = null
                        importEditingLineNumber.value = null
                        importStage.value = ImportStage.PREVIEW
                        currentScreen.value = AppScreen.IMPORT
                    } else {
                        val index = plans.indexOfFirst { it.id == plan.id }
                        if (index != -1) {
                            ReminderScheduler.cancelReminder(context, plan.id)
                            plans[index] = plan
                        } else {
                            plans.add(0, plan)
                        }

                        AppStorage.savePlans(context, plans)

                        if (plan.status == PlanStatus.PENDING) {
                            ReminderScheduler.scheduleReminder(context, plan)
                        } else {
                            ReminderScheduler.cancelReminder(context, plan.id)
                        }

                        editingPlan.value = null
                        currentScreen.value = AppScreen.HOME
                    }
                }
            )
        }

        AppScreen.IMPORT -> {
            ImportScreen(
                stage = importStage.value,
                inputText = importInputText.value,
                previewItems = importPreviewItems,
                onBack = { currentScreen.value = AppScreen.HOME },
                onGoInput = { importStage.value = ImportStage.INPUT },
                onInputTextChange = { importInputText.value = it },
                onParseRequested = {
                    importPreviewItems.clear()
                    importPreviewItems.addAll(parseImportLines(importInputText.value))
                    importStage.value = ImportStage.PREVIEW
                },
                onUpdatePreviewItemRaw = { index, rawLine ->
                    if (index in importPreviewItems.indices) {
                        val originalLineNumber = importPreviewItems[index].lineNumber
                        importPreviewItems[index] = parseImportLines(rawLine).first().copy(
                            lineNumber = originalLineNumber
                        )
                    }
                },
                onDeletePreviewItem = { index ->
                    if (index in importPreviewItems.indices) {
                        importPreviewItems.removeAt(index)
                    }
                },
                onEditSuccessItem = { item ->
                    editingPlan.value = item.plan
                    importEditingLineNumber.value = item.lineNumber
                    currentScreen.value = AppScreen.ADD
                },
                onImportConfirmed = {
                    val importedPlans = importPreviewItems.mapNotNull { it.plan }

                    if (importedPlans.isNotEmpty()) {
                        val maxExistingId = plans.maxOfOrNull { it.id } ?: 0L

                        val normalizedPlans = importedPlans.mapIndexed { index, plan ->
                            plan.copy(id = maxExistingId + index + 1)
                        }

                        plans.addAll(0, normalizedPlans)
                        AppStorage.savePlans(context, plans)

                        normalizedPlans.forEach { plan ->
                            if (plan.status == PlanStatus.PENDING) {
                                ReminderScheduler.scheduleReminder(context, plan)
                            } else {
                                ReminderScheduler.cancelReminder(context, plan.id)
                            }
                        }

                        importInputText.value = ""
                        importPreviewItems.clear()
                        importStage.value = ImportStage.INPUT
                    }

                    currentScreen.value = AppScreen.HOME
                }
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                onBack = { currentScreen.value = AppScreen.HOME },
                vibrationEnabled = vibrationEnabled.value,
                onVibrationChange = {
                    vibrationEnabled.value = it
                    AppStorage.saveVibrationEnabled(context, it)
                },
                coordinateVisible = coordinateVisible.value,
                onCoordinateVisibleChange = {
                    coordinateVisible.value = it
                    AppStorage.saveCoordinateVisible(context, it)
                },
                ringtoneName = ringtoneName.value,
                ringtoneUri = ringtoneUri.value,
                onRingtoneSelected = { name, uri ->
                    ringtoneName.value = name
                    ringtoneUri.value = uri
                    com.czsk.app.reminder.ReminderPrefs.saveRingtone(context, name, uri)
                    AppStorage.saveRingtone(context, name, uri)
                }
            )
        }

        AppScreen.MAP -> {
            MapCoordinateScreen(
                onBack = { currentScreen.value = AppScreen.HOME }
            )
        }

        AppScreen.BATTLE -> {
            KaMiaoBattleScreen( // 使用正确的组件名
                onBack = { currentScreen.value = AppScreen.HOME }
            )
        }

        AppScreen.MORE -> {
            MoreScreen(
                onBack = { currentScreen.value = AppScreen.HOME }
            )
        }
    }
}