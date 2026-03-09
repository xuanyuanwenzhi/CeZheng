package com.czsk.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.czsk.app.model.PlanItem
import com.czsk.app.model.PlanStatus
import com.czsk.app.model.PlanType
import com.czsk.app.model.ReminderMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlanFilter {
    ALL,
    URGENT,
    PENDING,
    DONE
}

private val PageBgColor = Color(0xFFF4EBD8)
private val DoneCardColor = Color(0xFFE2DEDA)

private fun planTypeContainerColor(type: PlanType): Color {
    return when (type) {
        PlanType.SIEGE -> Color(0xFFE7D9D7)
        PlanType.RECRUIT -> Color(0xFFE7DEC8)
        PlanType.MARCH -> Color(0xFFD8E0EA)
        PlanType.BUILD -> Color(0xFFDCE3D7)
        PlanType.STAMINA -> Color(0xFFD8E6E1)
        PlanType.TRUCE -> Color(0xFFECE2A8)
        PlanType.SNATCH -> Color(0xFFF1D3A8)
        PlanType.OTHER -> Color(0xFFE1E1E1)
    }
}

private fun planTypeAccentColor(type: PlanType): Color {
    return when (type) {
        PlanType.SIEGE -> Color(0xFF7A5650)
        PlanType.RECRUIT -> Color(0xFF8A6E3B)
        PlanType.MARCH -> Color(0xFF50657D)
        PlanType.BUILD -> Color(0xFF62725F)
        PlanType.STAMINA -> Color(0xFF4F7A6D)
        PlanType.TRUCE -> Color(0xFF9C8600)
        PlanType.SNATCH -> Color(0xFFB86A1F)
        PlanType.OTHER -> Color(0xFF666666)
    }
}

private fun formatDateTime(timeMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}

private fun formatRemainingTime(targetTimeMillis: Long, nowMillis: Long): String {
    val diff = targetTimeMillis - nowMillis
    if (diff <= 0L) return "已到时"

    val totalMinutes = diff / 60_000L
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 -> "${days}天 ${hours}小时 ${minutes}分钟"
        hours > 0 -> "${hours}小时 ${minutes}分钟"
        else -> "${minutes}分钟"
    }
}

private fun formatReminder(plan: PlanItem): String {
    return when (plan.reminderMode) {
        ReminderMode.NONE -> "不提醒"
        ReminderMode.NOTIFICATION,
        ReminderMode.RING,
        ReminderMode.ALARM -> {
            val modeText = plan.reminderMode.label
            val leads = plan.allReminderLeadMinutes
            if (leads.isEmpty()) {
                modeText
            } else {
                "$modeText（提前 ${leads.joinToString("、")} 分钟）"
            }
        }
    }
}

private fun displayLocation(plan: PlanItem, coordinateVisible: Boolean): String {
    return if (coordinateVisible) {
        plan.locationText
    } else {
        if (plan.placeName.isNotBlank()) plan.placeName else "地点信息已隐藏"
    }
}

private fun exportDate(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMillis))
}

private fun exportTime(timeMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

private fun exportLocation(plan: PlanItem): String {
    val hasName = plan.placeName.isNotBlank()
    val hasCoord = plan.x != null && plan.y != null

    return when {
        hasName && hasCoord -> "${plan.placeName}(${plan.x},${plan.y})"
        hasName -> plan.placeName
        hasCoord -> "(${plan.x},${plan.y})"
        else -> ""
    }
}

private fun sanitizeExportField(value: String): String {
    return value
        .replace("|", "｜")
        .replace("\n", " ")
        .replace("\r", " ")
        .trim()
}

private fun buildExportLine(plan: PlanItem): String {
    val title = sanitizeExportField(plan.title)
    val type = sanitizeExportField(plan.type.label)
    val date = exportDate(plan.scheduledTimeMillis)
    val time = exportTime(plan.scheduledTimeMillis)
    val location = sanitizeExportField(exportLocation(plan))
    val reminderMode = sanitizeExportField(plan.reminderMode.label)
    val leadMinutes = plan.allReminderLeadMinutes.joinToString("、")
    val note = sanitizeExportField(plan.note)

    return listOf(
        title,
        type,
        date,
        time,
        location,
        reminderMode,
        leadMinutes,
        note
    ).joinToString("|")
}

@Composable
fun HomeScreen(
    plans: List<PlanItem>,
    coordinateVisible: Boolean,
    onNavigateAdd: () -> Unit,
    onNavigateEdit: (PlanItem) -> Unit,
    onNavigateImport: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateConfig: () -> Unit,
    onNavigateMore: () -> Unit,
    onToggleDone: (PlanItem) -> Unit,
    onDelete: (PlanItem) -> Unit
) {
    var currentFilter by remember { mutableStateOf(PlanFilter.ALL) }
    var fabExpanded by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectionMode by remember { mutableStateOf(false) }

    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }
    val selectedStates = remember { mutableStateMapOf<Long, Boolean>() }

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val urgentPredicate: (PlanItem) -> Boolean = {
        it.status == PlanStatus.PENDING &&
                (it.scheduledTimeMillis - nowMillis) in 0..(60 * 60 * 1000L)
    }

    val filteredPlans = plans.filter {
        when (currentFilter) {
            PlanFilter.ALL -> true
            PlanFilter.URGENT -> urgentPredicate(it)
            PlanFilter.PENDING -> it.status == PlanStatus.PENDING
            PlanFilter.DONE -> it.status == PlanStatus.DONE
        }
    }

    val sortedPlans = filteredPlans.sortedWith { a, b ->
        when {
            a.status != b.status -> {
                if (a.status == PlanStatus.PENDING) -1 else 1
            }
            a.status == PlanStatus.PENDING -> {
                a.scheduledTimeMillis.compareTo(b.scheduledTimeMillis)
            }
            else -> {
                b.scheduledTimeMillis.compareTo(a.scheduledTimeMillis)
            }
        }
    }

    val pendingCount = plans.count { it.status == PlanStatus.PENDING }
    val doneCount = plans.count { it.status == PlanStatus.DONE }
    val urgentCount = plans.count(urgentPredicate)
    val selectedCount = selectedStates.count { it.value }

    fun copyExportText(text: String, successMessage: String) {
        clipboardManager.setText(AnnotatedString(text))
        scope.launch {
            snackbarHostState.showSnackbar(successMessage)
        }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedStates.clear()
    }

    fun clearSelectionAndExit() {
        selectedStates.clear()
        selectionMode = false
    }

    fun toggleSelected(planId: Long) {
        val willSelect = selectedStates[planId] != true
        if (willSelect) {
            selectedStates[planId] = true
        } else {
            selectedStates.remove(planId)
            if (selectedStates.isEmpty()) {
                exitSelectionMode()
            }
        }
    }

    Scaffold(
        containerColor = PageBgColor,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PageBgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "策征时刻",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "运筹帷幄，准时出征——你的SLG战场闹钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (fabExpanded && !selectionMode) {
                    MiniActionButton(label = "➕ 添加", onClick = onNavigateAdd)
                    MiniActionButton(label = "📥 一键导入", onClick = onNavigateImport)
                    MiniActionButton(label = "🧪 配置信息", onClick = onNavigateConfig)
                    MiniActionButton(label = "⚙️ 设置", onClick = onNavigateSettings)
                    MiniActionButton(label = "📦 其他", onClick = onNavigateMore)
                }

                FloatingActionButton(
                    onClick = {
                        if (!selectionMode) {
                            fabExpanded = !fabExpanded
                        }
                    },
                    containerColor = Color(0xFFD8C9A7)
                ) {
                    Text(
                        text = if (fabExpanded && !selectionMode) "−" else "+",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBgColor)
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SummaryFilterSection(
                    urgentCount = urgentCount,
                    pendingCount = pendingCount,
                    doneCount = doneCount,
                    currentFilter = currentFilter,
                    onFilterClick = { clicked ->
                        currentFilter = if (currentFilter == clicked) {
                            PlanFilter.ALL
                        } else {
                            clicked
                        }
                    }
                )
            }

            item {
                ExpandControlSection(
                    selectionMode = selectionMode,
                    selectedCount = selectedCount,
                    onExpandAll = {
                        sortedPlans.forEach { expandedStates[it.id] = true }
                    },
                    onCollapseAll = {
                        sortedPlans.forEach { expandedStates[it.id] = false }
                    },
                    onEnterSelectionMode = {
                        fabExpanded = false
                        selectionMode = true
                        selectedStates.clear()
                    },
                    onSelectAll = {
                        sortedPlans.forEach { selectedStates[it.id] = true }
                    },
                    onClearSelection = {
                        clearSelectionAndExit()
                    },
                    onExportSelected = {
                        val selectedPlans = sortedPlans.filter { selectedStates[it.id] == true }
                        if (selectedPlans.isEmpty()) {
                            exitSelectionMode()
                        } else {
                            val exportText = selectedPlans.joinToString("\n") { buildExportLine(it) }
                            copyExportText(exportText, "已复制 ${selectedPlans.size} 条计划到剪贴板")
                            exitSelectionMode()
                        }
                    }
                )
            }

            item {
                Text(
                    text = if (selectionMode) "计划列表（选择导出）" else "计划列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (sortedPlans.isEmpty()) {
                item {
                    EmptyStateCard()
                }
            } else {
                items(
                    items = sortedPlans,
                    key = { it.id }
                ) { item ->
                    val expanded = expandedStates[item.id] ?: false
                    val selected = selectedStates[item.id] == true

                    PlanCard(
                        plan = item,
                        nowMillis = nowMillis,
                        expanded = expanded,
                        coordinateVisible = coordinateVisible,
                        selectionMode = selectionMode,
                        selected = selected,
                        onToggleExpand = {
                            expandedStates[item.id] = !expanded
                        },
                        onToggleSelected = {
                            toggleSelected(item.id)
                        },
                        onToggleDone = { onToggleDone(item) },
                        onEdit = { onNavigateEdit(item) },
                        onDelete = { onDelete(item) },
                        onExport = {
                            copyExportText(buildExportLine(item), "已复制到剪贴板")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryFilterSection(
    urgentCount: Int,
    pendingCount: Int,
    doneCount: Int,
    currentFilter: PlanFilter,
    onFilterClick: (PlanFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryFilterCard(
            modifier = Modifier.weight(1f),
            title = "需盯紧",
            value = urgentCount.toString(),
            hint = "60分钟内",
            selected = currentFilter == PlanFilter.URGENT,
            onClick = { onFilterClick(PlanFilter.URGENT) }
        )
        SummaryFilterCard(
            modifier = Modifier.weight(1f),
            title = "待执行",
            value = pendingCount.toString(),
            hint = "点按筛选",
            selected = currentFilter == PlanFilter.PENDING,
            onClick = { onFilterClick(PlanFilter.PENDING) }
        )
        SummaryFilterCard(
            modifier = Modifier.weight(1f),
            title = "已完成",
            value = doneCount.toString(),
            hint = "点按筛选",
            selected = currentFilter == PlanFilter.DONE,
            onClick = { onFilterClick(PlanFilter.DONE) }
        )
    }
}

@Composable
private fun SummaryFilterCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) Color(0xFFE4D7BB) else Color(0xFFF7F1E4)
    val borderColor = if (selected) Color(0xFF9C8960) else Color.Transparent

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniActionButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFF7F1E4),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExpandControlSection(
    selectionMode: Boolean,
    selectedCount: Int,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExportSelected: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!selectionMode) {
            Button(
                onClick = onExpandAll,
                modifier = Modifier.weight(1f)
            ) {
                Text("全部展开", fontSize = 12.sp, maxLines = 1)
            }

            Button(
                onClick = onCollapseAll,
                modifier = Modifier.weight(1f)
            ) {
                Text("全部折叠", fontSize = 12.sp, maxLines = 1)
            }

            Button(
                onClick = onEnterSelectionMode,
                modifier = Modifier.weight(1f)
            ) {
                Text("导出", fontSize = 12.sp, maxLines = 1)
            }
        } else {
            Button(
                onClick = onSelectAll,
                modifier = Modifier.weight(1f)
            ) {
                Text("全选", fontSize = 12.sp, maxLines = 1)
            }

            Button(
                onClick = onClearSelection,
                modifier = Modifier.weight(1f)
            ) {
                Text("清空", fontSize = 12.sp, maxLines = 1)
            }

            Button(
                onClick = onExportSelected,
                modifier = Modifier.weight(1f)
            ) {
                Text("导出（$selectedCount）", fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanItem,
    nowMillis: Long,
    expanded: Boolean,
    coordinateVisible: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelected: () -> Unit,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val isDone = plan.status == PlanStatus.DONE
    val typeBg = planTypeContainerColor(plan.type)
    val typeAccent = planTypeAccentColor(plan.type)
    val remainingText = if (isDone) "已结束" else formatRemainingTime(plan.scheduledTimeMillis, nowMillis)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    Modifier.border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color(0xFF8A6E3B) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable {
                if (selectionMode) {
                    onToggleSelected()
                } else {
                    onToggleExpand()
                }
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) DoneCardColor else typeBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectionIndicator(selected = selected)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = plan.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TypeBadge(type = plan.type)
                        }
                    }

                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) Color(0xFF6E6E6E) else typeAccent
                    )
                }
            } else if (!expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TypeBadge(type = plan.type)
                    }

                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) Color(0xFF6E6E6E) else typeAccent
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TypeBadge(type = plan.type)
                            StatusBadge(
                                text = if (isDone) "已完成" else "待执行",
                                color = if (isDone) Color(0xFF6E6E6E) else typeAccent
                            )
                        }
                    }

                    TextButton(onClick = onToggleExpand) {
                        Text("折叠")
                    }
                }

                Text(
                    text = "剩余时间：$remainingText",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDone) Color(0xFF6E6E6E) else typeAccent
                )

                Divider(color = if (isDone) Color(0xFFB9B2AA) else typeAccent.copy(alpha = 0.18f))

                InfoBlock(
                    title = "地点",
                    content = displayLocation(plan, coordinateVisible)
                )

                InfoBlock(
                    title = "计划时间",
                    content = formatDateTime(plan.scheduledTimeMillis)
                )

                InfoBlock(
                    title = "提醒方式",
                    content = formatReminder(plan)
                )

                if (plan.note.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDone) Color(0xFFF1EFEC) else Color.White.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = "备注：${plan.note}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onEdit) {
                        Text("编辑", color = if (isDone) Color(0xFF6E6E6E) else typeAccent)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onDelete) {
                        Text("删除", color = if (isDone) Color(0xFF6E6E6E) else typeAccent)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onExport) {
                        Text("导出", color = if (isDone) Color(0xFF6E6E6E) else typeAccent)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onToggleDone) {
                        Text(if (isDone) "恢复待执行" else "标记完成")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .width(22.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFF8A6E3B) else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (selected) Color(0xFF8A6E3B) else Color(0xFF9E8F74),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(
                text = "✓",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TypeBadge(type: PlanType) {
    val bg = planTypeContainerColor(type)
    val accent = planTypeAccentColor(type)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = type.label,
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.32f),
                shape = CircleShape
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InfoBlock(
    title: String,
    content: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7F1E4)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "当前没有符合筛选条件的计划",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "点右下角按钮展开菜单，可以进入添加、导入、设置等页面。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}