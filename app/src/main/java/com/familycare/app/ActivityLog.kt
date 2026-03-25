package com.familycare.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class ActivityEntry(val packageName: String, val appName: String, val timestamp: Long, val action: String)

object ActivityLog {
    private val gson = Gson()
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    private var ctx: Context? = null

    fun init(context: Context) { ctx = context.applicationContext }

    fun add(pkg: String, appName: String, action: String) {
        val c = ctx ?: return
        val list = getAll().toMutableList()
        list.add(0, ActivityEntry(pkg, appName, System.currentTimeMillis(), action))
        if (list.size > 100) list.subList(100, list.size).clear()
        c.getSharedPreferences("fc_log", Context.MODE_PRIVATE)
            .edit().putString("log", gson.toJson(list)).apply()
    }

    fun getAll(): List<ActivityEntry> {
        val c = ctx ?: return emptyList()
        val json = c.getSharedPreferences("fc_log", Context.MODE_PRIVATE).getString("log", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<ActivityEntry>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun formatForTelegram(): String {
        val entries = getAll().take(20)
        if (entries.isEmpty()) return "📋 Belum ada aktivitas."
        val sb = StringBuilder("📋 *Log Aktivitas (20 terakhir)*\n\n")
        entries.forEach {
            val icon = when (it.action) { "owner" -> "✅"; "stranger" -> "🚨"; "locked" -> "🔒"; else -> "•" }
            sb.append("$icon `${fmt.format(Date(it.timestamp))}` *${it.appName}*\n")
        }
        return sb.toString()
    }
}
