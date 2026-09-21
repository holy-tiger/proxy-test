package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dns.DnsVerdictType
import com.example.ui.ProxyViewModel
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DnsDiagnosticsScreen(
    viewModel: ProxyViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val stats by viewModel.proxyStats.collectAsState()

    val testDomains = remember {
        listOf(
            "google.com",
            "wikipedia.org",
            "twitter.com",
            "github.com",
            "cloudflare.com",
            "youtube.com"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Introduction Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DNS 污染与绕过诊断工具",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "对比本地运营商 DNS 与本应用代理设置的上游 DNS，直观展示 DNS 污染识别与成功绕过效果",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            // Domain Test Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择或输入测试域名",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        testDomains.forEach { domain ->
                            FilterChip(
                                selected = uiState.dnsTestDomain == domain,
                                onClick = {
                                    viewModel.updateDnsTestDomain(domain)
                                    viewModel.runDnsDiagnostics(domain)
                                },
                                label = { Text(domain, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = uiState.dnsTestDomain,
                        onValueChange = { viewModel.updateDnsTestDomain(it) },
                        label = { Text("目标域名") },
                        placeholder = { Text("例如 example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dns_diagnostic_domain_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.runDnsDiagnostics() },
                        enabled = !uiState.isDnsTestRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_dns_diagnostic_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isDnsTestRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("双路 DNS 查询对比中...")
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("开始防污染对比解析")
                        }
                    }
                }
            }
        }

        // Diagnostic comparison result
        uiState.lastDnsComparison?.let { comp ->
            item {
                // Verdict Banner with 3 granular levels (Consistent, CDN divergence, Hard pollution)
                val isHardPollution = comp.verdict == DnsVerdictType.POLLUTION_BLOCKED || comp.verdict == DnsVerdictType.POLLUTION_BOGON
                val isCdnDivergence = comp.verdict == DnsVerdictType.CDN_DIVERGENCE

                val bannerColor = when {
                    isHardPollution -> StatusAmber.copy(alpha = 0.18f)
                    isCdnDivergence -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else -> StatusGreen.copy(alpha = 0.15f)
                }
                val borderColor = when {
                    isHardPollution -> StatusAmber
                    isCdnDivergence -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> StatusGreen
                }
                val iconVector = when {
                    isHardPollution -> Icons.Default.GppBad
                    isCdnDivergence -> Icons.Default.Dns
                    else -> Icons.Default.GppGood
                }
                val iconTint = when {
                    isHardPollution -> StatusAmber
                    isCdnDivergence -> MaterialTheme.colorScheme.primary
                    else -> StatusGreen
                }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dns_verdict_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = bannerColor
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(borderColor)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when {
                                    isHardPollution -> "⚠️ 检测到本地 DNS 污染 / 阻断"
                                    isCdnDivergence -> "ℹ️ 正常 CDN / Anycast 智能多节点分流 (非污染)"
                                    else -> "✅ DNS 解析一致"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = iconTint
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    comp.verdict == DnsVerdictType.POLLUTION_BLOCKED ->
                                        "本地 ISP DNS 出现超时或阻断无法解析，而应用内指定的上游 DNS 服务器 (${comp.customDnsServer}) 成功解析出纯净真实 IP！内部代理已成功保护通信。"
                                    comp.verdict == DnsVerdictType.POLLUTION_BOGON ->
                                        "本地 ISP DNS 返回了虚假/私有保留 IP，证实受到投毒污染；上游 DNS 成功解析出公网真实 IP，代理有效防御。"
                                    isCdnDivergence ->
                                        "目标网站部署了 CDN 或全球 Anycast 网络。本地系统与上游 DNS (${comp.customDnsServer}) 分配了不同机房的正常公网节点，属于合法的就近接入调度，并非 DNS 污染。"
                                    else ->
                                        "本地 ISP DNS 与上游纯净 DNS 解析结果一致，无异常污染迹象。"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Side-by-side comparison cards
            item {
                Text(
                    text = "解析详情对比 (${comp.domain})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                // 1. Local ISP DNS
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. 本地系统 ISP DNS 解析",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${comp.localLatencyMs} ms",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (comp.localError != null) {
                            Text(
                                text = "解析失败: ${comp.localError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (comp.localSystemIps.isEmpty()) {
                            Text(
                                text = "无返回 IP 记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed
                            )
                        } else {
                            comp.localSystemIps.forEach { ip ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (comp.isPollutedOrDifferent) StatusRed else StatusGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ip,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // 2. In-App Custom DNS
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. 内部代理上游 DNS (${comp.customDnsServer})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = comp.customProtocol,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${comp.customLatencyMs} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (comp.customError != null) {
                            Text(
                                text = "查询异常: ${comp.customError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (comp.customDnsIps.isEmpty()) {
                            Text(
                                text = "无返回 A 记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRed
                            )
                        } else {
                            comp.customDnsIps.forEach { ip ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ip,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // How it works card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "原理解析：如何解决 DNS 污染？",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. 常规请求流程：应用发起请求 -> 调用系统 getaddrinfo() -> 向当地运营商 (ISP) DNS 发送明文 UDP 查询 -> 遭遇旁路投毒/篡改 -> 获得虚假 IP 或阻断。\n\n" +
                                "2. 本 App 代理架构：应用在 127.0.0.1 运行轻量 HTTP/HTTPS CONNECT 代理；WebView 与原生网络库均配置指向该内部代理；由代理服务器直接向指定的纯净 DNS 服务器（如 1.1.1.1、8.8.8.8 或 DoH）发起解析，彻底脱离当地运营商 DNS 链路，从而解决 DNS 污染与受限访问需求。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
