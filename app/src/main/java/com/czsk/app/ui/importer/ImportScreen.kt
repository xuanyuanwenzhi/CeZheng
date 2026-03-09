package com.czsk.app.ui.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

private val PageBgColor = Color(0xFFF4EBD8)

enum class ImportStage {
    INPUT,
    PREVIEW
}

data class ParsedLocation(
    val placeName: String,
    val x: Int?,
    val y: Int?
)

data class ImportErrorDetail(
    val fieldName: String,
    val reason: String,
    val examples: List<String>
)

data class ImportPreviewItem(
    val rawLine: String,
    val lineNumber: Int,
    val success: Boolean,
    val plan: PlanItem?,
    val errorMessage: String?,
    val errorDetail: ImportErrorDetail? = null
)

private class ImportParseException(
    val detail: ImportErrorDetail
) : IllegalArgumentException(detail.reason)

private data class ParsedDate(
    val year: Int,
    val month: Int,
    val day: Int
)

private data class ParsedTime(
    val hour: Int,
    val minute: Int
)

private data class ParsedLeadMinutes(
    val primary: Int?,
    val extra: List<Int>
)

fun parseImportLines(rawText: String): List<ImportPreviewItem> {
    return rawText
        .lines()
        .mapIndexedNotNull { index, line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) null else parseSingleLine(trimmed, index + 1)
        }
}

fun rebuildImportPreviewItemFromPlan(
    plan: PlanItem,
    lineNumber: Int
): ImportPreviewItem {
    return ImportPreviewItem(
        rawLine = buildImportLineFromPlan(plan),
        lineNumber = lineNumber,
        success = true,
        plan = plan,
        errorMessage = null,
        errorDetail = null
    )
}

private fun buildImportLineFromPlan(plan: PlanItem): String {
    val location = when {
        plan.placeName.isNotBlank() && plan.x != null && plan.y != null ->
            "${plan.placeName}(${plan.x},${plan.y})"
        plan.placeName.isNotBlank() -> plan.placeName
        plan.x != null && plan.y != null -> "(${plan.x},${plan.y})"
        else -> ""
    }

    val lead = plan.allReminderLeadMinutes.joinToString("、")
    val note = plan.note.trim()

    return listOf(
        plan.title.trim(),
        plan.type.label,
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(plan.scheduledTimeMillis)),
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(plan.scheduledTimeMillis)),
        location,
        plan.reminderMode.label,
        lead,
        note
    ).joinToString("|")
}

@Composable
fun ImportScreen(
    stage: ImportStage,
    inputText: String,
    previewItems: List<ImportPreviewItem>,
    onBack: () -> Unit,
    onGoInput: () -> Unit,
    onInputTextChange: (String) -> Unit,
    onParseRequested: () -> Unit,
    onUpdatePreviewItemRaw: (index: Int, rawLine: String) -> Unit,
    onDeletePreviewItem: (index: Int) -> Unit,
    onEditSuccessItem: (ImportPreviewItem) -> Unit,
    onImportConfirmed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingRawLine by remember { mutableStateOf("") }

    val successCount = previewItems.count { it.success && it.plan != null }
    val failCount = previewItems.count { !it.success }

    if (editingIndex >= 0 && editingIndex < previewItems.size) {
        AlertDialog(
            onDismissRequest = { editingIndex = -1 },
            title = { Text("编辑第 ${previewItems[editingIndex].lineNumber} 行") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "修改这一行后会重新解析。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "格式：标题|类型|日期|时间|地点描述|提醒方式|提前分钟|备注，也兼容用 / 分隔。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editingRawLine,
                        onValueChange = { editingRawLine = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("原始文本") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editingRawLine.trim()
                        if (trimmed.isBlank()) {
                            onDeletePreviewItem(editingIndex)
                        } else {
                            onUpdatePreviewItemRaw(editingIndex, trimmed)
                        }
                        editingIndex = -1
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = -1 }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        containerColor = PageBgColor,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PageBgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        if (stage == ImportStage.PREVIEW) {
                            onGoInput()
                        } else {
                            onBack()
                        }
                    }
                ) {
                    Text(if (stage == ImportStage.PREVIEW) "返回输入" else "返回")
                }

                Text(
                    text = if (stage == ImportStage.PREVIEW) "导入预览" else "一键导入",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.padding(24.dp))
            }
        }
    ) { innerPadding ->
        when (stage) {
            ImportStage.INPUT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PageBgColor)
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "导入格式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "一行一条，字段顺序：标题|类型|日期|时间|地点描述|提醒方式|提前分钟|备注，也兼容 / 分隔。",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "日期推荐写：今天 / 明天 / 后天 / 0312 / 312 / 38 / 2026-03-12 / 3-12",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "类型支持：攻城 / 征兵 / 赶路 / 修建 / 回复体力 / 卡免 / 抢夺 / 其他",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "提前分钟支持：10 或 15、10、5 或 15,10,5 或 15，10，5",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "示例：主队出征|攻城|明天|晚上8点十分|郿乌(352,328)|振铃|10|主力先到",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "多提醒示例：卡免提醒|卡免|312|晚上8点十分|洛阳|通知|15、10、5|准备卡点",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        label = { Text("把计划文本粘贴到这里") }
                    )

                    Button(
                        onClick = {
                            if (inputText.lines().all { it.isBlank() }) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("请输入至少一条计划")
                                }
                            } else {
                                onParseRequested()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始解析")
                    }
                }
            }

            ImportStage.PREVIEW -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PageBgColor)
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "解析结果",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("成功：$successCount 条")
                            Text("失败：$failCount 条")
                            if (failCount > 0) {
                                Text(
                                    text = "成功项可进入添加页编辑，失败项可直接修改原文并重新解析。",
                                    modifier = Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (previewItems.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "当前没有可预览的内容",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = previewItems,
                                key = { _, item -> "${item.lineNumber}-${item.rawLine}" }
                            ) { index, item ->
                                ImportPreviewCard(
                                    item = item,
                                    onEdit = {
                                        if (item.success && item.plan != null) {
                                            onEditSuccessItem(item)
                                        } else {
                                            editingIndex = index
                                            editingRawLine = item.rawLine
                                        }
                                    },
                                    onDelete = {
                                        onDeletePreviewItem(index)
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onGoInput,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("返回修改")
                        }

                        Button(
                            onClick = {
                                if (successCount == 0) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("没有可导入的成功项")
                                    }
                                } else {
                                    onImportConfirmed()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("确认导入（$successCount）")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewCard(
    item: ImportPreviewItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = if (item.success) Color(0xFFE8F2E5) else Color(0xFFF6E2E2)
    val borderColor = if (item.success) Color(0xFF6D8E63) else Color(0xFFB26565)

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "第 ${item.lineNumber} 行",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (item.success) "解析成功" else "解析失败",
                    color = borderColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.6f)
            ) {
                Text(
                    text = item.rawLine,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (item.success && item.plan != null) {
                Divider()
                PreviewInfo("标题", item.plan.title)
                PreviewInfo("类型", item.plan.type.label)
                PreviewInfo("时间", formatPlanDateTime(item.plan.scheduledTimeMillis))
                PreviewInfo("地点", buildPreviewLocation(item.plan))
                PreviewInfo("提醒", buildPreviewReminder(item.plan))
                PreviewInfo("备注", if (item.plan.note.isBlank()) "无" else item.plan.note)
            } else {
                Divider()
                Text(
                    text = "错误原因：${item.errorMessage ?: "解析失败"}",
                    color = Color(0xFF8E3C3C),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                item.errorDetail?.let { detail ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "出错字段：${detail.fieldName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "可写模板：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    detail.examples.forEach { example ->
                        Text(
                            text = "• $example",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Text(if (item.success) "编辑计划" else "编辑原文")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun PreviewInfo(
    title: String,
    content: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun splitImportFields(rawLine: String): List<String> {
    val pipeParts = rawLine.split("|").map { it.trim() }
    if (pipeParts.size == 8) return pipeParts

    val slashParts = rawLine.split("/").map { it.trim() }
    if (slashParts.size == 8) return slashParts

    return pipeParts
}

private fun parseSingleLine(rawLine: String, lineNumber: Int): ImportPreviewItem {
    return try {
        val fields = splitImportFields(rawLine)
        if (fields.size != 8) {
            throw ImportParseException(
                ImportErrorDetail(
                    fieldName = "整行格式",
                    reason = "字段数量不正确，应为 8 段。支持使用 | 或 / 分隔。",
                    examples = listOf(
                        "标题|类型|日期|时间|地点描述|提醒方式|提前分钟|备注",
                        "主队出征|攻城|明天|20:30|郿乌(352,328)|振铃|10|主力先到",
                        "卡免提醒|卡免|312|晚上8点十分|洛阳|通知|15、10、5|准备卡点"
                    )
                )
            )
        }

        val title = fields[0].normalizeNullableField()
            ?: failField(
                "标题",
                "标题不能为空",
                listOf("主队出征", "卡免提醒", "抢夺窗口")
            )

        val type = parsePlanType(fields[1])
        val date = parseDatePart(fields[2])
        val time = parseTimePart(fields[3])
        val location = parseLocation(fields[4])
        val reminderMode = parseReminderMode(fields[5])
        val leadMinutes = parseLeadMinutes(fields[6], reminderMode)
        val note = fields[7].normalizeNullableField().orEmpty()

        val scheduledTimeMillis = buildDateTimeMillis(date, time)

        val plan = PlanItem(
            id = System.currentTimeMillis() + lineNumber,
            title = title,
            type = type,
            placeName = location.placeName,
            x = location.x,
            y = location.y,
            scheduledTimeMillis = scheduledTimeMillis,
            note = note,
            status = PlanStatus.PENDING,
            reminderMode = reminderMode,
            reminderLeadMinutes = leadMinutes.primary,
            extraReminderLeadMinutes = leadMinutes.extra
        )

        ImportPreviewItem(
            rawLine = rawLine,
            lineNumber = lineNumber,
            success = true,
            plan = plan,
            errorMessage = null,
            errorDetail = null
        )
    } catch (e: ImportParseException) {
        ImportPreviewItem(
            rawLine = rawLine,
            lineNumber = lineNumber,
            success = false,
            plan = null,
            errorMessage = e.detail.reason,
            errorDetail = e.detail
        )
    } catch (e: Exception) {
        ImportPreviewItem(
            rawLine = rawLine,
            lineNumber = lineNumber,
            success = false,
            plan = null,
            errorMessage = e.message ?: "解析失败",
            errorDetail = null
        )
    }
}

private fun failField(
    fieldName: String,
    reason: String,
    examples: List<String>
): Nothing {
    throw ImportParseException(
        ImportErrorDetail(
            fieldName = fieldName,
            reason = reason,
            examples = examples
        )
    )
}

private fun String.normalizeNullableField(): String? {
    val trimmed = trim()
    return when (trimmed) {
        "", ".", "。", "-", "无", "空" -> null
        else -> trimmed
    }
}

private fun parsePlanType(value: String): PlanType {
    return when (value.normalizeNullableField() ?: failField(
        "类型",
        "类型不能为空",
        listOf("攻城", "征兵", "赶路", "修建", "回复体力", "卡免", "抢夺", "其他", "1", "6", "7", "攻", "免", "抢")
    )) {
        "0", "其", "其他" -> PlanType.OTHER
        "1", "攻", "攻城" -> PlanType.SIEGE
        "2", "征", "征兵" -> PlanType.RECRUIT
        "3", "路", "赶路", "行军" -> PlanType.MARCH
        "4", "建", "修建", "建造" -> PlanType.BUILD
        "5", "体", "体力", "回复体力" -> PlanType.STAMINA
        "6", "免", "卡免" -> PlanType.TRUCE
        "7", "抢", "抢夺" -> PlanType.SNATCH
        else -> failField(
            "类型",
            "类型无效",
            listOf(
                "攻城", "征兵", "赶路", "修建", "回复体力", "卡免", "抢夺", "其他",
                "1", "2", "3", "4", "5", "6", "7",
                "攻", "征", "路", "建", "体", "免", "抢", "其"
            )
        )
    }
}

private fun parseReminderMode(value: String): ReminderMode {
    return when (value.normalizeNullableField() ?: failField(
        "提醒方式",
        "提醒方式不能为空",
        listOf("不提醒", "通知", "振铃", "闹钟模式", "0", "1", "2", "3", "无", "通", "振", "闹")
    )) {
        "0", "无", "不提醒" -> ReminderMode.NONE
        "1", "通", "通知" -> ReminderMode.NOTIFICATION
        "2", "振", "振铃" -> ReminderMode.RING
        "3", "闹", "闹钟", "闹钟模式" -> ReminderMode.ALARM
        else -> failField(
            "提醒方式",
            "提醒方式无效",
            listOf("不提醒", "通知", "振铃", "闹钟模式", "0", "1", "2", "3", "无", "通", "振", "闹")
        )
    }
}

private fun parseLeadMinutes(value: String, mode: ReminderMode): ParsedLeadMinutes {
    if (mode == ReminderMode.NONE) {
        return ParsedLeadMinutes(primary = null, extra = emptyList())
    }

    val normalized = value.normalizeNullableField() ?: failField(
        "提前分钟",
        "提前分钟不能为空",
        listOf("5", "10", "15、10、5", "15,10,5", "15，10，5")
    )

    val parts = normalized
        .replace("，", ",")
        .replace("、", ",")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (parts.isEmpty()) {
        failField(
            "提前分钟",
            "提前分钟不能为空",
            listOf("5", "10", "15、10、5", "15,10,5", "15，10，5")
        )
    }

    val minutes = parts.map { text ->
        text.toIntOrNull() ?: failField(
            "提前分钟",
            "提前分钟不是有效数字",
            listOf("5", "10", "15、10、5", "15,10,5", "15，10，5")
        )
    }

    if (minutes.any { it <= 0 }) {
        failField(
            "提前分钟",
            "提前分钟必须大于 0",
            listOf("5", "10", "15、10、5")
        )
    }

    val normalizedMinutes = minutes.distinct().sortedDescending()
    return ParsedLeadMinutes(
        primary = normalizedMinutes.firstOrNull(),
        extra = normalizedMinutes.drop(1)
    )
}

private fun parseDatePart(value: String): ParsedDate {
    val normalized = value.normalizeNullableField() ?: failField(
        "日期",
        "日期不能为空",
        listOf("今天", "明天", "后天", "0312", "312", "38", "2026-03-12", "3-12")
    )

    val today = Calendar.getInstance()

    return when (normalized) {
        "今天" -> ParsedDate(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH)
        )

        "明天" -> {
            today.add(Calendar.DAY_OF_MONTH, 1)
            ParsedDate(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH)
            )
        }

        "后天" -> {
            today.add(Calendar.DAY_OF_MONTH, 2)
            ParsedDate(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH)
            )
        }

        else -> {
            val fullDash = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
            val shortDash = Regex("""^(\d{1,2})-(\d{1,2})$""")
            val digits4 = Regex("""^\d{4}$""")
            val digits3 = Regex("""^\d{3}$""")
            val digits2 = Regex("""^\d{2}$""")

            when {
                fullDash.matches(normalized) -> {
                    val match = fullDash.find(normalized)!!
                    ParsedDate(
                        match.groupValues[1].toInt(),
                        match.groupValues[2].toInt(),
                        match.groupValues[3].toInt()
                    )
                }

                shortDash.matches(normalized) -> {
                    val match = shortDash.find(normalized)!!
                    ParsedDate(
                        today.get(Calendar.YEAR),
                        match.groupValues[1].toInt(),
                        match.groupValues[2].toInt()
                    )
                }

                digits4.matches(normalized) -> {
                    val month = normalized.substring(0, 2).toInt()
                    val day = normalized.substring(2, 4).toInt()
                    ParsedDate(today.get(Calendar.YEAR), month, day)
                }

                digits3.matches(normalized) -> {
                    val firstMonth = normalized.substring(0, 1).toInt()
                    val lastTwoDay = normalized.substring(1, 3).toInt()

                    if (firstMonth in 1..9 && lastTwoDay in 1..31) {
                        ParsedDate(today.get(Calendar.YEAR), firstMonth, lastTwoDay)
                    } else {
                        val firstTwoMonth = normalized.substring(0, 2).toInt()
                        val lastDay = normalized.substring(2, 3).toInt()
                        if (firstTwoMonth in 1..12 && lastDay in 1..9) {
                            ParsedDate(today.get(Calendar.YEAR), firstTwoMonth, lastDay)
                        } else {
                            failField(
                                "日期",
                                "三位数字日期无法识别",
                                listOf("312 = 3月12日", "120 = 12月0日（无效）", "推荐直接写 312 / 111 / 38")
                            )
                        }
                    }
                }

                digits2.matches(normalized) -> {
                    val month = normalized.substring(0, 1).toInt()
                    val day = normalized.substring(1, 2).toInt()
                    ParsedDate(today.get(Calendar.YEAR), month, day)
                }

                else -> failField(
                    "日期",
                    "日期格式错误",
                    listOf("今天", "明天", "后天", "0312", "312", "38", "2026-03-12", "3-12")
                )
            }
        }
    }
}

private fun parseTimePart(value: String): ParsedTime {
    val normalized = value.normalizeNullableField()
        ?.replace("：", ":")
        ?.replace(" ", "")
        ?: failField(
            "时间",
            "时间不能为空",
            listOf("20:30", "8:05", "十一点", "上午7点", "上午九点", "下午两点半", "晚上8点十分")
        )

    val colonPattern = Regex("""^(\d{1,2}):(\d{1,2})$""")
    val colonMatch = colonPattern.find(normalized)
    if (colonMatch != null) {
        val hour = colonMatch.groupValues[1].toInt()
        val minute = colonMatch.groupValues[2].toInt()

        if (hour !in 0..23) {
            failField("时间", "小时超出范围", listOf("20:30", "8:05", "23:59"))
        }
        if (minute !in 0..59) {
            failField("时间", "分钟超出范围", listOf("20:30", "8:05", "23:59"))
        }

        return ParsedTime(hour, minute)
    }

    return parseNaturalTime(normalized)
}

private fun parseNaturalTime(value: String): ParsedTime {
    var text = value
    var period: String? = null

    val periods = listOf("凌晨", "早上", "早晨", "上午", "中午", "下午", "傍晚", "晚上", "今晚")
    periods.firstOrNull { text.startsWith(it) }?.let {
        period = it
        text = text.removePrefix(it)
    }

    val pattern = Regex("""^([零〇一二两三四五六七八九十\d]+)点(?:(半|一刻|三刻|[零〇一二两三四五六七八九十\d]+分?|[零〇一二两三四五六七八九十\d]+分钟))?$""")
    val match = pattern.find(text) ?: failField(
        "时间",
        "时间格式错误",
        listOf("20:30", "8:05", "十一点", "上午7点", "上午九点", "下午两点半", "晚上8点十分")
    )

    val rawHour = parseFlexibleChineseNumber(match.groupValues[1]) ?: failField(
        "时间",
        "小时无法识别",
        listOf("十一点", "上午7点", "下午两点半")
    )

    val minutePart = match.groupValues[2]
    val rawMinute = when {
        minutePart.isBlank() -> 0
        minutePart == "半" -> 30
        minutePart == "一刻" -> 15
        minutePart == "三刻" -> 45
        else -> {
            val normalizedMinute = minutePart
                .removeSuffix("分钟")
                .removeSuffix("分")
            parseFlexibleChineseNumber(normalizedMinute) ?: failField(
                "时间",
                "分钟无法识别",
                listOf("晚上8点十分", "下午两点半", "上午九点一刻")
            )
        }
    }

    val hour = when (period) {
        "凌晨", "早上", "早晨", "上午" -> {
            if (rawHour == 12) 0 else rawHour
        }

        "中午" -> {
            when (rawHour) {
                in 0..10 -> rawHour + 12
                else -> rawHour
            }
        }

        "下午", "傍晚", "晚上", "今晚" -> {
            if (rawHour in 1..11) rawHour + 12 else rawHour
        }

        else -> rawHour
    }

    if (hour !in 0..23) {
        failField("时间", "小时超出范围", listOf("20:30", "十一点", "下午两点半"))
    }
    if (rawMinute !in 0..59) {
        failField("时间", "分钟超出范围", listOf("晚上8点十分", "下午两点半"))
    }

    return ParsedTime(hour, rawMinute)
}

private fun parseFlexibleChineseNumber(text: String): Int? {
    if (text.all { it.isDigit() }) return text.toInt()

    val normalized = text
        .replace("两", "二")
        .replace("〇", "零")

    val digitMap = mapOf(
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9
    )

    if ("十" !in normalized) {
        return if (normalized.length == 1) digitMap[normalized[0]] else null
    }

    return when {
        normalized == "十" -> 10
        normalized.startsWith("十") -> {
            val ones = digitMap[normalized.getOrNull(1)] ?: return null
            10 + ones
        }

        normalized.endsWith("十") -> {
            val tens = digitMap[normalized.first()] ?: return null
            tens * 10
        }

        normalized.length == 3 && normalized[1] == '十' -> {
            val tens = digitMap[normalized[0]] ?: return null
            val ones = digitMap[normalized[2]] ?: return null
            tens * 10 + ones
        }

        else -> null
    }
}

private fun buildDateTimeMillis(
    date: ParsedDate,
    time: ParsedTime
): Long {
    val calendar = Calendar.getInstance().apply {
        isLenient = false
        set(Calendar.YEAR, date.year)
        set(Calendar.MONTH, date.month - 1)
        set(Calendar.DAY_OF_MONTH, date.day)
        set(Calendar.HOUR_OF_DAY, time.hour)
        set(Calendar.MINUTE, time.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    return try {
        calendar.time
        calendar.timeInMillis
    } catch (_: Exception) {
        failField(
            "日期/时间",
            "日期时间无效",
            listOf("2026-03-12|20:30", "明天|晚上8点十分", "312|20:30")
        )
    }
}

private fun parseLocation(value: String): ParsedLocation {
    val normalized = value.normalizeNullableField() ?: return ParsedLocation("", null, null)

    val nameWithCoord = Regex("""^(.+)[(（]\s*(\d+)\s*,\s*(\d+)\s*[)）]$""")
    val coordOnly = Regex("""^[(（]\s*(\d+)\s*,\s*(\d+)\s*[)）]$""")

    return when {
        nameWithCoord.matches(normalized) -> {
            val match = nameWithCoord.find(normalized)!!
            ParsedLocation(
                placeName = match.groupValues[1].trim(),
                x = match.groupValues[2].toInt(),
                y = match.groupValues[3].toInt()
            )
        }

        coordOnly.matches(normalized) -> {
            val match = coordOnly.find(normalized)!!
            ParsedLocation(
                placeName = "",
                x = match.groupValues[1].toInt(),
                y = match.groupValues[2].toInt()
            )
        }

        else -> ParsedLocation(
            placeName = normalized,
            x = null,
            y = null
        )
    }
}

private fun formatPlanDateTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

private fun buildPreviewLocation(plan: PlanItem): String {
    val hasName = plan.placeName.isNotBlank()
    val hasCoord = plan.x != null && plan.y != null

    return when {
        hasName && hasCoord -> "${plan.placeName}（${plan.x},${plan.y}）"
        hasName -> plan.placeName
        hasCoord -> "（${plan.x},${plan.y}）"
        else -> "未设置"
    }
}

private fun buildPreviewReminder(plan: PlanItem): String {
    return when (plan.reminderMode) {
        ReminderMode.NONE -> "不提醒"
        else -> {
            val leads = plan.allReminderLeadMinutes
            if (leads.isEmpty()) {
                plan.reminderMode.label
            } else {
                "${plan.reminderMode.label}（提前 ${leads.joinToString("、")} 分钟）"
            }
        }
    }
}