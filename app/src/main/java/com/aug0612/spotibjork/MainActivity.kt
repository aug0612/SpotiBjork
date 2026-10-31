package com.aug0612.spotibjork

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.aug0612.spotibjork.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adBlocker = AdBlocker(this)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        loadSpotify()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                    if (adBlocker.isAdUrl(url)) {
                        return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    injectCustomCSS(view)
                }
            }
        }
    }

    private fun injectCustomCSS(webView: WebView?) {
        val css = """
            body { font-family: -apple-system, BlinkMacSystemFont, sans-serif !important; }
            .main-view-container { backdrop-filter: blur(20px) !important; background: rgba(255,255,255,0.7) !important; border-radius: 16px !important; }
            .ButtonInner-buttonPrimary { background: linear-gradient(135deg, #FC4C02 0%, #FC6A5D 100%) !important; border-radius: 24px !important; }
            .ads-container, .ad-slot { display: none !important; }
        """.trimIndent()

        val js = "var style = document.createElement('style'); style.innerHTML = " + '"' + css + '"' + "; document.head.appendChild(style);"
        webView?.evaluateJavascript(js, null)
    }

    private fun loadSpotify() {
        binding.webView.loadUrl("https://open.spotify.com")
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
