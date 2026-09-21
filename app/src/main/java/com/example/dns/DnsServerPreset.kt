package com.example.dns

data class DnsServerPreset(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 53,
    val description: String,
    val dohUrl: String? = null,
    val isCustom: Boolean = false
) {
    companion object {
        val PRESETS = listOf(
            DnsServerPreset(
                id = "cloudflare",
                name = "Cloudflare DNS",
                ip = "1.1.1.1",
                port = 53,
                description = "高速隐私保护，全球极速节点",
                dohUrl = "https://1.1.1.1/dns-query"
            ),
            DnsServerPreset(
                id = "google",
                name = "Google Public DNS",
                ip = "8.8.8.8",
                port = 53,
                description = "谷歌全球公共 DNS 解析服务",
                dohUrl = "https://8.8.8.8/resolve"
            ),
            DnsServerPreset(
                id = "quad9",
                name = "Quad9 (9.9.9.9)",
                ip = "9.9.9.9",
                port = 53,
                description = "瑞士非营利机构，内置恶意域名拦截",
                dohUrl = "https://9.9.9.9/dns-query"
            ),
            DnsServerPreset(
                id = "opendns",
                name = "OpenDNS (Cisco)",
                ip = "208.67.222.222",
                port = 53,
                description = "思科旗下老牌安全 DNS 服务",
                dohUrl = null
            ),
            DnsServerPreset(
                id = "alidns",
                name = "AliDNS (阿里)",
                ip = "223.5.5.5",
                port = 53,
                description = "阿里巴巴公共 DNS 服务",
                dohUrl = "https://223.5.5.5/resolve"
            ),
            DnsServerPreset(
                id = "dns114",
                name = "114 DNS",
                ip = "114.114.114.114",
                port = 53,
                description = "国内主流纯净公共 DNS",
                dohUrl = null
            )
        )
    }
}
