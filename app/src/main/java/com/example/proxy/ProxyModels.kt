package com.example.proxy

data class ProxyConfig(
    val port: Int = 8964,
    val dnsServerIp: String = "1.1.1.1",
    val dnsServerPort: Int = 53,
    val useDoh: Boolean = false,
    val useCache: Boolean = true,
    val activePresetId: String = "cloudflare"
)

data class ProxyLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val targetHost: String,
    val targetPort: Int,
    val resolvedIp: String? = null,
    val dnsServerUsed: String? = null,
    val status: String,
    val latencyMs: Long = 0,
    val bytesTransferred: Long = 0,
    val protocol: String = "HTTPS Tunnel"
)

data class ProxyServerStats(
    val isRunning: Boolean = false,
    val port: Int = 8964,
    val dnsServerIp: String = "1.1.1.1",
    val activeConnections: Int = 0,
    val totalRequests: Long = 0,
    val totalBytesTransferred: Long = 0,
    val startedAt: Long? = null,
    val lastErrorMessage: String? = null
)
