package com.smartbridge.tunnel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.Socket
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var exitIpText: TextView
    private lateinit var proxyHint: TextView
    private lateinit var trialBadgeText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var serverList: RecyclerView
    private lateinit var adapter: ServerAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val httpPort = 13128

    // 隧道实例引用（用于停止时清理）
    private var sshBridge: SshBridge? = null
    private var httpProxy: HttpProxy? = null

    companion object {
        private const val TAG = "SmartBridge"
        var tunnelRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)
        exitIpText = findViewById(R.id.exitIpText)
        proxyHint = findViewById(R.id.proxyHint)
        trialBadgeText = findViewById(R.id.trialBadgeText)
        progressBar = findViewById(R.id.progressBar)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        serverList = findViewById(R.id.serverList)

        // 授权状态检查
        checkLicenseStatus()

        adapter = ServerAdapter(this, ServerList.servers) { pos ->
            getSharedPreferences("bridge", MODE_PRIVATE)
                .edit().putInt("server_idx", pos).apply()
        }

        val saved = getSharedPreferences("bridge", MODE_PRIVATE)
            .getInt("server_idx", 0)
        adapter.setSelected(saved)

        serverList.layoutManager = LinearLayoutManager(this)
        serverList.adapter = adapter

        updateLocalIP()

        btnStart.setOnClickListener { startTunnel() }
        btnStop.setOnClickListener { stopTunnel() }

        createNotificationChannel()

        if (tunnelRunning) {
            statusText.text = "● 运行中"
            statusText.setTextColor(Color.parseColor("#34A853"))
            btnStart.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
        }
    }

    /**
     * 检查授权状态：已激活 / 试用中 / 试用过期
     * 如果过期且未激活，跳回 LicenseActivity
     */
    private fun checkLicenseStatus() {
        val activated = LicenseManager.isActivated(this)
        if (activated) {
            trialBadgeText.text = "✅ 已激活"
            trialBadgeText.setTextColor(Color.parseColor("#34A853"))
            return
        }

        // 未激活，检查试用
        if (TrialManager.isTrialValid(this)) {
            val remaining = TrialManager.getRemainingHuman(this)
            trialBadgeText.text = "⏳ 试用剩余: $remaining"
            trialBadgeText.setTextColor(Color.parseColor("#FF9800"))
        } else {
            // 试用过期，跳回激活页
            Toast.makeText(this, "试用期已结束，请激活", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LicenseActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun updateLocalIP() {
        try {
            val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
            ipText.text = "本机 IP: $ip"
            proxyHint.text = "Wi‑Fi 代理 → $ip:$httpPort"
        } catch (_: Exception) {
            ipText.text = "本机 IP: --"
        }
    }

    private fun startTunnel() {
        // 二次检查授权
        if (!LicenseManager.isActivated(this) && TrialManager.isTrialExpired(this)) {
            Toast.makeText(this, "试用期已结束，请激活后使用", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LicenseActivity::class.java))
            return
        }

        val server = adapter.getSelected()
        Log.i(TAG, "启动隧道: ${server.flag} ${server.city} ${server.host}:${server.port}")

        // 如果已有隧道在运行，先彻底停止
        if (tunnelRunning) {
            Log.i(TAG, "已有隧道运行中，先停止...")
            cleanupTunnelSync()
        }

        btnStart.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        statusText.text = "连接中…"
        statusText.setTextColor(Color.parseColor("#FF9800"))

        SecureStore.save(this, "pw_${server.host}", server.password)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Log.i(TAG, "开始 SSH 连接 ${server.host}:${server.port} user=${server.user}")

                    // 1. SSH 连接
                    val ssh = SshBridge(
                        host = server.host,
                        port = server.port,
                        user = server.user,
                        password = server.password
                    )
                    ssh.connect()
                    Log.i(TAG, "SSH 连接成功")
                    sshBridge = ssh

                    // 2. HTTP 代理 (直接通过 SSH direct-tcpip)
                    val http = HttpProxy(
                        sshSession = ssh.getSession()!!,
                        bindPort = httpPort,
                        bindHost = "0.0.0.0"
                    )
                    runBlocking { http.start() }
                    Log.i(TAG, "HTTP 代理启动成功: $httpPort")
                    httpProxy = http

                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "隧道启动失败", e)
                    try { sshBridge?.disconnect() } catch (_: Exception) {}
                    try { httpProxy?.stop() } catch (_: Exception) {}
                    sshBridge = null
                    httpProxy = null
                    Result.failure(e)
                }
            }

            progressBar.visibility = View.GONE

            if (result.isSuccess) {
                tunnelRunning = true
                statusText.text = "● 运行中"
                statusText.setTextColor(Color.parseColor("#34A853"))
                exitIpText.text = "出口 IP: 检测中…"

                // 启动前台服务保活
                val intent = Intent(this@MainActivity, TunnelService::class.java).apply {
                    putExtra("host", server.host)
                    putExtra("port", server.port)
                    putExtra("user", server.user)
                    putExtra("password", server.password)
                    putExtra("http_port", httpPort)
                    putExtra("city", server.city)
                    putExtra("flag", server.flag)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                // 检测出口 IP
                scope.launch(Dispatchers.IO) {
                    val ip = detectExitIP()
                    withContext(Dispatchers.Main) {
                        exitIpText.text = "出口 IP: $ip"
                        exitIpText.setTextColor(Color.parseColor("#34A853"))
                    }
                }

                Toast.makeText(this@MainActivity,
                    "🚀 ${server.flag} ${server.city} 已连接", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "✕ 连接失败"
                statusText.setTextColor(Color.parseColor("#EA4335"))
                btnStart.visibility = View.VISIBLE
                btnStop.visibility = View.GONE
                val err = result.exceptionOrNull()?.message ?: "未知错误"
                Log.e(TAG, "连接失败: $err")
                Toast.makeText(this@MainActivity,
                    "连接失败: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun detectExitIP(): String {
        return try {
            Log.i(TAG, "检测出口 IP (SSH)...")
            val server = adapter.getSelected()
            val jsch = com.jcraft.jsch.JSch()
            val session = jsch.getSession(server.user, server.host, server.port)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setPassword(server.password)
            session.connect(10_000)
            
            val ch = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
            ch.setCommand("curl -s ifconfig.me 2>/dev/null || curl -s ip-api.com/line/?fields=query 2>/dev/null || hostname -I | awk '{print \$1}'")
            ch.connect(10_000)
            
            val output = ch.inputStream.bufferedReader().readText().trim()
            ch.disconnect()
            session.disconnect()
            
            Log.i(TAG, "出口 IP (SSH): $output")
            if (output.isNotEmpty()) output else "未知"
        } catch (e: Exception) {
            Log.e(TAG, "出口 IP 检测失败 (SSH)", e)
            "检测失败"
        }
    }

    /**
     * 同步清理隧道资源（修复端口残留 bug）
     * 在调用方线程阻塞等待清理完成
     */
    private fun cleanupTunnelSync() {
        Log.i(TAG, "清理隧道资源 (同步)...")
        
        // 先停止 HTTP 代理（关闭 ServerSocket 释放端口）
        try {
            Log.i(TAG, "停止 HTTP 代理...")
            httpProxy?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP 代理停止失败", e)
        }
        httpProxy = null

        // 再断开 SSH（关闭所有 channel）
        try {
            Log.i(TAG, "断开 SSH...")
            sshBridge?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "SSH 断开失败", e)
        }
        sshBridge = null

        // 确保端口释放
        try {
            val socket = java.net.ServerSocket(httpPort)
            socket.close()
            Log.i(TAG, "端口 $httpPort 已释放")
        } catch (e: Exception) {
            Log.w(TAG, "端口 $httpPort 仍被占用: ${e.message}")
            // 强制等待一下再重试
            Thread.sleep(500)
            try {
                val socket = java.net.ServerSocket(httpPort)
                socket.close()
                Log.i(TAG, "端口 $httpPort 第二次尝试已释放")
            } catch (_: Exception) {
                Log.e(TAG, "端口 $httpPort 无法释放")
            }
        }

        Log.i(TAG, "隧道资源已清理")
    }

    /**
     * 异步清理（用于 onDestroy）
     */
    private fun cleanupTunnelAsync() {
        Thread {
            try { httpProxy?.stop() } catch (_: Exception) {}
            try { sshBridge?.disconnect() } catch (_: Exception) {}
            sshBridge = null
            httpProxy = null
        }.start()
    }

    private fun stopTunnel() {
        Log.i(TAG, "停止隧道")
        
        // 同步清理，确保端口释放
        cleanupTunnelSync()
        
        stopService(Intent(this, TunnelService::class.java))
        tunnelRunning = false
        statusText.text = "○ 已停止"
        statusText.setTextColor(Color.parseColor("#888888"))
        exitIpText.text = "出口 IP: --"
        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bridge_tunnel", "网桥隧道",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "SSH 隧道前台服务" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onResume() {
        super.onResume()
        updateLocalIP()
        // 每次返回页面时刷新授权状态
        checkLicenseStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupTunnelAsync()
        scope.cancel()
    }
}
