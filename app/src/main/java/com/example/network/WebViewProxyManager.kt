package com.example.network

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object WebViewProxyManager {
    private const val TAG = "WebViewProxyManager"
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private var isProxySet = false

    fun isSupported(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    }

    fun applyProxy(port: Int, onComplete: ((Boolean) -> Unit)? = null) {
        if (!isSupported()) {
            Log.w(TAG, "WebViewFeature.PROXY_OVERRIDE is not supported on this device/ROM.")
            onComplete?.invoke(false)
            return
        }

        try {
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:$port")
                .addBypassRule("<-loopback>") // Route even loopback if needed, or default
                .build()

            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                executor,
                Runnable {
                    isProxySet = true
                    Log.i(TAG, "Successfully set WebView proxy to 127.0.0.1:$port")
                    onComplete?.invoke(true)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set WebView proxy: ${e.message}", e)
            onComplete?.invoke(false)
        }
    }

    fun clearProxy(onComplete: ((Boolean) -> Unit)? = null) {
        if (!isSupported() || !isProxySet) {
            onComplete?.invoke(true)
            return
        }

        try {
            ProxyController.getInstance().clearProxyOverride(
                executor,
                Runnable {
                    isProxySet = false
                    Log.i(TAG, "WebView proxy cleared")
                    onComplete?.invoke(true)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear WebView proxy: ${e.message}", e)
            onComplete?.invoke(false)
        }
    }

    fun isApplied(): Boolean = isProxySet
}
