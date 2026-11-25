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
import java.util.Locale

/** 高德 Web 服务逆地理编码:经纬度 -> 打卡地址。key 由用户在界面输入并本地缓存。 */
object AmapGeocoder {
    private const val TAG = "AmapGeocoder"
    private const val ENDPOINT = "https://restapi.amap.com/v3/geocode/regeo"

    suspend fun reverse(addressKey: String, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            if (addressKey.isBlank()) return@withContext null
            val location = String.format(Locale.US, "%.6f,%.6f", lng, lat)
            val url = ENDPOINT +
                    "?key=" + URLEncoder.encode(addressKey.trim(), "UTF-8") +
                    "&location=" + URLEncoder.encode(location, "UTF-8") +
                    "&poitype=&radius=1000&extensions=base&batch=false&roadlevel=0&output=JSON"
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 12_000
                conn.readTimeout = 12_000
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = if (stream == null) "" else {
                    BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
                }
                Log.d(TAG, "逆地理编码 HTTP $code: ${body.take(200)}")
                if (code !in 200..299) return@withContext null
                val root = JSONObject(body)
                if (root.optString("status") != "1") return@withContext null
                val addr = root.optJSONObject("regeocode")?.optString("formatted_address") ?: ""
                if (addr.isBlank()) null else addr
            } catch (e: Exception) {
                Log.e(TAG, "逆地理编码失败: ${e.message}", e)
                null
            } finally {
                conn?.disconnect()
            }
        }
}
