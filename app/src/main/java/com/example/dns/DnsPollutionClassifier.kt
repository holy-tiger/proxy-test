package com.example.dns

/**
 * Diagnostic verdict classifying the comparison between local ISP DNS and custom upstream DNS.
 */
enum class DnsVerdictType {
    CONSISTENT,      // 解析完全一致
    CDN_DIVERGENCE,  // 正常 CDN / Anycast 智能多节点分流 (两端均为正常公网合法 IP，非污染)
    POLLUTION_BOGON, // 检测到虚假/保留/内网投毒 IP
    POLLUTION_BLOCKED // 本地系统超时或拒绝解析，而上游纯净 DNS 成功解析
}

object DnsPollutionClassifier {

    // Common bogon / private / poisoned IP subnets used by ISPs or rogue DNS injectors
    private val POISONED_EXACT_IPS = setOf(
        "0.0.0.0",
        "127.0.0.1",
        "1.1.1.0",
        "255.255.255.255"
    )

    private fun isPrivateOrBogonIp(ip: String): Boolean {
        if (ip in POISONED_EXACT_IPS) return true
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false

        // 10.0.0.0/8 (Private)
        if (parts[0] == 10) return true
        // 172.16.0.0/12 (Private)
        if (parts[0] == 172 && parts[1] in 16..31) return true
        // 192.168.0.0/16 (Private)
        if (parts[0] == 192 && parts[1] == 168) return true
        // 100.64.0.0/10 (Carrier-grade NAT)
        if (parts[0] == 100 && parts[1] in 64..127) return true
        // 169.254.0.0/16 (Link-local)
        if (parts[0] == 169 && parts[1] == 254) return true
        // 224.0.0.0/4 (Multicast) or 240.0.0.0/4 (Reserved)
        if (parts[0] >= 224) return true

        return false
    }

    /**
     * Accurately classify the difference between local ISP DNS and upstream DNS.
     * Prevents false alarms on legitimate CDN GeoDNS / Anycast / Round-Robin routing.
     */
    fun classify(
        localIps: List<String>,
        localError: String?,
        upstreamIps: List<String>,
        upstreamError: String?
    ): DnsVerdictType {
        // 1. Upstream failed too or both failed -> consistent / unreachable
        if (upstreamIps.isEmpty()) {
            return DnsVerdictType.CONSISTENT
        }

        // 2. Local system failed or timed out, but upstream resolved cleanly -> Hard Block / Pollution
        if (localError != null || localIps.isEmpty()) {
            return DnsVerdictType.POLLUTION_BLOCKED
        }

        // 3. Exact IP match or intersection -> Consistent
        if (localIps.any { upstreamIps.contains(it) }) {
            return DnsVerdictType.CONSISTENT
        }

        // 4. Check if local ISP returned bogon / private / sinkhole IP -> Hard Poisoning
        val hasBogon = localIps.any { isPrivateOrBogonIp(it) }
        if (hasBogon) {
            return DnsVerdictType.POLLUTION_BOGON
        }

        // 5. Check subnet proximity (/16 or /24 matching): e.g. 74.125.138.x vs 74.125.200.x (Same AS/Google CDN)
        val sameSubnet = localIps.any { lIp ->
            upstreamIps.any { uIp ->
                val lParts = lIp.split(".")
                val uParts = uIp.split(".")
                if (lParts.size == 4 && uParts.size == 4) {
                    lParts[0] == uParts[0] && lParts[1] == uParts[1]
                } else false
            }
        }
        if (sameSubnet) {
            // Definitely CDN / Same Provider multi-node pool
            return DnsVerdictType.CDN_DIVERGENCE
        }

        // 6. If both sides returned valid public IPs with no bogons, treat as CDN / Anycast GeoDNS divergence
        return DnsVerdictType.CDN_DIVERGENCE
    }
}
