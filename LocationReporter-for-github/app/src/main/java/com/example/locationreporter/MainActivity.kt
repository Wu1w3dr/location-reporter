package com.example.locationreporter

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var tokenEdit: EditText
    private lateinit var saveTokenButton: Button
    private lateinit var startButton: Button
    private lateinit var batteryButton: Button
    private lateinit var autostartButton: Button
    private lateinit var testButton: Button

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val REQ_LOCATION = 1001
        const val REQ_NOTIFICATION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        tokenEdit = findViewById(R.id.tokenEdit)
        saveTokenButton = findViewById(R.id.saveTokenButton)
        startButton = findViewById(R.id.startButton)
        batteryButton = findViewById(R.id.batteryButton)
        autostartButton = findViewById(R.id.autostartButton)
        testButton = findViewById(R.id.testButton)

        // 回填已保存的 token;输入一次后重启 App 依然在
        val savedToken = TokenStore.getToken(this)
        tokenEdit.setText(savedToken)

        saveTokenButton.setOnClickListener {
            val token = tokenEdit.text.toString().trim()
            if (token.isEmpty()) {
                Toast.makeText(this, "请先填入 pushplus token", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            TokenStore.saveToken(this, token)
            Toast.makeText(this, "token 已保存到本地,后续无需再输入", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        startButton.setOnClickListener { startTracking() }
        batteryButton.setOnClickListener { requestIgnoreBattery() }
        autostartButton.setOnClickListener { openAutostartSettings() }
        testButton.setOnClickListener { testPush() }

        requestPermissionsStepByStep()
        refreshStatus()
    }

    private fun requestPermissionsStepByStep() {
        val needed = mutableListOf<String>()
        needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQ_LOCATION)
        } else {
            requestBackgroundLocation()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 不允许直接弹窗申请后台位置,必须跳到系统设置让用户选「始终允许」
            Toast.makeText(this, "请在此页选择「始终允许」以启用后台定位", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "请手动到 设置-应用-权限-位置 选择始终允许", Toast.LENGTH_LONG).show()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_NOTIFICATION
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "请先授予定位权限", Toast.LENGTH_SHORT).show()
            requestPermissionsStepByStep()
            return
        }

        if (!TokenStore.hasToken(this)) {
            Toast.makeText(this, "请先输入并保存 pushplus token", Toast.LENGTH_SHORT).show()
            tokenEdit.requestFocus()
            return
        }

        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "已启动后台定位", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun requestIgnoreBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    openAppDetails()
                }
            } else {
                Toast.makeText(this, "已忽略电池优化", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAutostartSettings() {
        // 小米/HyperOS 自启动管理页
        val miuiAutostart = Intent("miui.intent.action.APPLICATION_PREFERENCES").apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        try {
            startActivity(miuiAutostart)
        } catch (e: Exception) {
            // 兜底:跳转应用详情页
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_LOCATION -> requestBackgroundLocation()
            REQ_NOTIFICATION -> refreshStatus()
        }
    }

    private fun refreshStatus() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            fine
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        statusText.text = buildString {
            append("精确位置: ${if (fine) "已授予" else "未授予"}\n")
            append("后台位置: ${if (background) "已授予" else "未授予"}\n")
            append("电池优化白名单: ${if (batteryIgnored) "已加入" else "未加入"}\n")
            append("定位采集间隔: ${LocationForegroundService.REPORT_INTERVAL_MS / 1000} 秒\n")
            append("微信推送间隔: ${LocationForegroundService.PUSH_INTERVAL_MS / 1000} 秒\n")
            append("推送渠道: pushplus (${PushPlusSender.TITLE})\n")
            append("token: ${TokenStore.maskedToken(this@MainActivity)}")
        }
    }

    private fun testPush() {
        val token = TokenStore.getToken(this)
        if (token.isEmpty()) {
            Toast.makeText(this, "请先输入并保存 pushplus token", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在发送测试推送...", Toast.LENGTH_SHORT).show()
        uiScope.launch {
            val result = withContext(Dispatchers.IO) {
                PushPlusSender.sendWechat(
                    token,
                    "<h3>测试推送</h3><p>如果你在微信收到这条,说明推送通道已连通。</p>" +
                            "<p>token: ${token.take(8)}...</p>"
                )
            }
            Toast.makeText(this@MainActivity, "推送返回: $result", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }
}
