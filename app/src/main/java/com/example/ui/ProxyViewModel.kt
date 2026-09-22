package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dns.DnsCache
import com.example.dns.DnsPollutionClassifier
import com.example.dns.DnsQueryClient
import com.example.dns.DnsResolutionResult
import com.example.dns.DnsServerPreset
import com.example.dns.DnsVerdictType
import com.example.network.NativeHttpClient
import com.example.network.NativeHttpResponse
import com.example.network.WebViewProxyManager
import com.example.proxy.LocalProxyServer
import com.example.proxy.ProxyConfig
import com.example.proxy.ProxyLogEntry
import com.example.proxy.ProxyServerStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class DnsComparisonResult(
    val domain: String,
    val localSystemIps: List<String>,
    val localLatencyMs: Long,
    val localError: String? = null,
    val customDnsIps: List<String>,
    val customDnsServer: String,
    val customLatencyMs: Long,
    val customProtocol: String,
    val customError: String? = null,
    val isPollutedOrDifferent: Boolean = false,
    val verdict: DnsVerdictType = DnsVerdictType.CONSISTENT
)

data class ProxyUiState(
    val selectedPresetId: String = "cloudflare",
    val customDnsIp: String = "1.1.1.1",
    val customDnsPort: Int = 53,
    val proxyPort: Int = 8964,
    val useDoh: Boolean = false,
    val useCache: Boolean = true,
    val isWebViewProxyActive: Boolean = false,
    val isWebViewProxySupported: Boolean = true,
    // Native test state
    val nativeTestUrl: String = "https://cloudflare.com/cdn-cgi/trace",
    val isNativeTestRunning: Boolean = false,
    val lastNativeResponse: NativeHttpResponse? = null,
    // DNS test state
    val dnsTestDomain: String = "google.com",
    val isDnsTestRunning: Boolean = false,
    val lastDnsComparison: DnsComparisonResult? = null,
    val dnsCacheCount: Int = 0
)

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val dnsCache = DnsCache()
    private val dnsQueryClient = DnsQueryClient(dnsCache)
    private val proxyServer = LocalProxyServer(dnsQueryClient)
    private val nativeHttpClient = NativeHttpClient()

    val proxyStats: StateFlow<ProxyServerStats> = proxyServer.stats
    val proxyLogs: StateFlow<List<ProxyLogEntry>> = proxyServer.logs

    private val _uiState = MutableStateFlow(
        ProxyUiState(
            isWebViewProxySupported = WebViewProxyManager.isSupported()
        )
    )
    val uiState: StateFlow<ProxyUiState> = _uiState.asStateFlow()

    init {
        // Automatically start the proxy server on launch with Cloudflare DNS
        startProxy()

        // Observe the proxy server stats so that if a port was occupied and auto-rebound to a new port,
        // WebView and UI state automatically update to match the actual listening port
        viewModelScope.launch {
            proxyServer.stats.collect { stats ->
                if (stats.isRunning && stats.port > 0) {
                    if (_uiState.value.proxyPort != stats.port) {
                        _uiState.update { it.copy(proxyPort = stats.port) }
                        WebViewProxyManager.applyProxy(stats.port) { success ->
                            _uiState.update { it.copy(isWebViewProxyActive = success) }
                        }
                    }
                }
            }
        }
    }

    fun startProxy() {
        val state = _uiState.value
        val config = ProxyConfig(
            port = state.proxyPort,
            dnsServerIp = state.customDnsIp,
            dnsServerPort = state.customDnsPort,
            useDoh = state.useDoh,
            useCache = state.useCache,
            activePresetId = state.selectedPresetId
        )

        proxyServer.start(config)

        // Apply proxy override to WebView
        WebViewProxyManager.applyProxy(state.proxyPort) { success ->
            _uiState.update { it.copy(isWebViewProxyActive = success) }
        }
    }

    fun stopProxy() {
        proxyServer.stop()
        WebViewProxyManager.clearProxy {
            _uiState.update { it.copy(isWebViewProxyActive = false) }
        }
    }

    fun selectPreset(preset: DnsServerPreset) {
        dnsCache.clear() // Clear cache so new DNS server resolves fresh IPs immediately
        _uiState.update {
            it.copy(
                selectedPresetId = preset.id,
                customDnsIp = preset.ip,
                customDnsPort = preset.port,
                // Only keep useDoh enabled if the target preset actually supports DoH; otherwise automatically switch to false
                useDoh = if (preset.dohUrl != null) it.useDoh else false
            )
        }
        if (proxyStats.value.isRunning) {
            startProxy() // Hot-reload proxy configuration
        }
    }

    fun setCustomDns(ip: String, port: Int = 53) {
        dnsCache.clear() // Clear cache so new DNS server resolves fresh IPs immediately
        _uiState.update {
            it.copy(
                selectedPresetId = "custom",
                customDnsIp = ip.trim(),
                customDnsPort = port
            )
        }
        if (proxyStats.value.isRunning) {
            startProxy()
        }
    }

    fun setProxyPort(port: Int) {
        _uiState.update { it.copy(proxyPort = port) }
        if (proxyStats.value.isRunning) {
            startProxy()
        }
    }

    fun toggleDoh(enabled: Boolean) {
        _uiState.update { it.copy(useDoh = enabled) }
        if (proxyStats.value.isRunning) {
            startProxy()
        }
    }

    fun toggleCache(enabled: Boolean) {
        _uiState.update { it.copy(useCache = enabled) }
        if (proxyStats.value.isRunning) {
            startProxy()
        }
    }

    fun clearLogs() {
        proxyServer.clearLogs()
    }

    fun clearDnsCache() {
        dnsCache.clear()
        _uiState.update { it.copy(dnsCacheCount = 0) }
    }

    fun updateNativeTestUrl(url: String) {
        _uiState.update { it.copy(nativeTestUrl = url) }
    }

    fun updateDnsTestDomain(domain: String) {
        _uiState.update { it.copy(dnsTestDomain = domain) }
    }

    fun executeNativeRequest(method: String = "GET") {
        val state = _uiState.value
        val url = state.nativeTestUrl.trim()
        if (url.isEmpty()) return

        _uiState.update { it.copy(isNativeTestRunning = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val isRunning = proxyStats.value.isRunning
            val port = if (isRunning) state.proxyPort else null

            val response = nativeHttpClient.executeRequest(
                url = url,
                method = method,
                proxyPort = port
            )

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isNativeTestRunning = false,
                        lastNativeResponse = response,
                        dnsCacheCount = dnsCache.size()
                    )
                }
            }
        }
    }

    fun runDnsDiagnostics(targetDomain: String? = null) {
        val domain = (targetDomain ?: _uiState.value.dnsTestDomain).trim()
        if (domain.isEmpty()) return

        _uiState.update { it.copy(isDnsTestRunning = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value

            // 1. Resolve via Local System DNS
            val localStart = System.currentTimeMillis()
            var localIps = emptyList<String>()
            var localError: String? = null
            try {
                val addrs = InetAddress.getAllByName(domain)
                localIps = addrs.map { it.hostAddress ?: "" }.filter { it.isNotEmpty() }
            } catch (e: Exception) {
                localError = e.message ?: "Local resolution failed"
            }
            val localDuration = System.currentTimeMillis() - localStart

            // 2. Resolve via Custom in-app DNS server
            val customResult = dnsQueryClient.resolve(
                domain = domain,
                dnsServerIp = state.customDnsIp,
                dnsServerPort = state.customDnsPort,
                useDoh = state.useDoh,
                useCache = false // Force fresh lookup for diagnostic comparison
            )

            // 3. Classify difference with intelligent CDN & Bogon detection
            val verdict = DnsPollutionClassifier.classify(
                localIps = localIps,
                localError = localError,
                upstreamIps = customResult.ips,
                upstreamError = customResult.errorMessage
            )

            val isPolluted = verdict == DnsVerdictType.POLLUTION_BLOCKED || verdict == DnsVerdictType.POLLUTION_BOGON

            val comparison = DnsComparisonResult(
                domain = domain,
                localSystemIps = localIps,
                localLatencyMs = localDuration,
                localError = localError,
                customDnsIps = customResult.ips,
                customDnsServer = "${state.customDnsIp}:${state.customDnsPort}",
                customLatencyMs = customResult.latencyMs,
                customProtocol = customResult.protocol,
                customError = customResult.errorMessage,
                isPollutedOrDifferent = isPolluted,
                verdict = verdict
            )

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isDnsTestRunning = false,
                        lastDnsComparison = comparison,
                        dnsCacheCount = dnsCache.size()
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        proxyServer.stop()
        WebViewProxyManager.clearProxy()
    }
}
