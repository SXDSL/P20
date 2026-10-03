package com.p20.roulette

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.URLConnection

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("P20", "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                return true
            }
        }
        webView.webViewClient = AppViewClient()
        setContentView(webView)
        webView.loadUrl(HOST_URL)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    companion object {
        const val HOST_URL = "https://app.local/"
    }
}

private class AppViewClient : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val url = request?.url ?: return null
        if (url.host != "app.local") return null
        val path = url.path ?: "/"
        return when {
            path == "/api/image" -> apiResponse(url.query ?: "", ImageApi::handle)
            path == "/api/artist" -> apiResponse(url.query ?: "", ImageApi::handleArtist)
            path == "/" || path == "/index.html" -> assetResponse(view, "index.html")
            else -> assetResponse(view, path.removePrefix("/"))
        }
    }

    private fun apiResponse(
        query: String,
        handler: (String) -> Pair<Int, String>,
    ): WebResourceResponse {
        val (code, body) = handler(query)
        val reason = when (code) {
            200 -> "OK"
            404 -> "Not Found"
            else -> "Error"
        }
        return WebResourceResponse(
            "application/json",
            "UTF-8",
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)),
        ).apply {
            setStatusCodeAndReasonPhrase(code, reason)
            setResponseHeaders(mapOf("Cache-Control" to "no-store"))
        }
    }

    private fun assetResponse(view: WebView?, assetPath: String): WebResourceResponse? {
        return try {
            val stream = view!!.context.assets.open(assetPath)
            val mime = URLConnection.guessContentTypeFromStream(stream) ?: "application/octet-stream"
            WebResourceResponse(mime, "UTF-8", stream)
        } catch (e: Exception) {
            null
        }
    }
}
