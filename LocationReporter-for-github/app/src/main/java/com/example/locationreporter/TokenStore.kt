package com.example.locationreporter

import android.content.Context

/** 把 pushplus token 存在应用私有 SharedPreferences,输入一次后永久可用 */
object TokenStore {
    private const val PREFS_NAME = "location_reporter_prefs"
    private const val KEY_TOKEN = "pushplus_token"

    fun getToken(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, "")?.trim() ?: ""
    }

    fun saveToken(context: Context, token: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun hasToken(context: Context): Boolean = getToken(context).isNotEmpty()

    /** 状态栏展示用,只露前 8 位,避免明文 token 直接显示在界面上 */
    fun maskedToken(context: Context): String {
        val token = getToken(context)
        return when {
            token.isEmpty() -> "未配置"
            token.length <= 8 -> token
            else -> "${token.take(8)}...${token.takeLast(4)}"
        }
    }
}
