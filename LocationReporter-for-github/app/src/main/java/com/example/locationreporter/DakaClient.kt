package com.example.locationreporter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一次打卡的结果。success 表示请求发出且服务端未返回明确失败。 */
data class DakaResult(val success: Boolean, val detail: String)

/**
 * 特变打卡三步接口(Kotlin 原生移植,不含任何网络绕过):
 *   1) GET  openauth2/api/token          建立会话(返回 access_token,后续并不使用)
 *   2) POST mobileCloud/api/POST + url=/H5Standard/ValidateUserInfoByConfigure  取 SessionId
 *   3) POST mobileCloud/api/POST + source=/Attendance/SubmitEmployeeAttendanceDataByMAP  提交考勤
 * 所有 URL/header 参数来自原 Python 脚本,敏感值由 DakaConfig 提供。
 */
object DakaClient {
    private const val TAG = "DakaClient"
    private const val TOKEN_URL = "http://tbeaejia.gaiaworkforce.com:8020/openauth2/api/token"
    private const val API_URL = "https://h5tbearesource.gaiaworkforce.com/api/mobileCloud/api/POST"
    private const val UA = "Mozilla/5.0 (Linux; Android 16; 24031PN0DC Build/BP2A.250605.031.A3; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.192 Mobile Safari/537.36"

    suspend fun punch(cfg: DakaConfig.Snapshot, lng: String, lat: String, address: String): DakaResult = withContext(Dispatchers.IO) {
        try {
            // appid / secret 也由用户在设置页输入,不写死在代码里
            if (cfg.appid.isBlank() || cfg.secret.isBlank()) {
                return@withContext DakaResult(false, "未填写 token 接口的 appid 或 secret")
            }

            // ---------- 第 1 步:取 token(建立会话) ----------
            val tokenUrl = TOKEN_URL +
                    "?appid=" + enc(cfg.appid) +
                    "&secret=" + enc(cfg.secret) +
                    "&grant_type=client_credential"
            val tokenResp = httpGet(tokenUrl, commonHeaders() + mapOf("Accept" to "application/json"))
            Log.d(TAG, "token 返回 ${tokenResp.first}: ${tokenResp.second.take(200)}")

            // ---------- 第 2 步:验证用户,取 SessionId ----------
            val payload2 = JSONObject().apply {
                put("tokenkey", "PSNACCOUNT|EMPLOYEEID")
                put("tokenValue", cfg.userCode)
                put("encodeType", "0")
                put("lan", "zh-CN")
            }.toString()
            val headers2 = commonHeaders() + mapOf(
                "pragma" to "no-cache",
                "cache-control" to "no-cache",
                "companycode" to cfg.companyCode,
                "sec-ch-ua-platform" to "\"Android\"",
                "sec-ch-ua" to "\"Android WebView\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"",
                "sec-ch-ua-mobile" to "?1",
                "url" to "/H5Standard/ValidateUserInfoByConfigure",
                "content-type" to "application/json; charset=UTF-8",
                "origin" to "https://h5tbearesource.gaiaworkforce.com",
                "x-requested-with" to "com.tbea.kdweibo.client",
                "sec-fetch-site" to "same-origin",
                "sec-fetch-mode" to "cors",
                "sec-fetch-dest" to "empty",
                "referer" to "https://h5tbearesource.gaiaworkforce.com/?companyCode=${cfg.companyCode}&token=${cfg.userCode}",
                "accept-language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
                "priority" to "u=1, i"
            )
            val validateResp = httpPost(API_URL, payload2, headers2)
            val sessionId = try {
                JSONObject(validateResp.second).optJSONObject("data")?.optString("SessionId") ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "解析 SessionId 失败: ${e.message}")
                ""
            }
            if (sessionId.isBlank()) {
                return@withContext DakaResult(
                    false,
                    "取 SessionId 失败(HTTP ${validateResp.first}): ${validateResp.second.take(300)}"
                )
            }

            // ---------- 第 3 步:提交考勤 ----------
            val now = Date()
            val timeStamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(now)
            val serverTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)
            val payload3 = JSONObject().apply {
                put("Longitude", lng)
                put("Latitude", lat)
                put("Address", address)
                put("Remark", "")
                put("MachineID", "APP_GPS")
                put("timeStamp", timeStamp)
                put("sign", "")
                put("isNeedLocation", false)
                put("serverTime", serverTime)
                put("overTime", "120")
                put("needApprove", false)
                put("SessionId", sessionId)
                put("sessionId", sessionId)
                put("sessionID", sessionId)
            }.toString()
            val headers3 = commonHeaders() + mapOf(
                "pragma" to "no-cache",
                "cache-control" to "no-cache",
                "companycode" to cfg.companyCode,
                "interfaceshiftaddress" to "",
                "interfacecompanycode" to cfg.companyCode,
                "sec-ch-ua-platform" to "\"Android\"",
                "sec-ch-ua" to "\"Android WebView\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"",
                "sec-ch-ua-mobile" to "?1",
                "url" to "/Attendance/SubmitEmployeeAttendanceDataByMAP",
                "interfaceapiaddress" to cfg.iface,
                "interfacewfmflag" to "1",
                "content-type" to "application/json; charset=UTF-8",
                "interfacejavaurl" to "undefined",
                "origin" to "https://h5tbearesource.gaiaworkforce.com",
                "x-requested-with" to "com.tbea.kdweibo.client",
                "sec-fetch-site" to "same-origin",
                "sec-fetch-mode" to "cors",
                "sec-fetch-dest" to "empty",
                "referer" to "https://h5tbearesource.gaiaworkforce.com/?companyCode=${cfg.companyCode}&token=${cfg.userCode}",
                "accept-language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
                "priority" to "u=1, i"
            )
            val submitResp = httpPost(
                API_URL + "?source=/Attendance/SubmitEmployeeAttendanceDataByMAP",
                payload3, headers3
            )
            val ok = judge(submitResp.first, submitResp.second)
            Log.d(TAG, "提交返回 ${submitResp.first}: ${submitResp.second.take(500)}")
            DakaResult(ok.first, ok.second)
        } catch (e: Exception) {
            Log.e(TAG, "打卡异常: ${e.message}", e)
            DakaResult(false, "异常: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 成功/失败判据:先看 HTTP,再找响应里常见的失败关键字,最后看成功标志。 */
    private fun judge(code: Int, body: String): Pair<Boolean, String> {
        val head = "HTTP $code · ${body.take(500)}"
        if (code !in 200..299) return false to head
        val lower = body.lowercase(Locale.ROOT)
        val failWords = listOf(
            "失败", "错误", "异常", "不能", "无效", "不存在", "重复", "已打卡", "未注册", "超时",
            "\"success\":false", "\"success\":false", "\"code\":500", "\"code\":401", "\"code\":403",
            "\"resultcode\":1", "\"resultcode\":-"
        )
        if (failWords.any { lower.contains(it.lowercase(Locale.ROOT)) }) {
            return false to head
        }
        val okWords = listOf("成功", "\"success\":true", "\"code\":200", "\"code\":0", "\"result\":\"ok\"")
        if (okWords.any { lower.contains(it.lowercase(Locale.ROOT)) }) {
            return true to head
        }
        // 无明确关键字:HTTP 200 且能解析成 JSON 视为已受理
        val parsed = try { JSONObject(body); true } catch (e: Exception) { false }
        return parsed to head
    }

    private fun commonHeaders() = mapOf(
        "User-Agent" to UA,
        "Accept" to "application/json",
        "Accept-Language" to "zh-CN,zh;q=0.9"
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String, headers: Map<String, String>): Pair<Int, String> =
        http(url, "GET", null, headers)

    private fun httpPost(url: String, body: String, headers: Map<String, String>): Pair<Int, String> =
        http(url, "POST", body, headers)

    private fun http(url: String, method: String, body: String?, headers: Map<String, String>): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn!!.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Length", body.toByteArray(Charsets.UTF_8).size.toString())
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
            return code to text
        } finally {
            conn?.disconnect()
        }
    }
}
