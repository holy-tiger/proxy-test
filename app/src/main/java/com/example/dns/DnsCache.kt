package com.example.dns

import java.util.concurrent.ConcurrentHashMap

data class CachedDnsRecord(
    val ips: List<String>,
    val expireAtMillis: Long,
    val dnsServer: String
)

class DnsCache {
    private val cache = ConcurrentHashMap<String, CachedDnsRecord>()

    fun get(domain: String): List<String>? {
        val lowerDomain = domain.lowercase().trim()
        val record = cache[lowerDomain] ?: return null
        if (System.currentTimeMillis() > record.expireAtMillis) {
            cache.remove(lowerDomain)
            return null
        }
        return record.ips
    }

    fun put(domain: String, ips: List<String>, ttlSeconds: Long, dnsServer: String) {
        if (ips.isEmpty()) return
        val lowerDomain = domain.lowercase().trim()
        // Clamp TTL between 10 seconds and 1 hour
        val effectiveTtl = ttlSeconds.coerceIn(10L, 3600L)
        val expireAt = System.currentTimeMillis() + (effectiveTtl * 1000L)
        cache[lowerDomain] = CachedDnsRecord(ips, expireAt, dnsServer)
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size

    fun getAll(): Map<String, CachedDnsRecord> = cache.toMap()
}
