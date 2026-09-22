package com.example

import com.example.dns.DnsCache
import com.example.dns.DnsServerPreset
import com.example.proxy.ProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testDnsCachePutAndGet() {
        val cache = DnsCache()
        cache.put("example.com", listOf("93.184.216.34"), 60, "8.8.8.8")
        val result = cache.get("example.com")
        assertNotNull(result)
        assertEquals(listOf("93.184.216.34"), result)
        assertEquals(1, cache.size())
    }

    @Test
    fun testDnsCacheClear() {
        val cache = DnsCache()
        cache.put("example.com", listOf("93.184.216.34"), 60, "8.8.8.8")
        cache.clear()
        assertNull(cache.get("example.com"))
        assertEquals(0, cache.size())
    }

    @Test
    fun testDnsPresetsContainsCloudflareAndGoogle() {
        val presets = DnsServerPreset.PRESETS
        assertTrue(presets.any { it.ip == "1.1.1.1" })
        assertTrue(presets.any { it.ip == "8.8.8.8" })
    }

    @Test
    fun testDefaultProxyConfig() {
        val config = ProxyConfig()
        assertEquals(8964, config.port)
        assertEquals("1.1.1.1", config.dnsServerIp)
    }

    @Test
    fun testPortCollisionAvoidance() {
        // Pre-occupy a socket
        val blocker = java.net.ServerSocket()
        blocker.bind(java.net.InetSocketAddress("127.0.0.1", 0))
        val occupiedPort = blocker.localPort

        val candidate1 = occupiedPort
        var chosenPort = candidate1
        val maxAttempts = 5
        var attempt = 0
        var boundSocket: java.net.ServerSocket? = null

        while (attempt < maxAttempts) {
            val candidate = candidate1 + attempt
            try {
                val s = java.net.ServerSocket()
                s.reuseAddress = true
                s.bind(java.net.InetSocketAddress("127.0.0.1", candidate))
                boundSocket = s
                chosenPort = candidate
                break
            } catch (e: java.net.BindException) {
                attempt++
            }
        }

        assertNotNull(boundSocket)
        assertTrue(chosenPort > occupiedPort)
        boundSocket?.close()
        blocker.close()
    }

    @Test
    fun testDnsClassifierCdnDivergence() {
        // Legitimate Google CDN nodes in same or different subnet
        val localIps = listOf("74.125.138.138", "74.125.138.139")
        val upstreamIps = listOf("74.125.200.100", "74.125.200.101")

        val verdict = com.example.dns.DnsPollutionClassifier.classify(
            localIps = localIps,
            localError = null,
            upstreamIps = upstreamIps,
            upstreamError = null
        )
        // Should NOT falsely alarm as pollution, but correctly classify as CDN divergence
        assertEquals(com.example.dns.DnsVerdictType.CDN_DIVERGENCE, verdict)
    }

    @Test
    fun testDnsClassifierBogonPollution() {
        // Poisoned with 127.0.0.1 or 0.0.0.0
        val localIps = listOf("127.0.0.1")
        val upstreamIps = listOf("142.250.190.46")

        val verdict = com.example.dns.DnsPollutionClassifier.classify(
            localIps = localIps,
            localError = null,
            upstreamIps = upstreamIps,
            upstreamError = null
        )
        assertEquals(com.example.dns.DnsVerdictType.POLLUTION_BOGON, verdict)
    }

    @Test
    fun testDns114PresetHasNoDoh() {
        val preset114 = DnsServerPreset.PRESETS.find { it.id == "dns114" }
        assertNotNull(preset114)
        assertEquals("114.114.114.114", preset114?.ip)
        assertNull(preset114?.dohUrl)
    }
}

