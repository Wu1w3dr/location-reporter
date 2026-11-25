package com.example.locationreporter

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 执行一次完整打卡并把结果推送微信。定时闹钟和"测试打卡"按钮共用此入口。 */
object DakaRunner {
    private const val TAG = "DakaRunner"

    suspend fun runOnce(context: Context): DakaResult {
        val token = TokenStore.getToken(context)
        if (token.isBlank()) {
            return DakaResult(false, "未配置 pushplus token,无法推送结果")
        }
        if (!DakaConfig.isReady(context)) {
            return DakaResult(false, "打卡配置不完整,请先在设置页填写工号/接口地址/地址/经纬度")
        }

        val result = DakaClient.punch(DakaConfig.snapshot(context))
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val flag = if (result.success) "✅ 打卡成功" else "❌ 打卡失败"
        val cfg = DakaConfig.snapshot(context)
        val html = buildString {
            append("<div style=\"font-family:-apple-system,Segoe UI,Arial;font-size:15px\">")
            append("<h3 style=\"margin:0 0 8px\">特变打卡情况</h3>")
            append("<p style=\"margin:4px 0\"><b>$flag</b></p>")
            append("<p style=\"margin:4px 0\"><b>时间:</b> $now</p>")
            append("<p style=\"margin:4px 0\"><b>工号:</b> ${cfg.userCode}</p>")
            append("<p style=\"margin:4px 0\"><b>地址:</b> ${cfg.address}</p>")
            append("<p style=\"margin:4px 0\"><b>坐标:</b> ${cfg.lng}, ${cfg.lat}</p>")
            append("<p style=\"margin:8px 0 0;color:#666\">${result.detail}</p>")
            append("</div>")
        }
        val pushResult = PushPlusSender.sendWechat(token, html, title = "特变打卡情况")
        Log.d(TAG, "结果推送回执: $pushResult")
        return result
    }
}
