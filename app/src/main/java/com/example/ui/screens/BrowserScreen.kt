package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.ProxyViewModel
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

data class WebBookmark(
    val title: String,
    val url: String
)

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: ProxyViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.proxyStats.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var urlInput by remember { mutableStateOf("https://cloudflare.com/cdn-cgi/trace") }
    var currentUrl by remember { mutableStateOf(urlInput) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("") }
    var pageError by remember { mutableStateOf<String?>(null) }
    var failedUrl by remember { mutableStateOf("") }
    var isDnsRelatedError by remember { mutableStateOf(false) }

    val bookmarks = remember {
        listOf(
            WebBookmark("Cloudflare 诊断", "https://cloudflare.com/cdn-cgi/trace"),
            WebBookmark("维基百科", "https://en.m.wikipedia.org"),
            WebBookmark("IP 查询", "https://httpbin.org/ip"),
            WebBookmark("DuckDuckGo", "https://html.duckduckgo.com"),
            WebBookmark("GitHub 状态", "https://www.githubstatus.com")
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Proxy Security Status Banner
        Surface(
            color = if (stats.isRunning && uiState.isWebViewProxyActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else if (stats.isRunning) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (stats.isRunning) StatusGreen else StatusRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (stats.isRunning) {
                            "WebView 经本地代理转发 (127.0.0.1:${stats.port}) | DNS: ${stats.dnsServerIp}"
                        } else {
                            "⚠️ 代理未运行，WebView 目前使用系统直连"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (stats.isRunning) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }

                if (!uiState.isWebViewProxySupported) {
                    Text(
                        text = "WebView 代理需系统 WebKit 支持",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // 2. Navigation bar with Address Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webViewInstance?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "后退"
                )
            }

            IconButton(
                onClick = { webViewInstance?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "前进"
                )
            }

            IconButton(
                onClick = { webViewInstance?.reload() },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新"
                )
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("browser_url_input"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                leadingIcon = {
                    Icon(
                        imageVector = if (urlInput.startsWith("https://")) Icons.Default.Lock else Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (urlInput.startsWith("https://")) StatusGreen else MaterialTheme.colorScheme.primary
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        var target = urlInput.trim()
                        if (!target.startsWith("http://") && !target.startsWith("https://")) {
                            target = "https://$target"
                            urlInput = target
                        }
                        currentUrl = target
                        webViewInstance?.loadUrl(target)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        }

        // Quick bookmarks row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bookmarks.forEach { bm ->
                FilterChip(
                    selected = currentUrl == bm.url,
                    onClick = {
                        urlInput = bm.url
                        currentUrl = bm.url
                        webViewInstance?.loadUrl(bm.url)
                    },
                    label = { Text(bm.title, fontSize = 11.sp) }
                )
            }
        }

        // Progress indicator
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // 3. Android WebView Interop
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("browser_webview_container")
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        // Set software rendering mode in emulator environment to prevent renderer crashes
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                pageError = null
                                url?.let {
                                    urlInput = it
                                    currentUrl = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let {
                                    urlInput = it
                                    currentUrl = it
                                }
                                currentTitle = view?.title ?: ""
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    val errDesc = error?.description?.toString() ?: "网络连接失败"
                                    val errorCode = error?.errorCode ?: 0
                                    val isTunnelError = errDesc.contains("TUNNEL_CONNECTION_FAILED", ignoreCase = true) ||
                                            errDesc.contains("NAME_NOT_RESOLVED", ignoreCase = true) ||
                                            errorCode == ERROR_CONNECT ||
                                            errorCode == ERROR_HOST_LOOKUP
                                    pageError = errDesc
                                    failedUrl = request.url?.toString() ?: currentUrl
                                    isDnsRelatedError = isTunnelError
                                }
                            }

                            // Catch and safely recover from renderer process crashes (e.g. GPU out-of-memory or emulator crash)
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                isLoading = false
                                val crashed = detail?.didCrash() == true
                                pageError = if (crashed) "浏览器渲染引擎异常崩溃，已自动恢复" else "系统内存不足，渲染引擎已回收"
                                isDnsRelatedError = false
                                // Clean up old instance and reload
                                view?.let {
                                    (it.parent as? ViewGroup)?.removeView(it)
                                    it.destroy()
                                }
                                webViewInstance = null
                                return true // Handled, prevents host app crash
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                if (newProgress == 100) {
                                    isLoading = false
                                }
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                currentTitle = title ?: ""
                            }
                        }

                        webViewInstance = this
                        loadUrl(currentUrl)
                    }
                },
                update = { webView ->
                    // Keep instance reference
                    webViewInstance = webView
                }
            )

            // Friendly Error Overlay
            if (pageError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDnsRelatedError) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDnsRelatedError) Icons.Default.Dns else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDnsRelatedError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isDnsRelatedError) "DNS 解析失败 / 代理隧道无法建立" else "网页加载失败",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isDnsRelatedError) {
                            Text(
                                text = "无法访问目标网页：上游 DNS 服务器 (${stats.dnsServerIp}) 无法解析目标域名或服务器不可达。\n\n因为所有请求都通过本地防污染代理隧道，如果填写的 DNS 地址无效，本地代理无法获取真实 IP，Chromium 内核会返回 ERR_TUNNEL_CONNECTION_FAILED。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        } else {
                            Text(
                                text = "错误详情: $pageError",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "目标网址: $failedUrl",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "上游 DNS: ${stats.dnsServerIp}:53 (${if (uiState.useDoh) "DoH 443 加密" else "标准 UDP 53"})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "本地代理隧道: 127.0.0.1:${stats.port}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!uiState.useDoh && (stats.dnsServerIp == "1.1.1.1" || stats.dnsServerIp == "8.8.8.8")) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "⚠️ 提示: ${stats.dnsServerIp} 的 UDP 53 端口通常被国内防火墙封锁，请开启 DoH 以抗封锁解析。",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!uiState.useDoh) {
                                Button(
                                    onClick = {
                                        viewModel.toggleDoh(true)
                                        pageError = null
                                        webViewInstance?.loadUrl(failedUrl.ifBlank { currentUrl })
                                    }
                                ) {
                                    Text("开启 DoH 重试", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    // Switch to AliDNS (223.5.5.5) which works well on domestic networks
                                    val aliPreset = com.example.dns.DnsServerPreset.PRESETS.firstOrNull { it.id == "alidns" }
                                    if (aliPreset != null) {
                                        viewModel.selectPreset(aliPreset)
                                    }
                                    pageError = null
                                    webViewInstance?.loadUrl(failedUrl.ifBlank { currentUrl })
                                }
                            ) {
                                Text("切换为阿里 DNS", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    pageError = null
                                    webViewInstance?.loadUrl(failedUrl.ifBlank { currentUrl })
                                }
                            ) {
                                Text("重新尝试", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
        }
    }
}
