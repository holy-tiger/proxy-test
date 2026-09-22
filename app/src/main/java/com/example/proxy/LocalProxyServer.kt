package com.example.proxy

import android.util.Log
import com.example.dns.DnsQueryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class LocalProxyServer(
    private val dnsQueryClient: DnsQueryClient
) {
    private val TAG = "LocalProxyServer"

    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var currentConfig = ProxyConfig()

    private val activeConnectionsCounter = AtomicInteger(0)
    private val totalRequestsCounter = AtomicLong(0)
    private val totalBytesCounter = AtomicLong(0)

    private val _stats = MutableStateFlow(ProxyServerStats())
    val stats: StateFlow<ProxyServerStats> = _stats.asStateFlow()

    private val _logs = MutableStateFlow<List<ProxyLogEntry>>(emptyList())
    val logs: StateFlow<List<ProxyLogEntry>> = _logs.asStateFlow()

    fun getConfig(): ProxyConfig = currentConfig

    @Synchronized
    fun start(config: ProxyConfig) {
        if (_stats.value.isRunning && currentConfig.port == config.port && currentConfig.dnsServerIp == config.dnsServerIp && currentConfig.useDoh == config.useDoh) {
            // Already running with identical parameters
            return
        }

        stop()
        currentConfig = config

        serverJob = serverScope.launch {
            try {
                // Try binding to requested port first. If occupied (BindException), automatically
                // probe subsequent ports or assign ephemeral port so app never crashes or fails.
                var actualPort = config.port
                var ss: ServerSocket? = null
                var attempt = 0
                val maxAttempts = 20

                while (attempt < maxAttempts) {
                    val candidatePort = config.port + attempt
                    try {
                        val socket = ServerSocket()
                        socket.reuseAddress = true
                        socket.bind(InetSocketAddress("127.0.0.1", candidatePort))
                        ss = socket
                        actualPort = candidatePort
                        if (attempt > 0) {
                            Log.w(TAG, "Port ${config.port} was busy. Successfully rebound to alternative free port: $actualPort")
                        }
                        break
                    } catch (be: java.net.BindException) {
                        Log.w(TAG, "Port $candidatePort is currently occupied, trying next port...")
                        attempt++
                    }
                }

                // If consecutive ports are all busy, let the OS kernel allocate an ephemeral free port (port 0)
                if (ss == null) {
                    val socket = ServerSocket()
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress("127.0.0.1", 0))
                    ss = socket
                    actualPort = socket.localPort
                    Log.i(TAG, "Allocated dynamic OS free port: $actualPort")
                }

                serverSocket = ss
                currentConfig = config.copy(port = actualPort)

                _stats.update {
                    it.copy(
                        isRunning = true,
                        port = actualPort,
                        dnsServerIp = config.dnsServerIp,
                        startedAt = System.currentTimeMillis(),
                        lastErrorMessage = null
                    )
                }
                Log.i(TAG, "Local Proxy Server started on 127.0.0.1:$actualPort, upstream DNS: ${config.dnsServerIp}")

                while (isActive && !ss.isClosed) {
                    try {
                        val clientSocket = ss.accept()
                        clientSocket.soTimeout = 15000 // 15s socket timeout
                        launch(Dispatchers.IO) {
                            handleClientSocket(clientSocket, config)
                        }
                    } catch (e: Exception) {
                        if (!ss.isClosed) {
                            Log.w(TAG, "Exception accepting connection: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start proxy server on port ${config.port}: ${e.message}", e)
                _stats.update {
                    it.copy(
                        isRunning = false,
                        lastErrorMessage = "启动失败: ${e.message}"
                    )
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing serverSocket: ${e.message}")
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null

        _stats.update {
            it.copy(
                isRunning = false,
                activeConnections = 0
            )
        }
        Log.i(TAG, "Local Proxy Server stopped")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private suspend fun handleClientSocket(clientSocket: Socket, config: ProxyConfig) {
        val active = activeConnectionsCounter.incrementAndGet()
        val reqNumber = totalRequestsCounter.incrementAndGet()
        updateStats()

        val startTime = System.currentTimeMillis()
        var remoteSocket: Socket? = null
        var logEntry: ProxyLogEntry? = null

        try {
            val clientIn = BufferedInputStream(clientSocket.inputStream)
            val clientOut = BufferedOutputStream(clientSocket.outputStream)

            // Read the HTTP request line
            val requestLine = readLine(clientIn) ?: return
            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val uri = parts[1]

            if (method == "CONNECT") {
                // HTTPS CONNECT Tunneling
                val hostPort = uri.split(":")
                val host = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443

                // Skip remainder of client headers (until empty line)
                skipHeaders(clientIn)

                // 1. Resolve host with custom DNS server
                val dnsResult = dnsQueryClient.resolve(
                    domain = host,
                    dnsServerIp = config.dnsServerIp,
                    dnsServerPort = config.dnsServerPort,
                    useDoh = config.useDoh,
                    useCache = config.useCache
                )

                if (dnsResult.ips.isEmpty()) {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\n\r\nDNS Resolution Failed for $host via ${config.dnsServerIp}\r\n".toByteArray())
                    clientOut.flush()
                    logEntry = ProxyLogEntry(
                        method = "CONNECT",
                        targetHost = host,
                        targetPort = port,
                        resolvedIp = "None",
                        dnsServerUsed = "${config.dnsServerIp}:${config.dnsServerPort} (${dnsResult.protocol})",
                        status = "502 DNS Failed: ${dnsResult.errorMessage ?: "无解析结果"}",
                        latencyMs = System.currentTimeMillis() - startTime,
                        protocol = "HTTPS Tunnel"
                    )
                    return
                }

                val resolvedIp = dnsResult.ips.first()

                // 2. Connect to resolved IP
                val remote = Socket()
                remote.connect(InetSocketAddress(resolvedIp, port), 10000)
                remote.soTimeout = 30000
                remoteSocket = remote

                // 3. Inform client that tunnel is established
                val establishedHeader = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: InAppDnsProxy/1.0\r\n\r\n"
                clientOut.write(establishedHeader.toByteArray(Charsets.US_ASCII))
                clientOut.flush()

                // 4. Bi-directionally relay data
                val remoteIn = BufferedInputStream(remote.inputStream)
                val remoteOut = BufferedOutputStream(remote.outputStream)

                val bytesTransferred = relayBidirectional(clientIn, clientOut, remoteIn, remoteOut)
                totalBytesCounter.addAndGet(bytesTransferred)

                val totalDuration = System.currentTimeMillis() - startTime
                logEntry = ProxyLogEntry(
                    method = "CONNECT",
                    targetHost = host,
                    targetPort = port,
                    resolvedIp = resolvedIp,
                    dnsServerUsed = "${dnsResult.dnsServer} (${dnsResult.protocol})",
                    status = "200 Connection Closed",
                    latencyMs = totalDuration,
                    bytesTransferred = bytesTransferred,
                    protocol = "HTTPS Tunnel"
                )
            } else {
                // Plain HTTP Forward Proxy
                var host = ""
                var port = 80
                var relativePath = uri

                if (uri.startsWith("http://", ignoreCase = true)) {
                    val afterScheme = uri.substring(7)
                    val slashIdx = afterScheme.indexOf('/')
                    val hostPort = if (slashIdx != -1) afterScheme.substring(0, slashIdx) else afterScheme
                    relativePath = if (slashIdx != -1) afterScheme.substring(slashIdx) else "/"

                    val hp = hostPort.split(":")
                    host = hp[0]
                    if (hp.size > 1) port = hp[1].toIntOrNull() ?: 80
                }

                // Read all client headers
                val headerLines = mutableListOf<String>()
                while (true) {
                    val headerLine = readLine(clientIn) ?: break
                    if (headerLine.isEmpty()) break
                    headerLines.add(headerLine)
                    if (host.isEmpty() && headerLine.startsWith("Host:", ignoreCase = true)) {
                        val hostValue = headerLine.substring(5).trim()
                        val hp = hostValue.split(":")
                        host = hp[0]
                        if (hp.size > 1) port = hp[1].toIntOrNull() ?: 80
                    }
                }

                if (host.isEmpty()) {
                    clientOut.write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray())
                    clientOut.flush()
                    return
                }

                // Resolve with custom DNS server
                val dnsResult = dnsQueryClient.resolve(
                    domain = host,
                    dnsServerIp = config.dnsServerIp,
                    dnsServerPort = config.dnsServerPort,
                    useDoh = config.useDoh,
                    useCache = config.useCache
                )

                if (dnsResult.ips.isEmpty()) {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                    clientOut.flush()
                    logEntry = ProxyLogEntry(
                        method = method,
                        targetHost = host,
                        targetPort = port,
                        resolvedIp = "None",
                        dnsServerUsed = "${config.dnsServerIp}:${config.dnsServerPort} (${dnsResult.protocol})",
                        status = "502 DNS Failed: ${dnsResult.errorMessage ?: "无解析结果"}",
                        latencyMs = System.currentTimeMillis() - startTime,
                        protocol = "HTTP"
                    )
                    return
                }

                val resolvedIp = dnsResult.ips.first()
                val remote = Socket()
                remote.connect(InetSocketAddress(resolvedIp, port), 10000)
                remoteSocket = remote

                val remoteOut = BufferedOutputStream(remote.outputStream)
                val remoteIn = BufferedInputStream(remote.inputStream)

                // Rewrite request line to relative path and send headers
                val version = if (parts.size > 2) parts[2] else "HTTP/1.1"
                remoteOut.write("$method $relativePath $version\r\n".toByteArray())
                for (h in headerLines) {
                    if (!h.startsWith("Proxy-Connection:", ignoreCase = true)) {
                        remoteOut.write("$h\r\n".toByteArray())
                    }
                }
                remoteOut.write("Connection: close\r\n\r\n".toByteArray())
                remoteOut.flush()

                // Forward response from remote back to client
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                try {
                    while (remoteIn.read(buffer).also { bytesRead = it } != -1) {
                        clientOut.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    clientOut.flush()
                } catch (_: Exception) {}

                totalBytesCounter.addAndGet(totalBytes)
                val totalDuration = System.currentTimeMillis() - startTime
                logEntry = ProxyLogEntry(
                    method = method,
                    targetHost = host,
                    targetPort = port,
                    resolvedIp = resolvedIp,
                    dnsServerUsed = "${dnsResult.dnsServer} (${dnsResult.protocol})",
                    status = "200 OK",
                    latencyMs = totalDuration,
                    bytesTransferred = totalBytes,
                    protocol = "HTTP"
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            if (e !is SocketTimeoutException) {
                Log.d(TAG, "Connection handled with exception: ${e.message}")
            }
        } finally {
            activeConnectionsCounter.decrementAndGet()
            updateStats()
            try { clientSocket.close() } catch (_: Exception) {}
            try { remoteSocket?.close() } catch (_: Exception) {}

            logEntry?.let { entry ->
                addLog(entry)
            }
        }
    }

    private fun addLog(entry: ProxyLogEntry) {
        _logs.update { current ->
            val list = current.toMutableList()
            list.add(0, entry)
            if (list.size > 200) {
                list.subList(0, 200)
            } else {
                list
            }
        }
    }

    private fun updateStats() {
        _stats.update {
            it.copy(
                activeConnections = activeConnectionsCounter.get().coerceAtLeast(0),
                totalRequests = totalRequestsCounter.get(),
                totalBytesTransferred = totalBytesCounter.get()
            )
        }
    }

    private fun relayBidirectional(
        clientIn: InputStream,
        clientOut: OutputStream,
        remoteIn: InputStream,
        remoteOut: OutputStream
    ): Long {
        val totalBytes = AtomicLong(0)
        val thread1 = Thread {
            try {
                val buffer = ByteArray(16384)
                var read: Int
                while (clientIn.read(buffer).also { read = it } != -1) {
                    remoteOut.write(buffer, 0, read)
                    remoteOut.flush()
                    totalBytes.addAndGet(read.toLong())
                }
            } catch (_: Exception) {}
        }

        val thread2 = Thread {
            try {
                val buffer = ByteArray(16384)
                var read: Int
                while (remoteIn.read(buffer).also { read = it } != -1) {
                    clientOut.write(buffer, 0, read)
                    clientOut.flush()
                    totalBytes.addAndGet(read.toLong())
                }
            } catch (_: Exception) {}
        }

        thread1.start()
        thread2.start()

        try {
            thread1.join(45000)
            thread2.join(45000)
        } catch (_: Exception) {}

        return totalBytes.get()
    }

    private fun readLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\n'.code) {
                break
            } else if (c != '\r'.code) {
                baos.write(c)
            }
        }
        if (baos.size() == 0 && c == -1) return null
        return baos.toString("ISO-8859-1")
    }

    private fun skipHeaders(input: InputStream) {
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
        }
    }
}
