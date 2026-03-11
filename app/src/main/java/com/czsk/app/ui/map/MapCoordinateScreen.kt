@file:OptIn(ExperimentalFoundationApi::class)

package com.czsk.app.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.czsk.app.model.ConfigPlace
import com.czsk.app.storage.AppStorage
import kotlinx.coroutines.launch

private val PageBgColor = Color(0xFFF4EBD8)
private val SelectedRowColor = Color(0xFFE4D7BB)
private val TableHeaderBg = Color(0xFFE0D8C8)

@Composable
fun MapCoordinateScreen(
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val coordinateList = remember { mutableStateListOf<ConfigPlace>() }

    var nameInput by remember { mutableStateOf("") }
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    var remarkInput by remember { mutableStateOf("") }
    var searchKeyword by remember { mutableStateOf("") }

    var isInputExpanded by remember { mutableStateOf(true) }

    var editingItemId by remember { mutableStateOf<Long?>(null) }

    var expandedMenuId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {

        val localData = AppStorage.loadConfigPlaces(context)

        if (localData.isEmpty()) {

            val initData = listOf(
                "洛阳 501.501", "弘农 471.451", "安 434.475", "新郑 541.501", "怀 514.567",
                "平阳 445.400", "建津 499.588", "虎牢关 566.531", "潼关 460.407", "夏阳 501.424",
                "乌枝 397.346", "泥阳 488.290", "襄武 341.250", "长安 446.293", "槐里 436.261",
                "冀 392.264", "街亭 428.226", "郑 357.301", "郿坞 391.364", "长子 314.369",
                "任城 501.621", "定陶 594.647", "东平 487.716", "陈留 541.647", "昌邑 567.674",
                "奉高 531.736", "卢 475.752", "高苑 482.766", "济南 475.807"
            ).mapIndexed { index, item ->

                val parts = item.split(" ")
                val xy = parts[1].split(".")

                ConfigPlace(
                    id = index.toLong(),
                    name = parts[0],
                    x = xy.getOrNull(0) ?: "",
                    y = xy.getOrNull(1) ?: "",
                    remark = ""
                )
            }

            coordinateList.addAll(initData)
            AppStorage.saveConfigPlaces(context, initData)

        } else {

            coordinateList.addAll(localData)
        }
    }

    val filteredList = remember(searchKeyword, coordinateList) {

        if (searchKeyword.isBlank()) {

            coordinateList

        } else {

            coordinateList.filter {
                it.name.contains(searchKeyword, ignoreCase = true)
            }
        }
    }

    fun saveToLocal() {

        AppStorage.saveConfigPlaces(context, coordinateList.toList())
    }

    fun clearInputs() {

        nameInput = ""
        xInput = ""
        yInput = ""
        remarkInput = ""
        editingItemId = null
    }

    fun addOrUpdateItem() {

        if (nameInput.isNotBlank() && xInput.isNotBlank() && yInput.isNotBlank()) {

            val newItem = ConfigPlace(
                id = editingItemId ?: System.currentTimeMillis(),
                name = nameInput,
                x = xInput,
                y = yInput,
                remark = remarkInput
            )

            if (editingItemId != null) {

                val index = coordinateList.indexOfFirst { it.id == editingItemId }

                if (index != -1) {
                    coordinateList[index] = newItem
                }

            } else {

                coordinateList.add(0, newItem)
            }

            saveToLocal()
            clearInputs()
            searchKeyword = ""
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

                TextButton(onClick = onBack) {
                    Text("返回")
                }

                Text(
                    text = "地图坐标",
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "该页面仅做测试还未完工，坐标仅为率土之滨s1部分城池坐标。长按可以进行编辑置顶和删除",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            SectionCard(
                title = "坐标录入",
                expandable = true,
                expanded = isInputExpanded,
                onExpandChange = { isInputExpanded = !isInputExpanded }
            ) {

                AnimatedVisibility(visible = isInputExpanded) {

                    Column {

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("名称") },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            OutlinedTextField(
                                value = xInput,
                                onValueChange = { xInput = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("X坐标") },
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = yInput,
                                onValueChange = { yInput = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Y坐标") },
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = remarkInput,
                            onValueChange = { remarkInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("备注") },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            Button(
                                onClick = { searchKeyword = nameInput },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("搜索")
                            }

                            Button(
                                onClick = { addOrUpdateItem() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (editingItemId != null) "更新" else "添加")
                            }
                        }
                    }
                }
            }

            SectionCard(title = "坐标列表") {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TableHeaderBg)
                        .padding(10.dp)
                ) {

                    Text("名称", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("X坐标", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Y坐标", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("备注", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    items(
                        items = filteredList,
                        key = { it.id }
                    ) { item ->

                        val isSelected = expandedMenuId == item.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) SelectedRowColor else Color.Transparent)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        expandedMenuId = item.id
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(item.name, modifier = Modifier.weight(1f))
                            Text(item.x, modifier = Modifier.weight(1f))
                            Text(item.y, modifier = Modifier.weight(1f))
                            Text(item.remark, modifier = Modifier.weight(1f))

                            DropdownMenu(
                                expanded = expandedMenuId == item.id,
                                onDismissRequest = { expandedMenuId = null },
                                properties = PopupProperties(focusable = false)
                            ) {

                                DropdownMenuItem(
                                    text = { Text("置顶") },
                                    onClick = {

                                        expandedMenuId = null

                                        val index = coordinateList.indexOfFirst { it.id == item.id }

                                        if (index != -1) {

                                            val targetItem = coordinateList.removeAt(index)

                                            coordinateList.add(0, targetItem)

                                            saveToLocal()

                                            scope.launch {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("编辑") },
                                    onClick = {

                                        expandedMenuId = null

                                        nameInput = item.name
                                        xInput = item.x
                                        yInput = item.y
                                        remarkInput = item.remark
                                        editingItemId = item.id
                                        isInputExpanded = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {

                                        expandedMenuId = null

                                        coordinateList.removeIf { it.id == item.id }

                                        saveToLocal()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
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

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = title,
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

            content()
        }
    }
}