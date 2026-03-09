package com.czsk.app.ui.add

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.czsk.app.model.PlanItem
import com.czsk.app.model.PlanStatus
import com.czsk.app.model.PlanType
import com.czsk.app.model.ReminderMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PageBgColor = Color(0xFFF4EBD8)

private enum class TimeInputMode {
    ABSOLUTE,
    REMAINING
}

private fun formatDate(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMillis))
}

private fun formatTime(timeMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

private fun formatDateTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPlanScreen(
    existingPlan: PlanItem?,
    onBack: () -> Unit,
    onSave: (PlanItem) -> Unit
) {
    val context = LocalContext.current

    var title by remember(existingPlan) { mutableStateOf(existingPlan?.title ?: "") }
    var selectedType by remember(existingPlan) { mutableStateOf(existingPlan?.type ?: PlanType.OTHER) }
    var placeName by remember(existingPlan) { mutableStateOf(existingPlan?.placeName ?: "") }
    var xText by remember(existingPlan) { mutableStateOf(existingPlan?.x?.toString() ?: "") }
    var yText by remember(existingPlan) { mutableStateOf(existingPlan?.y?.toString() ?: "") }
    var note by remember(existingPlan) { mutableStateOf(existingPlan?.note ?: "") }

    var selectedReminderMode by remember(existingPlan) {
        mutableStateOf(existingPlan?.reminderMode ?: ReminderMode.NOTIFICATION)
    }

    val selectedReminderMinutes = remember(existingPlan) {
        mutableStateListOf<Int>().apply {
            val initial = existingPlan?.allReminderLeadMinutes ?: listOf(10)
            addAll(initial.filter { it > 0 }.distinct().sortedDescending())
        }
    }

    var customReminderText by remember(existingPlan) { mutableStateOf("") }

    val initialTime = existingPlan?.scheduledTimeMillis ?: Calendar.getInstance().apply {
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    var scheduledTimeMillis by remember(existingPlan) { mutableLongStateOf(initialTime) }

    var timeInputMode by remember(existingPlan) { mutableStateOf(TimeInputMode.ABSOLUTE) }
    var remainingHoursText by remember(existingPlan) { mutableStateOf("") }
    var remainingMinutesText by remember(existingPlan) { mutableStateOf("") }

    val remainingHours = remainingHoursText.toIntOrNull() ?: 0
    val remainingMinutes = remainingMinutesText.toIntOrNull() ?: 0
    val remainingTotalMinutes = remainingHours * 60 + remainingMinutes

    val resolvedScheduledTimeMillis = if (timeInputMode == TimeInputMode.ABSOLUTE) {
        scheduledTimeMillis
    } else {
        System.currentTimeMillis() + remainingTotalMinutes * 60_000L
    }

    val normalizedReminderMinutes = if (selectedReminderMode == ReminderMode.NONE) {
        emptyList()
    } else {
        selectedReminderMinutes
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
    }

    val primaryReminderLeadMinutes = normalizedReminderMinutes.firstOrNull()
    val extraReminderLeadMinutes = normalizedReminderMinutes.drop(1)

    val canSave = title.isNotBlank() &&
            (timeInputMode == TimeInputMode.ABSOLUTE || remainingTotalMinutes > 0) &&
            (
                    selectedReminderMode == ReminderMode.NONE ||
                            normalizedReminderMinutes.isNotEmpty()
                    )

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledTimeMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = scheduledTimeMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                scheduledTimeMillis = updated.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledTimeMillis }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = scheduledTimeMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                scheduledTimeMillis = updated.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    fun toggleReminderMinutes(minutes: Int) {
        if (selectedReminderMinutes.contains(minutes)) {
            selectedReminderMinutes.remove(minutes)
        } else {
            selectedReminderMinutes.add(minutes)
            val sorted = selectedReminderMinutes.distinct().sortedDescending()
            selectedReminderMinutes.clear()
            selectedReminderMinutes.addAll(sorted)
        }
    }

    fun addCustomReminderMinutes() {
        val custom = customReminderText.toIntOrNull() ?: return
        if (custom <= 0) return
        if (!selectedReminderMinutes.contains(custom)) {
            selectedReminderMinutes.add(custom)
            val sorted = selectedReminderMinutes.distinct().sortedDescending()
            selectedReminderMinutes.clear()
            selectedReminderMinutes.addAll(sorted)
        }
        customReminderText = ""
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
                    text = if (existingPlan == null) "添加计划" else "编辑计划",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBgColor)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SectionCard(title = "基础信息") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "任务类型",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlanType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label) }
                        )
                    }
                }
            }

            SectionCard(title = "地点与坐标") {
                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地点名称（例：郿乌）") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = xText,
                        onValueChange = { xText = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("X 坐标") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = yText,
                        onValueChange = { yText = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Y 坐标") },
                        singleLine = true
                    )
                }
            }

            SectionCard(title = "日期与时间") {
                Text(
                    text = "你可以直接选结束时间，也可以按游戏里的“剩余时间”来录入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = timeInputMode == TimeInputMode.ABSOLUTE,
                        onClick = { timeInputMode = TimeInputMode.ABSOLUTE },
                        label = { Text("直接选时间") }
                    )
                    FilterChip(
                        selected = timeInputMode == TimeInputMode.REMAINING,
                        onClick = { timeInputMode = TimeInputMode.REMAINING },
                        label = { Text("按剩余时间") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (timeInputMode == TimeInputMode.ABSOLUTE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { openDatePicker() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("日期：${formatDate(scheduledTimeMillis)}")
                        }

                        Button(
                            onClick = { openTimePicker() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("时间：${formatTime(scheduledTimeMillis)}")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = remainingHoursText,
                            onValueChange = { remainingHoursText = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text("剩余小时") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = remainingMinutesText,
                            onValueChange = { remainingMinutesText = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text("剩余分钟") },
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (remainingTotalMinutes > 0) {
                            "预计结束时间：${formatDateTime(resolvedScheduledTimeMillis)}"
                        } else {
                            "请填写一个大于 0 的剩余时长"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard(title = "提醒设置") {
                Text(
                    text = "提醒方式",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedReminderMode == mode,
                            onClick = { selectedReminderMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }

                if (selectedReminderMode == ReminderMode.ALARM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "闹钟模式会持续响铃并持续震动，直到你点击通知里的“关闭”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedReminderMode != ReminderMode.NONE) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "提前提醒（可多选）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = selectedReminderMinutes.contains(minutes),
                                onClick = { toggleReminderMinutes(minutes) },
                                label = { Text("提前${minutes}分钟") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customReminderText,
                            onValueChange = { customReminderText = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text("自定义分钟数") },
                            singleLine = true
                        )
                        Button(
                            onClick = { addCustomReminderMinutes() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("加入")
                        }
                    }

                    if (selectedReminderMinutes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "已选提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedReminderMinutes
                                .distinct()
                                .sortedDescending()
                                .forEach { minutes ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { toggleReminderMinutes(minutes) },
                                        label = { Text("${minutes}分钟") }
                                    )
                                }
                        }
                    }
                }
            }

            SectionCard(title = "备注") {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") }
                )
            }

            Button(
                onClick = {
                    onSave(
                        PlanItem(
                            id = existingPlan?.id ?: System.currentTimeMillis(),
                            title = title.trim(),
                            type = selectedType,
                            placeName = placeName.trim(),
                            x = xText.toIntOrNull(),
                            y = yText.toIntOrNull(),
                            scheduledTimeMillis = resolvedScheduledTimeMillis,
                            note = note.trim(),
                            status = existingPlan?.status ?: PlanStatus.PENDING,
                            reminderMode = selectedReminderMode,
                            reminderLeadMinutes = if (selectedReminderMode == ReminderMode.NONE) null else primaryReminderLeadMinutes,
                            extraReminderLeadMinutes = if (selectedReminderMode == ReminderMode.NONE) emptyList() else extraReminderLeadMinutes
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (existingPlan == null) "保存计划" else "保存修改")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}