package com.example.locationreporter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationForegroundService : Service() {

    companion object {
        const val TAG = "LocFgService"
        const val ACTION_START = "com.example.locationreporter.ACTION_START"
        const val ACTION_STOP = "com.example.locationreporter.ACTION_STOP"
        const val CHANNEL_ID = "location_reporter_channel"
        const val NOTIFICATION_ID = 1001

        // 位置采集间隔(毫秒)。小米上不建议低于 5000,否则耗电与限制概率都上升
        const val REPORT_INTERVAL_MS = 10_000L
        const val MIN_DISTANCE_M = 0f

        // 微信推送节流:pushplus 免费额度有限,且太高频率会刷屏。默认每 60 秒推一次,
        // 需要更实时就调小(如 30_000),但注意额度消耗。
        const val PUSH_INTERVAL_MS = 60_000L

        const val DEVICE_ID = "MI14_001"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reportJob: Job? = null
    private var lastLocation: Location? = null
    private var lastPushTime = 0L
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            Log.d(TAG, "定位: ${location.latitude}, ${location.longitude} 精度=${location.accuracy}m")
            enqueueReport(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocationReporter::wakelock")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        startLocationUpdates()
        startPeriodicReporter()

        // 被系统杀掉后自动重启;配合 BOOT_COMPLETED 与用户"自启动"设置实现长期存活
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台定位服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "锁屏后持续上报实时位置"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopPi = PendingIntent.getService(this, 1, stopIntent, flags)

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPi = PendingIntent.getActivity(this, 2, openIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("位置共享中")
            .setContentText("正在后台采集并微信推送位置")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPi)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompatCheck.fineLocation(this)) {
            // 同时注册 GPS 与网络 provider:室内/锁屏下网络定位更快,户外 GPS 更准
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            providers.forEach { provider ->
                if (locationManager.isProviderEnabled(provider)) {
                    try {
                        locationManager.requestLocationUpdates(
                            provider,
                            REPORT_INTERVAL_MS,
                            MIN_DISTANCE_M,
                            locationListener,
                            Looper.getMainLooper()
                        )
                        Log.d(TAG, "已注册定位监听: $provider")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "注册定位监听失败: $provider", e)
                    }
                }
            }
        }
    }

    private fun startPeriodicReporter() {
        reportJob?.cancel()
        reportJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(REPORT_INTERVAL_MS)
                lastLocation?.let { enqueueReport(it) }
            }
        }
    }

    private fun enqueueReport(location: Location) {
        val now = System.currentTimeMillis()
        // 节流:距离上次微信推送不足 PUSH_INTERVAL_MS 就跳过,避免刷屏和额度耗尽
        if (now - lastPushTime < PUSH_INTERVAL_MS) return
        lastPushTime = now
        scope.launch {
            pushLocationByWechat(location)
        }
    }

    private suspend fun pushLocationByWechat(location: Location) = withContext(Dispatchers.IO) {
        val token = TokenStore.getToken(this@LocationForegroundService)
        if (token.isEmpty()) {
            Log.w(TAG, "未配置 pushplus token,跳过本次推送")
            return@withContext
        }
        val msg = PushPlusSender.formatLocationMessage(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            provider = location.provider,
            speed = location.speed,
            timeMillis = location.time,
            deviceId = DEVICE_ID
        )
        val result = PushPlusSender.sendWechat(token, msg)
        Log.d(TAG, "微信推送结果: $result")
    }

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            // 设置超时防止异常退出导致 CPU 长期无法休眠
            wakeLock.acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onDestroy() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            Log.e(TAG, "移除定位监听失败", e)
        }
        reportJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object ContextCompatCheck {
    fun fineLocation(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
