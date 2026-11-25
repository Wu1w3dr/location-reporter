package com.example.locationreporter

import android.content.Context

/**
 * 打卡所需的全部配置(含敏感项)。
 * 全部由用户在界面输入一次,之后存应用私有 SharedPreferences,不再写死在代码里。
 */
object DakaConfig {
    private const val PREFS = "daka_config"
    private const val K_USER = "user_code"
    private const val K_APPID = "appid"
    private const val K_SECRET = "secret"
    private const val K_IFACE = "interface_api_address"
    private const val K_COMPANY = "company_code"
    private const val K_ADDRESS = "address"
    private const val K_LNG = "lng"
    private const val K_LAT = "lat"

    data class Snapshot(
        val userCode: String,
        val appid: String,
        val secret: String,
        val iface: String,
        val companyCode: String,
        val address: String,
        val lng: String,
        val lat: String
    )

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getUserCode(c: Context) = prefs(c).getString(K_USER, "") ?: ""
    fun getAppid(c: Context) = prefs(c).getString(K_APPID, "") ?: ""
    fun getSecret(c: Context) = prefs(c).getString(K_SECRET, "") ?: ""
    fun getIface(c: Context) = prefs(c).getString(K_IFACE, "") ?: ""
    fun getCompany(c: Context) = prefs(c).getString(K_COMPANY, "") ?: ""
    fun getAddress(c: Context) = prefs(c).getString(K_ADDRESS, "") ?: ""
    fun getLng(c: Context) = prefs(c).getString(K_LNG, "") ?: ""
    fun getLat(c: Context) = prefs(c).getString(K_LAT, "") ?: ""

    fun save(
        c: Context, userCode: String, appid: String, secret: String,
        iface: String, company: String, address: String, lng: String, lat: String
    ) {
        prefs(c).edit()
            .putString(K_USER, userCode.trim())
            .putString(K_APPID, appid.trim())
            .putString(K_SECRET, secret.trim())
            .putString(K_IFACE, iface.trim())
            .putString(K_COMPANY, company.trim())
            .putString(K_ADDRESS, address.trim())
            .putString(K_LNG, lng.trim())
            .putString(K_LAT, lat.trim())
            .apply()
    }

    fun snapshot(c: Context) = Snapshot(
        getUserCode(c), getAppid(c), getSecret(c), getIface(c),
        getCompany(c), getAddress(c), getLng(c), getLat(c)
    )

    /** 打卡所需的全部参数都必须由用户填好,代码里不保留任何默认值。 */
    fun isReady(c: Context): Boolean {
        val s = snapshot(c)
        return s.userCode.isNotBlank() && s.iface.isNotBlank() &&
                s.companyCode.isNotBlank() && s.appid.isNotBlank() && s.secret.isNotBlank() &&
                s.address.isNotBlank() && s.lng.isNotBlank() && s.lat.isNotBlank()
    }

    fun maskedSecret(c: Context): String {
        val s = getSecret(c)
        return when {
            s.isEmpty() -> "未配置"
            s.length <= 6 -> "******"
            else -> "${s.take(3)}****${s.takeLast(3)}"
        }
    }
}
