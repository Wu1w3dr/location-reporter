package com.example.locationreporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // 恢复定位服务
            val serviceIntent = Intent(context, LocationForegroundService::class.java).apply {
                this.action = LocationForegroundService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Android 12+ 后台启动前台服务受限,需在用户交互后再启动
            }
            // 恢复打卡定时闹钟
            try {
                DakaScheduler.rescheduleAll(context.applicationContext)
            } catch (e: Exception) {
                // 忽略
            }
        }
    }
}
