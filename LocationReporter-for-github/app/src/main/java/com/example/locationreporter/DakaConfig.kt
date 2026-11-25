package com.example.locationreporter

import android.content.Context

/**
 * 打卡所需的固定配置(敏感项)。
 * 经纬度由手机定位实时获取,打卡地址由高德逆地理编码实时获取,不再手工填写,也不写入代码。
 * 全部由用户在界面输入一次,之后存应用私有 SharedPreferences。
 */
object DakaConfig {
    private const val PREFS = "daka_config"
    private const val K_USER = "user_code"
    private const val K_APPID = "appid"
    private const val K_SECRET = "secret"
    private const val K_IFACE = "interface_api_address"
    private const val K_COMPANY = "company_code"
    private const val K_AMAP_KEY = "amap_web_key"

    data class Snapshot(
        val userCode: String,
        val appid: String,
        val secret: String,
        val iface: String,
        val companyCode: String,
        val amapKey: String
    )

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getUserCode(c: Context) = prefs(c).getString(K_USER, "") ?: ""
    fun getAppid(c: Context) = prefs(c).getString(K_APPID, "") ?: ""
    fun getSecret(c: Context) = prefs(c).getString(K_SECRET, "") ?: ""
    fun getIface(c: Context) = prefs(c).getString(K_IFACE, "") ?: ""
    fun getCompany(c: Context) = prefs(c).getString(K_COMPANY, "") ?: ""
    fun getAmapKey(c: Context) = prefs(c).getString(K_AMAP_KEY, "") ?: ""

    fun save(
        c: Context, userCode: String, appid: String, secret: String,
        iface: String, company: String, amapKey: String
    ) {
        prefs(c).edit()
            .putString(K_USER, userCode.trim())
            .putString(K_APPID, appid.trim())
            .putString(K_SECRET, secret.trim())
            .putString(K_IFACE, iface.trim())
            .putString(K_COMPANY, company.trim())
            .putString(K_AMAP_KEY, amapKey.trim())
            .apply()
    }

    fun snapshot(c: Context) = Snapshot(
        getUserCode(c), getAppid(c), getSecret(c), getIface(c), getCompany(c), getAmapKey(c)
    )

    /** 打卡所需的固定参数都必须填好,代码里不保留任何默认值。 */
    fun isReady(c: Context): Boolean {
        val s = snapshot(c)
        return s.userCode.isNotBlank() && s.iface.isNotBlank() &&
                s.companyCode.isNotBlank() && s.appid.isNotBlank() &&
                s.secret.isNotBlank() && s.amapKey.isNotBlank()
    }

    fun maskedSecret(c: Context): String {
        val s = getSecret(c)
        return when {
            s.isEmpty() -> "未配置"
            s.length <= 6 -> "******"
            else -> "${s.take(3)}****${s.takeLast(3)}"
        }
    }

    fun maskedAmapKey(c: Context): String {
        val s = getAmapKey(c)
        return when {
            s.isEmpty() -> "未配置"
            s.length <= 8 -> s
            else -> "${s.take(6)}...${s.takeLast(4)}"
        }
    }
}
