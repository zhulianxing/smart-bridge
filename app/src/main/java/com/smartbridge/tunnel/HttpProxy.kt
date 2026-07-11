package com.smartbridge.tunnel

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.*

/**
 * HTTP/HTTPS 代理 — 直接通过 SSH direct-tcpip channel 转发
 *
 * 不经过 SOCKS5 中间层，每个连接直接创建 SSH channel 连接远程目标。
 */
class HttpProxy(
    private val sshSession: Session,
    private val bindPort: Int,
    private val bindHost: String = "0.0.0.0"
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var onStatus: ((String) -> Unit)? = null

    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(bindPort, 50, java.net.InetAddress.getByName(bindHost))
            onStatus?.invoke("HTTP 代理 $bindHost:$bindPort 就绪 (SSH direct-tcpip)")
            scope.launch { acceptLoop() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun acceptLoop() {
        while (scope.isActive) {
            val client = try { serverSocket?.accept() } catch (_: Exception) { null } ?: break
            scope.launch { handleClient(client) }
        }
    }

    private suspend fun handleClient(client: Socket) {
        try {
            client.soTimeout = 60_000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // 读取请求行
            val requestLine = readLine(input) ?: run { client.close(); return }
            val parts = requestLine.split(" ")
            if (parts.size < 2) { client.close(); return }

            val method = parts[0]
            val target = parts[1]

            // 读取所有 headers
            val headers = mutableListOf<String>()
            var line = readLine(input)
            while (line != null && line.isNotEmpty()) {
                headers.add(line)
                line = readLine(input)
            }

            when (method) {
                "CONNECT" -> handleConnect(client, target, input, output)
                else -> handleHttp(client, target, method, headers, input, output)
            }
        } catch (e: Exception) {
            Log.w("SmartBridge", "代理连接异常: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * CONNECT host:port → SSH direct-tcpip channel
     * 先回复 200，再用独立线程双向桥接 client ↔ channel
     */
    private suspend fun handleConnect(
        client: Socket, target: String,
        input: InputStream, output: OutputStream
    ) = withContext(Dispatchers.IO) {
        val parts = target.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 443

        try {
            // 先创建 channel 并连接远程
            val channel = sshSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(host)
            channel.setPort(port)
            // 不让 JSch 接管 client 的流，我们自己管理
            channel.connect(15_000)

            if (!channel.isConnected) {
                throw Exception("channel not connected")
            }

            // 回复客户端 200
            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            output.flush()

            // 获取 channel 的输入输出流
            val remoteIn = channel.getInputStream()
            val remoteOut = channel.getOutputStream()

            // 双向桥接
            val t1 = Thread {
                try {
                    val buf = ByteArray(16_384)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        remoteOut.write(buf, 0, n)
                        remoteOut.flush()
                    }
                } catch (_: Exception) {}
                try { channel.disconnect() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            val t2 = Thread {
                try {
                    val buf = ByteArray(16_384)
                    while (true) {
                        val n = remoteIn.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        output.flush()
                    }
                } catch (_: Exception) {}
                try { channel.disconnect() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            // 等待任一方向结束
            t1.join()
            t2.join()

        } catch (e: Exception) {
            Log.e("SmartBridge", "CONNECT $target 失败: ${e.message}")
            try {
                output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                output.flush()
            } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 明文 HTTP → 通过 SSH direct-tcpip 转发
     */
    private suspend fun handleHttp(
        client: Socket, target: String, method: String,
        headers: List<String>,
        input: InputStream, output: OutputStream
    ) = withContext(Dispatchers.IO) {
        val url = try {
            java.net.URL(target)
        } catch (_: Exception) {
            client.close(); return@withContext
        }
        val port = if (url.port > 0) url.port else if (url.protocol == "https") 443 else 80

        try {
            val channel = sshSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(url.host)
            channel.setPort(port)
            channel.connect(15_000)

            val remoteOut = channel.getOutputStream()
            val remoteIn = channel.getInputStream()

            // 构建转发请求
            val path = buildString {
                append(if (url.path.isNullOrEmpty()) "/" else url.path)
                if (!url.query.isNullOrEmpty()) append("?${url.query}")
            }

            // 写请求行
            remoteOut.write("$method $path HTTP/1.1\r\n".toByteArray())

            // 转发 headers
            var hasHost = false
            for (h in headers) {
                val lower = h.lowercase()
                if (lower.startsWith("proxy-")) continue
                if (lower.startsWith("host:")) hasHost = true
                remoteOut.write("$h\r\n".toByteArray())
            }
            if (!hasHost) {
                remoteOut.write("Host: ${url.host}\r\n".toByteArray())
            }
            remoteOut.write("Connection: close\r\n".toByteArray())
            remoteOut.write("\r\n".toByteArray())
            remoteOut.flush()

            // 转发请求体（如果有）
            val t1 = Thread {
                try {
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        remoteOut.write(buf, 0, n)
                        remoteOut.flush()
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            // 转发响应
            val buf = ByteArray(16_384)
            while (true) {
                val n = remoteIn.read(buf)
                if (n == -1) break
                output.write(buf, 0, n)
                output.flush()
            }

            try { channel.disconnect() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
            t1.interrupt()
        } catch (e: Exception) {
            Log.e("SmartBridge", "HTTP $method $target 失败: ${e.message}")
            try {
                val msg = e.message ?: "unknown"
                output.write("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\n\r\nSSH connect failed: $msg".toByteArray())
                output.flush()
            } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 逐行读取 HTTP 请求
     */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code && prev == '\r'.code) {
                sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    fun stop() {
        scope.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        onStatus?.invoke("HTTP 代理已停止")
    }

    fun isRunning(): Boolean = serverSocket?.isClosed == false
}
