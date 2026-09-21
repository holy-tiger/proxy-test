package com.example.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class NativeHttpResponse(
    val url: String,
    val method: String,
    val statusCode: Int,
    val statusMessage: String,
    val durationMs: Long,
    val headers: Map<String, String>,
    val body: String,
    val isThroughProxy: Boolean,
    val proxyHost: String?,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class NativeHttpClient {

    fun executeRequest(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        requestBody: String? = null,
        proxyPort: Int? = null
    ): NativeHttpResponse {
        val startTime = System.currentTimeMillis()
        val isUsingProxy = proxyPort != null

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (proxyPort != null) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
            clientBuilder.proxy(proxy)
        }

        val client = clientBuilder.build()

        try {
            val requestBuilder = Request.Builder().url(url)

            for ((k, v) in headers) {
                requestBuilder.header(k, v)
            }
            if (!headers.containsKey("User-Agent")) {
                requestBuilder.header("User-Agent", "DnsProxyAndroid/1.0 (Mobile)")
            }

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "HEAD" -> requestBuilder.head()
                "POST" -> {
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val body = (requestBody ?: "").toRequestBody(mediaType)
                    requestBuilder.post(body)
                }
                else -> requestBuilder.get()
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                val responseHeaders = mutableMapOf<String, String>()
                for (name in response.headers.names()) {
                    responseHeaders[name] = response.header(name) ?: ""
                }

                val bodyStr = response.body?.string() ?: ""
                return NativeHttpResponse(
                    url = url,
                    method = method,
                    statusCode = response.code,
                    statusMessage = response.message,
                    durationMs = duration,
                    headers = responseHeaders,
                    body = bodyStr,
                    isThroughProxy = isUsingProxy,
                    proxyHost = if (isUsingProxy) "127.0.0.1:$proxyPort" else null,
                    isSuccess = response.isSuccessful
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            return NativeHttpResponse(
                url = url,
                method = method,
                statusCode = 0,
                statusMessage = "Error",
                durationMs = duration,
                headers = emptyMap(),
                body = "",
                isThroughProxy = isUsingProxy,
                proxyHost = if (isUsingProxy) "127.0.0.1:$proxyPort" else null,
                isSuccess = false,
                errorMessage = e.message ?: e.toString()
            )
        }
    }
}
