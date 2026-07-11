package com.smartbridge.tunnel

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import android.util.Log

/**
 * SSH 隧道：JSch 建立 SSH 连接
 * 暴露 Session 供 HttpProxy 使用 direct-tcpip channel
 */
class SshBridge(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String,
    private val localSocksPort: Int = 0  // 不再使用 SOCKS5，保留参数兼容
) {
    private var session: Session? = null

    fun connect() {
        val jsch = JSch()
        val s = jsch.getSession(user, host, port)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setPassword(password)
        s.connect(15_000)

        // 不再创建 SOCKS5 动态转发，直接用 direct-tcpip
        Log.i("SmartBridge", "SSH 连接成功: ${s.host}:${s.port} (direct-tcpip 模式)")

        session = s
    }

    fun getSession(): Session? = session

    fun disconnect() {
        try { session?.disconnect() } catch (_: Exception) {}
        session = null
    }

    fun isConnected(): Boolean = session?.isConnected == true
}
