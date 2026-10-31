package com.spotibjork.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#121212"))
        root.fitsSystemWindows = true
        setContentView(root)

        webView = KeepAliveWebView(this)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = Config.USER_AGENT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebBridge(), "SpotibjorkBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(BRIDGE_JS, null)
                if (Config.LAYOUT_WIDTH_PX > 0) {
                    view?.evaluateJavascript(viewportJs(Config.LAYOUT_WIDTH_PX), null)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                // Autorise uniquement le DRM (Widevine) nécessaire à la lecture.
                val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                if (allowed.isEmpty()) {
                    request.deny()
                } else {
                    request.grant(allowed.toTypedArray())
                }
            }
        }

        root.addView(
            webView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        WebHolder.webView = webView
        webView.loadUrl(Config.START_URL)

        startForegroundService(Intent(this, PlaybackService::class.java))
    }

    override fun onPause() {
        super.onPause()
        // Garde le lecteur actif quand l'app passe en arrière-plan.
        webView.onResume()
        webView.resumeTimers()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        WebHolder.webView = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        if (isFinishing) {
            stopService(Intent(this, PlaybackService::class.java))
        }
        super.onDestroy()
    }

    companion object {
        // Lit la session média de la page et l'envoie à l'application.
        private const val BRIDGE_JS = """
(function () {
  if (window.__spotibjork) { return; }
  window.__spotibjork = true;
  setInterval(function () {
    try {
      var ms = navigator.mediaSession;
      var md = ms && ms.metadata;
      var els = document.querySelectorAll('audio,video');
      var playing = false;
      for (var i = 0; i < els.length; i++) {
        if (!els[i].paused && !els[i].ended) { playing = true; }
      }
      SpotibjorkBridge.update(md ? (md.title || '') : '', md ? (md.artist || '') : '', playing);
    } catch (e) {}
  }, 1500);
})();
"""

        private fun viewportJs(width: Int): String =
            "(function(){var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                "m.content='width=$width, initial-scale=1';})();"
    }
}
