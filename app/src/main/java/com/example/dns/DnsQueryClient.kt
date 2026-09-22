package com.example.dns

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

data class DnsResolutionResult(
    val domain: String,
    val ips: List<String>,
    val dnsServer: String,
    val latencyMs: Long,
    val fromCache: Boolean = false,
    val protocol: String = "UDP-53",
    val ttlSeconds: Long = 300,
    val errorMessage: String? = null
)

class DnsQueryClient(
    private val cache: DnsCache = DnsCache()
) {
    private val TAG = "DnsQueryClient"
    private val ipPattern = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun isIpAddress(address: String): Boolean {
        return ipPattern.matcher(address.trim()).matches() || address.contains(":")
    }

    suspend fun resolve(
        domain: String,
        dnsServerIp: String,
        dnsServerPort: Int = 53,
        useDoh: Boolean = false,
        useCache: Boolean = true
    ): DnsResolutionResult {
        val cleanDomain = domain.trim().trimEnd('.')
        if (isIpAddress(cleanDomain)) {
            return DnsResolutionResult(
                domain = cleanDomain,
                ips = listOf(cleanDomain),
                dnsServer = "Direct IP",
                latencyMs = 0,
                fromCache = false,
                protocol = "Direct"
            )
        }

        if (useCache) {
            val cached = cache.get(cleanDomain)
            if (!cached.isNullOrEmpty()) {
                return DnsResolutionResult(
                    domain = cleanDomain,
                    ips = cached,
                    dnsServer = dnsServerIp,
                    latencyMs = 1,
                    fromCache = true,
                    protocol = "Cache"
                )
            }
        }

        val startTime = System.currentTimeMillis()

        // If DoH is requested, try DoH first
        if (useDoh) {
            val dohResult = queryDoh(cleanDomain, dnsServerIp)
            if (dohResult.ips.isNotEmpty()) {
                if (useCache) {
                    cache.put(cleanDomain, dohResult.ips, dohResult.ttlSeconds, dnsServerIp)
                }
                return dohResult
            }
            Log.w(TAG, "DoH failed for $cleanDomain via $dnsServerIp, falling back to standard UDP 53")
        }

        // Try direct UDP DNS on port 53 (RFC 1035)
        try {
            val udpResult = queryUdp(cleanDomain, dnsServerIp, dnsServerPort)
            val latency = System.currentTimeMillis() - startTime
            if (udpResult.isNotEmpty()) {
                val ttl = 300L
                if (useCache) {
                    cache.put(cleanDomain, udpResult, ttl, dnsServerIp)
                }
                return DnsResolutionResult(
                    domain = cleanDomain,
                    ips = udpResult,
                    dnsServer = "$dnsServerIp:$dnsServerPort",
                    latencyMs = latency,
                    fromCache = false,
                    protocol = "UDP-53",
                    ttlSeconds = ttl
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP DNS query failed for $cleanDomain via $dnsServerIp: ${e.message}")
        }

        val latency = System.currentTimeMillis() - startTime
        return DnsResolutionResult(
            domain = cleanDomain,
            ips = emptyList(),
            dnsServer = "$dnsServerIp:$dnsServerPort",
            latencyMs = latency,
            fromCache = false,
            protocol = if (useDoh) "DoH+UDP-Failed" else "UDP-Failed",
            errorMessage = "No A records returned from $dnsServerIp:$dnsServerPort (timeout or blocked)"
        )
    }

    /**
     * Standard RFC 1035 UDP DNS Query
     */
    private fun queryUdp(domain: String, dnsServerIp: String, port: Int): List<String> {
        val queryId = Random.nextInt(1, 0xFFFE)
        val queryPacket = buildDnsQuery(queryId, domain)

        DatagramSocket().use { socket ->
            socket.soTimeout = 3500
            val serverAddress = InetAddress.getByName(dnsServerIp)
            val sendPacket = DatagramPacket(queryPacket, queryPacket.size, serverAddress, port)
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            return parseDnsResponse(receiveBuffer, receivePacket.length, queryId)
        }
    }

    private fun buildDnsQuery(queryId: Int, domain: String): ByteArray {
        val buffer = ByteBuffer.allocate(512)
        // 1. Transaction ID
        buffer.putShort(queryId.toShort())
        // 2. Flags: RD = 1 (Recursion Desired), Standard query
        buffer.putShort(0x0100.toShort())
        // 3. QDCOUNT: 1
        buffer.putShort(1.toShort())
        // 4. ANCOUNT: 0
        buffer.putShort(0.toShort())
        // 5. NSCOUNT: 0
        buffer.putShort(0.toShort())
        // 6. ARCOUNT: 0
        buffer.putShort(0.toShort())

        // Question: QNAME
        val labels = domain.split('.')
        for (label in labels) {
            val labelBytes = label.toByteArray(Charsets.US_ASCII)
            buffer.put(labelBytes.size.toByte())
            buffer.put(labelBytes)
        }
        buffer.put(0.toByte()) // Null terminator

        // QTYPE: 1 (A Record)
        buffer.putShort(1.toShort())
        // QCLASS: 1 (IN)
        buffer.putShort(1.toShort())

        val result = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(result)
        return result
    }

    private fun parseDnsResponse(data: ByteArray, length: Int, expectedId: Int): List<String> {
        if (length < 12) return emptyList()
        val buffer = ByteBuffer.wrap(data, 0, length)

        val id = buffer.short.toInt() and 0xFFFF
        val flags = buffer.short.toInt() and 0xFFFF
        val qdcount = buffer.short.toInt() and 0xFFFF
        val ancount = buffer.short.toInt() and 0xFFFF

        val rcode = flags and 0x000F
        if (rcode != 0 || ancount == 0) {
            return emptyList()
        }

        // Skip Question section
        for (i in 0 until qdcount) {
            skipName(buffer)
            if (buffer.remaining() < 4) return emptyList()
            buffer.short // QTYPE
            buffer.short // QCLASS
        }

        // Parse Answer section
        val ips = mutableListOf<String>()
        for (i in 0 until ancount) {
            if (buffer.remaining() < 10) break
            skipName(buffer)
            if (buffer.remaining() < 10) break

            val type = buffer.short.toInt() and 0xFFFF
            val clazz = buffer.short.toInt() and 0xFFFF
            val ttl = buffer.int.toLong() and 0xFFFFFFFFL
            val rdlength = buffer.short.toInt() and 0xFFFF

            if (buffer.remaining() < rdlength) break

            if (type == 1 && rdlength == 4) {
                // Type A (IPv4)
                val b1 = buffer.get().toInt() and 0xFF
                val b2 = buffer.get().toInt() and 0xFF
                val b3 = buffer.get().toInt() and 0xFF
                val b4 = buffer.get().toInt() and 0xFF
                ips.add("$b1.$b2.$b3.$b4")
            } else {
                // Skip RDATA for other types (e.g. CNAME, SOA, etc.)
                buffer.position(buffer.position() + rdlength)
            }
        }
        return ips
    }

    private fun skipName(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val len = buffer.get().toInt() and 0xFF
            if (len == 0) {
                break
            } else if ((len and 0xC0) == 0xC0) {
                // Pointer - 2 bytes total
                if (buffer.hasRemaining()) buffer.get()
                break
            } else {
                if (buffer.remaining() >= len) {
                    buffer.position(buffer.position() + len)
                } else {
                    break
                }
            }
        }
    }

    /**
     * DoH (DNS over HTTPS) using direct IP to bypass local DNS pollution
     */
    private fun queryDoh(domain: String, dnsServerIp: String): DnsResolutionResult {
        val startTime = System.currentTimeMillis()
        val url = when (dnsServerIp) {
            "1.1.1.1" -> "https://1.1.1.1/dns-query?name=$domain&type=A"
            "8.8.8.8" -> "https://8.8.8.8/resolve?name=$domain&type=A"
            "9.9.9.9" -> "https://9.9.9.9/dns-query?name=$domain&type=A"
            "223.5.5.5" -> "https://223.5.5.5/resolve?name=$domain&type=A"
            else -> "https://$dnsServerIp/dns-query?name=$domain&type=A"
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful && body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val answers = json.optJSONArray("Answer")
                    val ips = mutableListOf<String>()
                    var ttl = 300L
                    if (answers != null) {
                        for (i in 0 until answers.length()) {
                            val obj = answers.getJSONObject(i)
                            val type = obj.optInt("type", 0)
                            val data = obj.optString("data", "")
                            if (type == 1 && isIpAddress(data)) {
                                ips.add(data)
                                ttl = obj.optLong("TTL", 300L)
                            }
                        }
                    }
                    if (ips.isNotEmpty()) {
                        return DnsResolutionResult(
                            domain = domain,
                            ips = ips,
                            dnsServer = "$dnsServerIp (DoH)",
                            latencyMs = latency,
                            fromCache = false,
                            protocol = "DoH-HTTPS",
                            ttlSeconds = ttl
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoH query error: ${e.message}")
        }
        return DnsResolutionResult(
            domain = domain,
            ips = emptyList(),
            dnsServer = "$dnsServerIp (DoH)",
            latencyMs = System.currentTimeMillis() - startTime,
            protocol = "DoH-Failed"
        )
    }

    fun getCache(): DnsCache = cache
}
