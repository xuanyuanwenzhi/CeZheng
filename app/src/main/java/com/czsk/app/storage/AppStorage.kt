package com.czsk.app.storage

import android.content.Context
import com.czsk.app.model.ConfigPlace
import com.czsk.app.model.PlanItem
import com.czsk.app.model.PlanStatus
import com.czsk.app.model.PlanType
import com.czsk.app.model.ReminderMode
import org.json.JSONArray
import org.json.JSONObject

object AppStorage {
    private const val PREF_NAME = "cezheng_app_storage"

    private const val KEY_PLANS = "plans_json"
    private const val KEY_VIBRATION = "vibration_enabled"
    private const val KEY_COORDINATE_VISIBLE = "coordinate_visible"
    private const val KEY_RINGTONE_NAME = "ringtone_name"
    private const val KEY_RINGTONE_URI = "ringtone_uri"
    private const val KEY_CONFIG_PLACES = "config_places_json"

    fun savePlans(context: Context, plans: List<PlanItem>) {
        val array = JSONArray()
        plans.forEach { plan ->
            val obj = JSONObject().apply {
                put("id", plan.id)
                put("title", plan.title)
                put("type", plan.type.name)
                put("placeName", plan.placeName)
                if (plan.x != null) put("x", plan.x) else put("x", JSONObject.NULL)
                if (plan.y != null) put("y", plan.y) else put("y", JSONObject.NULL)
                put("scheduledTimeMillis", plan.scheduledTimeMillis)
                put("note", plan.note)
                put("status", plan.status.name)
                put("reminderMode", plan.reminderMode.name)
                if (plan.reminderLeadMinutes != null) {
                    put("reminderLeadMinutes", plan.reminderLeadMinutes)
                } else {
                    put("reminderLeadMinutes", JSONObject.NULL)
                }

                val extraArray = JSONArray()
                plan.extraReminderLeadMinutes.forEach { extra ->
                    extraArray.put(extra)
                }
                put("extraReminderLeadMinutes", extraArray)
            }
            array.put(obj)
        }

        prefs(context)
            .edit()
            .putString(KEY_PLANS, array.toString())
            .apply()
    }

    fun loadPlans(context: Context): List<PlanItem> {
        val json = prefs(context).getString(KEY_PLANS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)

                    val x = if (obj.isNull("x")) null else obj.getInt("x")
                    val y = if (obj.isNull("y")) null else obj.getInt("y")
                    val reminderLeadMinutes =
                        if (obj.isNull("reminderLeadMinutes")) null else obj.getInt("reminderLeadMinutes")

                    val extraReminderLeadMinutes =
                        if (obj.has("extraReminderLeadMinutes") && !obj.isNull("extraReminderLeadMinutes")) {
                            val extraArray = obj.getJSONArray("extraReminderLeadMinutes")
                            buildList {
                                for (j in 0 until extraArray.length()) {
                                    add(extraArray.getInt(j))
                                }
                            }
                        } else {
                            emptyList()
                        }

                    add(
                        PlanItem(
                            id = obj.getLong("id"),
                            title = obj.getString("title"),
                            type = PlanType.valueOf(obj.getString("type")),
                            placeName = obj.optString("placeName", ""),
                            x = x,
                            y = y,
                            scheduledTimeMillis = obj.getLong("scheduledTimeMillis"),
                            note = obj.optString("note", ""),
                            status = PlanStatus.valueOf(obj.getString("status")),
                            reminderMode = ReminderMode.valueOf(
                                obj.optString("reminderMode", ReminderMode.NOTIFICATION.name)
                            ),
                            reminderLeadMinutes = reminderLeadMinutes,
                            extraReminderLeadMinutes = extraReminderLeadMinutes
                        )
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun saveConfigPlaces(context: Context, places: List<ConfigPlace>) {
        val array = JSONArray()
        places.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("x", item.x)
                put("y", item.y)
            }
            array.put(obj)
        }

        prefs(context)
            .edit()
            .putString(KEY_CONFIG_PLACES, array.toString())
            .apply()
    }

    fun loadConfigPlaces(context: Context): List<ConfigPlace> {
        val json = prefs(context).getString(KEY_CONFIG_PLACES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ConfigPlace(
                            id = obj.getLong("id"),
                            name = obj.getString("name"),
                            x = obj.getInt("x"),
                            y = obj.getInt("y")
                        )
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun saveVibrationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun loadVibrationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_VIBRATION, true)
    }

    fun saveCoordinateVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_COORDINATE_VISIBLE, visible).apply()
    }

    fun loadCoordinateVisible(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_COORDINATE_VISIBLE, true)
    }

    fun saveRingtone(context: Context, name: String, uri: String?) {
        prefs(context)
            .edit()
            .putString(KEY_RINGTONE_NAME, name)
            .putString(KEY_RINGTONE_URI, uri)
            .apply()
    }

    fun loadRingtoneName(context: Context): String {
        return prefs(context).getString(KEY_RINGTONE_NAME, "系统默认通知音") ?: "系统默认通知音"
    }

    fun loadRingtoneUri(context: Context): String? {
        return prefs(context).getString(KEY_RINGTONE_URI, null)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}