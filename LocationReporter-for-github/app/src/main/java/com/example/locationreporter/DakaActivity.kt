package com.example.locationreporter

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DakaActivity : AppCompatActivity() {

    private lateinit var userCode: EditText
    private lateinit var company: EditText
    private lateinit var iface: EditText
    private lateinit var appid: EditText
    private lateinit var secret: EditText
    private lateinit var address: EditText
    private lateinit var lng: EditText
    private lateinit var lat: EditText
    private lateinit var hourEdit: EditText
    private lateinit var minuteEdit: EditText
    private lateinit var triggerListText: TextView
    private lateinit var statusText: TextView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daka)

        userCode = findViewById(R.id.dakaUserCode)
        company = findViewById(R.id.dakaCompany)
        iface = findViewById(R.id.dakaIface)
        appid = findViewById(R.id.dakaAppid)
        secret = findViewById(R.id.dakaSecret)
        address = findViewById(R.id.dakaAddress)
        lng = findViewById(R.id.dakaLng)
        lat = findViewById(R.id.dakaLat)
        hourEdit = findViewById(R.id.hourEdit)
        minuteEdit = findViewById(R.id.minuteEdit)
        triggerListText = findViewById(R.id.triggerListText)
        statusText = findViewById(R.id.dakaStatusText)

        // 回填已保存配置
        userCode.setText(DakaConfig.getUserCode(this))
        company.setText(DakaConfig.getCompany(this))
        iface.setText(DakaConfig.getIface(this))
        appid.setText(DakaConfig.getAppid(this))
        secret.setText(DakaConfig.getSecret(this))
        address.setText(DakaConfig.getAddress(this))
        lng.setText(DakaConfig.getLng(this))
        lat.setText(DakaConfig.getLat(this))

        findViewById<Button>(R.id.dakaSaveButton).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.addTriggerButton).setOnClickListener { addTrigger() }
        findViewById<Button>(R.id.clearTriggerButton).setOnClickListener { clearTriggers() }
        triggerListText.setOnClickListener { showDeleteDialog() }
        findViewById<Button>(R.id.testDakaButton).setOnClickListener { runDaka() }
        findViewById<Button>(R.id.testDakaPushButton).setOnClickListener { testPushOnly() }

        refreshTriggers()
        refreshStatus()
    }

    private fun saveConfig() {
        DakaConfig.save(
            this, userCode.text.toString(), appid.text.toString(), secret.text.toString(),
            iface.text.toString(), company.text.toString(), address.text.toString(),
            lng.text.toString(), lat.text.toString()
        )
        Toast.makeText(this, "打卡参数已保存到本机", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun addTrigger() {
        val h = hourEdit.text.toString().toIntOrNull()
        val m = minuteEdit.text.toString().toIntOrNull()
        if (h == null || m == null || h !in 0..23 || m !in 0..59) {
            Toast.makeText(this, "请输入有效时间:时 0-23,分 0-59", Toast.LENGTH_SHORT).show()
            return
        }
        TriggerStore.add(this, h, m)
        DakaScheduler.rescheduleAll(this)
        hourEdit.text?.clear()
        minuteEdit.text?.clear()
        Toast.makeText(this, "已添加 ${"%02d:%02d".format(h, m)} 打卡时段", Toast.LENGTH_SHORT).show()
        refreshTriggers()
    }

    private fun showDeleteDialog() {
        val list = TriggerStore.load(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "还没有时段", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = list.map { "${it.label()} ${if (it.enabled) "" else "(停用)"}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("点选一个时段删除")
            .setItems(labels) { _, which ->
                TriggerStore.remove(this, list[which].id)
                DakaScheduler.rescheduleAll(this)
                refreshTriggers()
                Toast.makeText(this, "已删除 ${list[which].label()}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearTriggers() {
        if (TriggerStore.load(this).isEmpty()) {
            Toast.makeText(this, "时段已为空", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("清空全部打卡时段?")
            .setMessage("清空后将不再自动打卡")
            .setPositiveButton("清空") { _, _ ->
                TriggerStore.save(this, emptyList())
                DakaScheduler.rescheduleAll(this)
                refreshTriggers()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun runDaka() {
        if (!TokenStore.hasToken(this)) {
            Toast.makeText(this, "请先在首页保存 pushplus token", Toast.LENGTH_SHORT).show()
            return
        }
        if (!DakaConfig.isReady(this)) {
            Toast.makeText(this, "请先保存完整的打卡参数", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在打卡,请稍候...", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) { DakaRunner.runOnce(applicationContext) }
            val msg = if (result.success) "打卡成功,结果已推送微信" else "打卡失败:${result.detail.take(120)}"
            statusText.text = "最近测试:$msg"
            Toast.makeText(this@DakaActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun testPushOnly() {
        val token = TokenStore.getToken(this)
        if (token.isBlank()) {
            Toast.makeText(this, "请先在首页保存 pushplus token", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                PushPlusSender.sendWechat(
                    token,
                    "<h3>测试推送</h3><p>如果你在微信收到这条,说明打卡通知通道已连通。</p>",
                    title = "特变打卡情况"
                )
            }
            Toast.makeText(this@DakaActivity, "推送返回:$r", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshTriggers() {
        triggerListText.text = buildString {
            append("当前时段:" + TriggerStore.summary(this@DakaActivity) + "\n")
            append("(点此列表可删除单个时段)")
        }
    }

    private fun refreshStatus() {
        val ready = DakaConfig.isReady(this)
        statusText.text = buildString {
            append("打卡参数:${if (ready) "已配置" else "未配置完整"}\n")
            append("工号:${DakaConfig.getUserCode(this@DakaActivity).ifBlank { "未填" }}\n")
            append("地址:${DakaConfig.getAddress(this@DakaActivity).ifBlank { "未填" }}\n")
            append("token: 首页保存的 pushplus token 通用\n")
            append("定时时段:${TriggerStore.summary(this@DakaActivity)}")
        }
    }
}
