package com.czsk.app.model

enum class PlanStatus {
    PENDING,
    DONE
}

enum class PlanType(val label: String) {
    SIEGE("攻城"),
    RECRUIT("征兵"),
    MARCH("赶路"),
    BUILD("修建"),
    STAMINA("回复体力"),
    TRUCE("卡免"),
    SNATCH("抢夺"),
    OTHER("其他")
}

enum class ReminderMode(val label: String) {
    NONE("不提醒"),
    NOTIFICATION("通知"),
    RING("振铃"),
    ALARM("闹钟模式")
}

data class PlanItem(
    val id: Long,
    val title: String,
    val type: PlanType,
    val placeName: String,
    val x: Int?,
    val y: Int?,
    val scheduledTimeMillis: Long,
    val note: String = "",
    val status: PlanStatus = PlanStatus.PENDING,
    val reminderMode: ReminderMode = ReminderMode.NOTIFICATION,
    val reminderLeadMinutes: Int? = 10,
    val extraReminderLeadMinutes: List<Int> = emptyList()
) {
    val locationText: String
        get() {
            val hasName = placeName.isNotBlank()
            val hasCoord = x != null && y != null

            return when {
                hasName && hasCoord -> "$placeName（$x,$y）"
                hasName -> placeName
                hasCoord -> "（$x,$y）"
                else -> "未设置"
            }
        }

    val allReminderLeadMinutes: List<Int>
        get() {
            if (reminderMode == ReminderMode.NONE) return emptyList()

            return (listOfNotNull(reminderLeadMinutes) + extraReminderLeadMinutes)
                .filter { it > 0 }
                .distinct()
                .sortedDescending()
        }
}