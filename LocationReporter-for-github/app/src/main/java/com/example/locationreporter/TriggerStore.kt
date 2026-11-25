package com.example.locationreporter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一个每日触发时段:24 小时制的时、分。 */
data class Trigger(val id: Long, val hour: Int, val minute: Int, val enabled: Boolean = true) {
    fun label() = "%02d:%02d".format(hour, minute)
}

/** 多个打卡时段的本地存储(SharedPreferences + JSON)。 */
object TriggerStore {
    private const val PREFS = "daka_triggers"
    private const val KEY = "list"

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): List<Trigger> {
        val raw = prefs(c).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Trigger(
                    id = o.optLong("id", i.toLong()),
                    hour = o.optInt("hour", 8),
                    minute = o.optInt("minute", 0),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(c: Context, list: List<Trigger>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("hour", t.hour)
                put("minute", t.minute)
                put("enabled", t.enabled)
            })
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(c: Context, hour: Int, minute: Int) {
        val list = load(c).toMutableList()
        list.add(Trigger(System.currentTimeMillis(), hour, minute, true))
        save(c, list.sortedWith(compareBy({ it.hour }, { it.minute })))
    }

    fun remove(c: Context, id: Long) {
        save(c, load(c).filterNot { it.id == id })
    }

    fun summary(c: Context): String {
        val list = load(c)
        if (list.isEmpty()) return "未设置打卡时段"
        return list.joinToString(", ") { it.label() + (if (it.enabled) "" else "(停)") }
    }
}
