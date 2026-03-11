package com.czsk.app.model

// 新增remark字段，支持保存备注，兼容原有逻辑
data class ConfigPlace(
    val id: Long,
    val name: String,
    val x: String,
    val y: String,
    val remark: String = "" // 新增备注字段，默认空
)