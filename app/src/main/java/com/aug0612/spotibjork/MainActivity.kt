package com.aug0612.spotibjork

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.aug0612.spotibjork.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adBlocker = AdBlocker(this)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MATERIAL YOU: Applique les couleurs dynamiques du système
        DynamicColors.applyToActivityIfAvailable(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        loadSpotifyPC()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true

                // VERSION PC: Force l'affichage desktop
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                // Désactive le zoom mobile
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false

                // Force le mode desktop
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Cache pour de meilleures performances
                cacheMode = WebSettings.LOAD_DEFAULT
                setGeolocationEnabled(false)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                    // Bloque les pubs
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
                    injectAppleMusicCSS(view)
                    injectPCModeJS(view)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                }
            }
        }
    }

    private fun injectAppleMusicCSS(webView: WebView?) {
        val css = """
            /* Apple Music Style */
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Roboto, sans-serif !important;
                -webkit-font-smoothing: antialiased !important;
            }

            /* Glassmorphism effects */
            .Root__nav-bar, .Root__main-view, .Root__now-playing-bar {
                backdrop-filter: blur(20px) !important;
                -webkit-backdrop-filter: blur(20px) !important;
                background: rgba(255, 255, 255, 0.7) !important;
                border: 1px solid rgba(255, 255, 255, 0.2) !important;
            }

            /* Dark mode glass */
            @media (prefers-color-scheme: dark) {
                .Root__nav-bar, .Root__main-view, .Root__now-playing-bar {
                    background: rgba(28, 28, 30, 0.8) !important;
                    border: 1px solid rgba(255, 255, 255, 0.1) !important;
                }
            }

            /* Apple Music red accent */
            .ButtonInner-buttonPrimary, .ButtonInner-buttonPrimary:hover,
            .main-playlistItem-playButton, .main-playlistItem-playButton:hover {
                background: linear-gradient(135deg, #FC4C02 0%, #FC6A5D 100%) !important;
                border: none !important;
                border-radius: 24px !important;
                box-shadow: 0 4px 12px rgba(252, 76, 2, 0.3) !important;
            }

            /* Rounded corners everywhere */
            .main-card-card, .main-trackList-trackListRow, .main-rootlist-rootlistItem {
                border-radius: 12px !important;
                transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
            }

            /* Smooth animations */
            * {
                transition: background-color 0.3s ease, color 0.3s ease !important;
            }

            /* Hide ads */
            .ads-container, .ad-slot, [data-testid*="ad"], .main-trackList-adSlot {
                display: none !important;
                visibility: hidden !important;
            }

            /* Now playing bar glass effect */
            .Root__now-playing-bar {
                border-radius: 16px 16px 0 0 !important;
                margin: 0 8px !important;
            }

            /* Progress bar style */
            .playback-bar__progress-bar {
                background: linear-gradient(90deg, #FC4C02, #FC6A5D) !important;
            }
        """.trimIndent()

        val js = """
            (function() {
                var head = document.getElementsByTagName('head')[0];
                if (head) {
                    var style = document.createElement('style');
                    style.innerHTML = `${css.replace("$", "\\$")}`;
                    head.appendChild(style);
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    private fun injectPCModeJS(webView: WebView?) {
        val js = """
            (function() {
                // Force desktop layout
                var meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                }

                // Remove mobile-specific elements
                var mobileElements = document.querySelectorAll('[class*="mobile"], [class*="Mobile"]');
                mobileElements.forEach(function(el) {
                    if (el.tagName !== 'BODY') {
                        el.style.display = 'none';
                    }
                });
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    private fun loadSpotifyPC() {
        // Charge la version PC de Spotify Web
        binding.webView.loadUrl("https://open.spotify.com/")
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
