@file:OptIn(ExperimentalFoundationApi::class)

package com.czsk.app.ui.battle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.max

private val PageBgColor = Color(0xFFF4EBD8)
private val SelectedRowColor = Color(0xFFE4D7BB)

data class Team(
    val id: Long = System.currentTimeMillis(),
    var name: String,
    var travelSeconds: Int,
    var isDeparted: Boolean = false
)

@Composable
fun KaMiaoBattleScreen(onBack: () -> Unit) {

    val context = LocalContext.current

    val teams = remember { mutableStateListOf<Team>() }

    var teamNameInput by remember { mutableStateOf("") }
    var hoursInput by remember { mutableStateOf("") }
    var minutesInput by remember { mutableStateOf("") }
    var secondsInput by remember { mutableStateOf("") }

    var editingTeamId by remember { mutableStateOf<Long?>(null) }
    var expandedMenuId by remember { mutableStateOf<Long?>(null) }

    val prepOptions = listOf(3, 5, 10)
    var selectedPrepSeconds by remember { mutableStateOf(3) }

    var configExpanded by remember { mutableStateOf(true) }

    var isRunning by remember { mutableStateOf(false) }

    var intervalDurations by remember { mutableStateOf<List<Long>>(emptyList()) }

    var intervalStartTime by remember { mutableStateOf(0L)}

    var currentIntervalIndex by remember { mutableStateOf(0)}

    var totalEndTime by remember { mutableStateOf(0L)}

    var pausedRemaining by remember { mutableStateOf(0L)}

    var refreshTick by remember { mutableStateOf(0)}

    val sortedTeams by remember(teams) {
        derivedStateOf { teams.sortedByDescending { it.travelSeconds } }
    }

    val currentTeam by remember(sortedTeams, currentIntervalIndex) {
        derivedStateOf {
            sortedTeams.getOrNull(currentIntervalIndex)
        }
    }

    val previousTeam by remember(sortedTeams, currentIntervalIndex) {
        derivedStateOf {
            if (currentIntervalIndex > 0) sortedTeams.getOrNull(currentIntervalIndex - 1)
            else null
        }
    }

    val nextTeam by remember(sortedTeams, currentIntervalIndex) {
        derivedStateOf {
            sortedTeams.getOrNull(currentIntervalIndex + 1)
        }
    }

    val currentRemaining by remember(isRunning, intervalStartTime, intervalDurations, currentIntervalIndex, refreshTick) {
        derivedStateOf {

            if (!isRunning) return@derivedStateOf pausedRemaining

            if (intervalStartTime == 0L || currentIntervalIndex >= intervalDurations.size) return@derivedStateOf 0L

            val elapsed = System.currentTimeMillis() - intervalStartTime

            max(0L, intervalDurations[currentIntervalIndex] - elapsed)
        }
    }

    val totalRemaining by remember(totalEndTime, refreshTick) {
        derivedStateOf {
            max(0L, totalEndTime - System.currentTimeMillis())
        }
    }

    fun startCountdown() {

        if (teams.isEmpty()) return

        val sorted = sortedTeams

        val intervals = mutableListOf<Long>()

        intervals.add(selectedPrepSeconds * 1000L)

        for (i in 0 until sorted.size - 1) {

            val diff = (sorted[i].travelSeconds - sorted[i + 1].travelSeconds) * 1000L

            intervals.add(diff)
        }

        val now = System.currentTimeMillis()

        intervalDurations = intervals

        intervalStartTime = now

        totalEndTime = now + intervals.sum()

        teams.forEach { it.isDeparted = false }

        currentIntervalIndex = 0

        pausedRemaining = intervals[0]

        isRunning = true
    }

    fun pauseCountdown() {

        pausedRemaining = currentRemaining

        isRunning = false
    }

    fun resumeCountdown() {

        if (pausedRemaining <= 0) return

        intervalStartTime = System.currentTimeMillis() - (intervalDurations[currentIntervalIndex] - pausedRemaining)

        isRunning = true
    }

    fun resetCountdown() {

        isRunning = false

        intervalDurations = emptyList()

        intervalStartTime = 0L

        totalEndTime = 0L

        pausedRemaining = 0L

        currentIntervalIndex = 0

        teams.forEach { it.isDeparted = false }
    }

    fun departCurrent() {

        if (currentIntervalIndex >= intervalDurations.size) return

        val team = currentTeam

        if (team != null) {

            val idx = teams.indexOfFirst { it.id == team.id }

            if (idx != -1) {

                teams[idx] = teams[idx].copy(isDeparted = true)
            }
        }

        currentIntervalIndex++

        if (currentIntervalIndex < intervalDurations.size) {

            intervalStartTime = System.currentTimeMillis()

            pausedRemaining = intervalDurations[currentIntervalIndex]
        }

        refreshTick++
    }

    LaunchedEffect(isRunning) {

        if (!isRunning) return@LaunchedEffect

        while (isRunning) {

            delay(16)

            refreshTick++

            val elapsed = System.currentTimeMillis() - intervalStartTime

            if (
                currentIntervalIndex < intervalDurations.size &&
                elapsed >= intervalDurations[currentIntervalIndex]
            ) {

                departCurrent()
            }

            if (currentIntervalIndex >= intervalDurations.size) {

                isRunning = false
            }
        }
    }

    fun addOrUpdateTeam() {

        val h = hoursInput.toIntOrNull() ?: 0
        val m = minutesInput.toIntOrNull() ?: 0
        val s = secondsInput.toIntOrNull() ?: 0

        val total = h * 3600 + m * 60 + s

        if (teamNameInput.isBlank() || total <= 0) return

        if (editingTeamId != null) {

            val index = teams.indexOfFirst { it.id == editingTeamId }

            if (index != -1) {

                teams[index] = teams[index].copy(
                    name = teamNameInput,
                    travelSeconds = total,
                    isDeparted = false
                )
            }

        } else {

            teams.add(Team(name = teamNameInput, travelSeconds = total))
        }

        teamNameInput = ""
        hoursInput = ""
        minutesInput = ""
        secondsInput = ""
        editingTeamId = null
    }

    fun deleteTeam(team: Team) {

        teams.remove(team)

        if (editingTeamId == team.id) {

            editingTeamId = null

            teamNameInput = ""
            hoursInput = ""
            minutesInput = ""
            secondsInput = ""
        }
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
                TextButton(onClick = onBack) { Text("返回") }

                Text(
                    text = "卡秒出征",
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

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4)),
                shape = RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // 修改点：总剩余时间添加等宽字体，防止抖动
                    Text(
                        text = "总剩余 ${formatTime(totalRemaining)}",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.height(20.dp)) {

                        previousTeam?.let {

                            Text(
                                text = "↑ ${it.name}",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    if (currentTeam != null) {

                        Text(
                            text = currentTeam!!.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                    } else {

                        Text("无", fontSize = 28.sp, color = Color.Gray)
                    }

                    Box(modifier = Modifier.height(20.dp)) {

                        nextTeam?.let {

                            Text(
                                text = "↓ ${it.name}",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = formatTime(currentRemaining),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Button(
                            onClick = {
                                if (isRunning) pauseCountdown()
                                else resumeCountdown()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = intervalDurations.isNotEmpty()
                        ) {

                            Text(if (isRunning) "暂停" else "继续")
                        }

                        Button(
                            onClick = { resetCountdown() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {

                            Text("结束")
                        }
                    }

                    AnimatedVisibility(visible = !isRunning && intervalDurations.isEmpty()) {

                        Button(
                            onClick = { startCountdown() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = teams.isNotEmpty()
                        ) {

                            Text("开始")
                        }
                    }
                }
            }

            SectionCard(
                title = "队伍配置",
                expandable = true,
                expanded = if (isRunning) false else configExpanded,
                onExpandChange = { configExpanded = !configExpanded }
            ) {

                Column {

                    OutlinedTextField(
                        value = teamNameInput,
                        onValueChange = { teamNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("队伍名称") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {

                        OutlinedTextField(
                            value = hoursInput,
                            onValueChange = { hoursInput = it.filter { c -> c.isDigit() }.take(2) },
                            modifier = Modifier.weight(1f),
                            label = { Text("时") },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = minutesInput,
                            onValueChange = { minutesInput = it.filter { c -> c.isDigit() }.take(2) },
                            modifier = Modifier.weight(1f),
                            label = { Text("分") },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = secondsInput,
                            onValueChange = { secondsInput = it.filter { c -> c.isDigit() }.take(2) },
                            modifier = Modifier.weight(1f),
                            label = { Text("秒") },
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Text("提前准备：")

                        prepOptions.forEach {

                            FilterChip(
                                selected = selectedPrepSeconds == it,
                                onClick = { selectedPrepSeconds = it },
                                label = { Text("$it 秒") },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { addOrUpdateTeam() },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(if (editingTeamId != null) "更新" else "添加")
                    }
                }
            }

            SectionCard(title = "队伍列表") {

                if (teams.isEmpty()) {

                    Text("暂无队伍，请添加", color = Color.Gray)

                } else {

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                        sortedTeams.forEach { team ->

                            val selected = expandedMenuId == team.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selected) SelectedRowColor else Color.Transparent)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { expandedMenuId = team.id }
                                    )
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                Column {

                                    Text(
                                        team.name,
                                        fontWeight = if (team.isDeparted) FontWeight.Light else FontWeight.Bold,
                                        color = if (team.isDeparted) Color.Gray else Color.Unspecified
                                    )

                                    Text(
                                        formatDuration(team.travelSeconds),
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                }

                                if (team.isDeparted) {

                                    Text("已出发", color = Color.Green, fontSize = 12.sp)
                                }

                                DropdownMenu(
                                    expanded = expandedMenuId == team.id,
                                    onDismissRequest = { expandedMenuId = null },
                                    properties = PopupProperties(focusable = false)
                                ) {

                                    DropdownMenuItem(
                                        text = { Text("编辑") },
                                        onClick = {

                                            expandedMenuId = null

                                            teamNameInput = team.name

                                            val h = team.travelSeconds / 3600
                                            val m = (team.travelSeconds % 3600) / 60
                                            val s = team.travelSeconds % 60

                                            hoursInput = if (h > 0) h.toString() else ""
                                            minutesInput = if (m > 0) m.toString() else ""
                                            secondsInput = if (s > 0) s.toString() else ""

                                            editingTeamId = team.id
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {

                                            expandedMenuId = null

                                            deleteTeam(team)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

fun formatTime(millis: Long): String {

    if (millis <= 0) return "00:00:00.00"

    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val hundredths = (millis % 1000) / 10

    return String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
}

fun formatDuration(totalSeconds: Int): String {

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun SectionCard(
    title: String,
    expandable: Boolean = false,
    expanded: Boolean = true,
    onExpandChange: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1E4)),
        shape = RoundedCornerShape(18.dp)
    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (expandable) {

                    TextButton(onClick = { onExpandChange?.invoke() }) {

                        Text(if (expanded) "收起 ▲" else "展开 ▼")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(visible = expanded) {

                content()
            }
        }
    }
}