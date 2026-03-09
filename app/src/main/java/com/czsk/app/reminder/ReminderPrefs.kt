package com.czsk.app.reminder

import android.content.Context
import android.media.RingtoneManager

object ReminderPrefs {
    private const val PREF_NAME = "cezheng_reminder_prefs"
    private const val KEY_RINGTONE_NAME = "ringtone_name"
    private const val KEY_RINGTONE_URI = "ringtone_uri"

    fun saveRingtone(context: Context, name: String, uri: String?) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_RINGTONE_NAME, name)
            .putString(KEY_RINGTONE_URI, uri)
            .apply()
    }

    fun getRingtoneName(context: Context): String {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_RINGTONE_NAME, "系统默认通知音") ?: "系统默认通知音"
    }

    fun getRingtoneUri(context: Context): String? {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getString(
            KEY_RINGTONE_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString()
        )
    }
}