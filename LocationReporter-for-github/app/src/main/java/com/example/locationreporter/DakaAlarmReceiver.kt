package com.example.locationreporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 闹钟到点触发:异步执行一次打卡并推送结果,同时把该时段排到明天。 */
class DakaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Log.d("DakaAlarm", "定时打卡触发")
                DakaRunner.runOnce(context.applicationContext)
                DakaScheduler.rescheduleAll(context.applicationContext)
            } catch (e: Exception) {
                Log.e("DakaAlarm", "定时打卡异常: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
