package com.example.locationreporter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** 用 AlarmManager 为每个时段设置每天重复的精确闹钟。 */
object DakaScheduler {
    const val ACTION_DAKA = "com.example.locationreporter.DAKA_ALARM"
    const val EXTRA_TRIGGER_ID = "trigger_id"

    /** 重新注册全部时段:先全清,再按当前列表设置。 */
    fun rescheduleAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 先取消旧闹钟:用一个较大的 id 范围清理,避免残留
        for (i in 0..60) {
            am.cancel(pendingIntent(context, i.toLong()))
        }
        TriggerStore.load(context).filter { it.enabled }.forEachIndexed { index, t ->
            setDaily(am, context, t, index.toLong())
        }
    }

    private fun setDaily(am: AlarmManager, context: Context, t: Trigger, requestCode: Long) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, t.hour)
            set(Calendar.MINUTE, t.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val pi = pendingIntent(context, requestCode)
        // setExactAndAllowWhileIdle 在低电耗/待机下也能触发,适合打卡这种每天少数几次的任务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Long): PendingIntent {
        val intent = Intent(context, DakaAlarmReceiver::class.java).apply {
            action = ACTION_DAKA
            putExtra(EXTRA_TRIGGER_ID, requestCode)
        }
        val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        return PendingIntent.getBroadcast(context, requestCode.toInt(), intent, flags)
    }
}
