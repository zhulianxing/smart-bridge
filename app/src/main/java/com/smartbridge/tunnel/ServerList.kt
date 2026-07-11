package com.smartbridge.tunnel

/**
 * 预置服务器列表 — 用户只需选择城市，凭证自动填充
 * 密码加密存储在 SecureStore 中，界面上完全不显示
 */
data class ServerInfo(
    val city: String,
    val flag: String,
    val host: String,
    val port: Int = 22,
    val user: String = "root",
    val password: String
)

object ServerList {
    val servers = listOf(
        ServerInfo("新加坡", "🇸🇬", "43.160.252.162", password = "buchiMIFAN139"),
        ServerInfo("首尔", "🇰🇷", "43.164.130.145", password = "buchiMIFAN139"),
        ServerInfo("东京", "🇯🇵", "43.165.191.194", password = "buchiMIFAN139"),
        ServerInfo("美国", "🇺🇸", "43.166.240.93", password = "buchiMIFAN139"),
        ServerInfo("曼谷", "🇹🇭", "43.164.1.110", password = "buchiMIFAN139"),
        ServerInfo("北京", "🇨🇳", "82.157.111.121", password = "buchiMIFAN139")
    )
}
