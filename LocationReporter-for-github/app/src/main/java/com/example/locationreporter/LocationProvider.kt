package com.example.locationreporter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 用手机自身定位拿经纬度:优先取短时间内缓存的 GPS/网络位置,
 * 缓存过期时请求一次新定位并等待最多 15 秒,拿到后立刻注销监听。
 * 不常驻定位,不做任何位置上报。
 */
object LocationProvider {

    data class Fix(
        val lat: Double,
        val lng: Double,
        val accuracy: Float,
        val provider: String,
        val ageMs: Long
    ) {
        fun display(): String = String.format(
            Locale.US, "%.6f, %.6f (%s, ±%.0fm)", lng, lat, provider, accuracy
        )
    }

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun current(context: Context): Fix? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null

        val providers = mutableListOf<String>()
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) return@withContext null

        val now = System.currentTimeMillis()
        val cached = bestLastKnown(lm, providers)
        if (cached != null && now - cached.time <= 2 * 60 * 1000L) {
            return@withContext cached.toFix(now)
        }

        // 缓存过期:注册一次性监听,轮询最多 15 秒等新位置,结束后立即注销
        val latest = AtomicReference<Location>(null)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                latest.set(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }

            override fun onProviderEnabled(provider: String) { }

            override fun onProviderDisabled(provider: String) { }
        }

        var requested = false
        providers.forEach { p ->
            try {
                lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                requested = true
            } catch (e: SecurityException) { }
        }

        try {
            if (requested) {
                val deadline = System.currentTimeMillis() + 15_000L
                while (System.currentTimeMillis() < deadline) {
                    latest.get()?.let { return@withContext it.toFix(System.currentTimeMillis()) }
                    delay(400)
                }
            }
            // 超时再看一次系统缓存,总比没有坐标强
            bestLastKnown(lm, providers)?.let { return@withContext it.toFix(System.currentTimeMillis()) }
            null
        } finally {
            try { lm.removeUpdates(listener) } catch (e: Exception) { }
        }
    }

    private fun bestLastKnown(lm: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { p ->
            try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
        }.filter { it.time > 0 }.maxByOrNull { it.time }

    private fun Location.toFix(now: Long) = Fix(
        lat = latitude,
        lng = longitude,
        accuracy = accuracy,
        provider = provider ?: "unknown",
        ageMs = (now - time).coerceAtLeast(0L)
    )
}
