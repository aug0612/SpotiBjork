package com.spotibjork.app

import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * WebView qui reste "visible" pour Chromium même quand l'écran est éteint ou l'app en arrière-plan,
 * afin que le lecteur Spotify continue d'enchaîner les morceaux.
 */
class KeepAliveWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.GONE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        }
    }
}
