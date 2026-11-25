package com.example.locationreporter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PushPlusSender {
    const val TAG = "PushPlusSender"

    // 推送标题。你给的 '今日基金收益情况' 看着是模板残留,这里改成位置相关;想换回去改这行即可
    const val TITLE = "手机实时位置上报"
    const val TEMPLATE = "html"

    private const val ENDPOINT = "https://www.pushplus.plus/send"
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 等价于你的 Python 函数:
     *   url = f"https://www.pushplus.plus/send?token={token}&title={title}&content={content}&template={template}"
     *   r = requests.get(url=url)
     *   print(r.text)
     *
     * 注意:Python requests 会自动对中文做 URL 编码,Kotlin 这里必须手动 URLEncoder.encode,
     * 否则 content 里的中文会乱码或被截断。
     */
    suspend fun sendWechat(token: String, msg: String): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "error: 未配置 token,请先在应用里输入"
        var connection: HttpURLConnection? = null
        try {
            val url = ENDPOINT +
                    "?token=" + URLEncoder.encode(token.trim(), "UTF-8") +
                    "&title=" + URLEncoder.encode(TITLE, "UTF-8") +
                    "&content=" + URLEncoder.encode(msg, "UTF-8") +
                    "&template=" + URLEncoder.encode(TEMPLATE, "UTF-8")

            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
            Log.d(TAG, "pushplus 返回 $code: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "pushplus 推送失败: ${e.message}", e)
            "error: ${e.message}"
        } finally {
            connection?.disconnect()
        }
    }

    /** template=html 时,content 直接按 HTML 渲染 */
    fun formatLocationMessage(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        provider: String?,
        speed: Float,
        timeMillis: Long,
        deviceId: String
    ): String {
        val amap = "https://uri.amap.com/marker?position=$longitude,$latitude&name=$deviceId"
        val time = timeFormat.format(Date(timeMillis))
        return """
            <div style="font-family:-apple-system,Segoe UI,Arial;font-size:15px">
              <h3 style="margin:0 0 10px">实时位置上报</h3>
              <p style="margin:4px 0"><b>设备:</b> $deviceId</p>
              <p style="margin:4px 0"><b>定位时间:</b> $time</p>
              <p style="margin:4px 0"><b>坐标:</b> $latitude, $longitude</p>
              <p style="margin:4px 0"><b>精度:</b> ${accuracy}m　<b>来源:</b> $provider</p>
              <p style="margin:4px 0"><b>速度:</b> ${(speed * 3.6).toInt()} km/h</p>
              <p style="margin:10px 0 0"><a href="$amap">在高德地图查看</a></p>
            </div>
        """.trimIndent()
    }
}
