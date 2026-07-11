package com.smartbridge.tunnel

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class TunnelService : Service() {

    private var sshBridge: SshBridge? = null
    private var httpProxy: HttpProxy? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val host = intent.getStringExtra("host") ?: return START_NOT_STICKY
        val port = intent.getIntExtra("port", 22)
        val user = intent.getStringExtra("user") ?: "root"
        val password = intent.getStringExtra("password") ?: return START_NOT_STICKY
        val httpPort = intent.getIntExtra("http_port", 13128)
        val city = intent.getStringExtra("city") ?: ""
        val flag = intent.getStringExtra("flag") ?: ""

        // 前台通知
        val notif = NotificationCompat.Builder(this, "bridge_tunnel")
            .setContentTitle("$flag $city · 网桥运行中")
            .setContentText("HTTP 代理 :$httpPort")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notif)

        // WakeLock 防止休眠断连
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bridge::Tunnel")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h

        // 启动隧道（服务端备份连接，保持前台保活）
        Thread {
            try {
                sshBridge = SshBridge(host, port, user, password)
                sshBridge?.connect()

                val session = sshBridge?.getSession() ?: return@Thread
                httpProxy = HttpProxy(session, httpPort, "0.0.0.0")
                kotlinx.coroutines.runBlocking { httpProxy?.start() }
            } catch (_: Exception) {}
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { httpProxy?.stop() } catch (_: Exception) {}
        try { sshBridge?.disconnect() } catch (_: Exception) {}
        wakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
