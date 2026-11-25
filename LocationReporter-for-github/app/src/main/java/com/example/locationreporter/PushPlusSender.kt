package com.example.locationreporter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PushPlusSender {
    const val TAG = "PushPlusSender"

    const val TITLE = "特变打卡情况"
    const val TEMPLATE = "html"

    private const val ENDPOINT = "https://www.pushplus.plus/send"

    /**
     * 等价于你的 Python 函数:
     *   url = f"https://www.pushplus.plus/send?token={token}&title={title}&content={content}&template={template}"
     *   r = requests.get(url=url)
     *   print(r.text)
     *
     * 注意:Python requests 会自动对中文做 URL 编码,Kotlin 这里必须手动 URLEncoder.encode,
     * 否则 content 里的中文会乱码或被截断。
     */
    suspend fun sendWechat(token: String, msg: String, title: String = TITLE): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "error: 未配置 token,请先在应用里输入"
        var connection: HttpURLConnection? = null
        try {
            val url = ENDPOINT +
                    "?token=" + URLEncoder.encode(token.trim(), "UTF-8") +
                    "&title=" + URLEncoder.encode(title, "UTF-8") +
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

}
