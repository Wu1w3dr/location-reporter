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
            return pushAndReturn(
                context, token, false,
                "打卡配置不完整,请先在设置页填写工号/接口地址/appid/secret/高德key",
                "未获取", "未获取", "未获取"
            )
        }
        if (!LocationProvider.hasPermission(context)) {
            return pushAndReturn(context, token, false, "未授予定位权限,无法获取当前经纬度", "未获取", "未获取", "未获取")
        }

        val fix = LocationProvider.current(context)
        if (fix == null) {
            return pushAndReturn(context, token, false, "手机定位失败(未开启定位或长时间未取到位置)", "未获取", "未获取", "未获取")
        }
        val lng = String.format(Locale.US, "%.6f", fix.lng)
        val lat = String.format(Locale.US, "%.6f", fix.lat)

        val cfg = DakaConfig.snapshot(context)
        val address = AmapGeocoder.reverse(cfg.amapKey, fix.lat, fix.lng)
        if (address == null) {
            return pushAndReturn(
                context, token, false,
                "高德逆地理编码失败,未取到打卡地址(检查高德 Web 服务 key 是否开通/配额是否用尽)",
                lng, lat, "$lng,$lat"
            )
        }

        val result = DakaClient.punch(cfg, lng, lat, address)
        return pushAndReturn(context, token, result.success, result.detail, lng, lat, address)
    }

    private suspend fun pushAndReturn(
        context: Context, token: String, success: Boolean,
        detail: String, lng: String, lat: String, address: String
    ): DakaResult {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val flag = if (success) "✅ 打卡成功" else "❌ 打卡失败"
        val cfg = DakaConfig.snapshot(context)
        val html = buildString {
            append("<div style=\"font-family:-apple-system,Segoe UI,Arial;font-size:15px\">")
            append("<h3 style=\"margin:0 0 8px\">特变打卡情况</h3>")
            append("<p style=\"margin:4px 0\"><b>$flag</b></p>")
            append("<p style=\"margin:4px 0\"><b>时间:</b> $now</p>")
            append("<p style=\"margin:4px 0\"><b>工号:</b> ${cfg.userCode}</p>")
            append("<p style=\"margin:4px 0\"><b>地址:</b> $address</p>")
            append("<p style=\"margin:4px 0\"><b>坐标:</b> $lng, $lat</p>")
            append("<p style=\"margin:8px 0 0;color:#666\">${detail.take(400)}</p>")
            append("</div>")
        }
        val pushResult = PushPlusSender.sendWechat(token, html, title = "特变打卡情况")
        Log.d(TAG, "结果推送回执: $pushResult")
        return DakaResult(success, detail)
    }
}
