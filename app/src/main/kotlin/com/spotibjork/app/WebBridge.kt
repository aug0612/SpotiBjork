package com.spotibjork.app

import android.webkit.JavascriptInterface

/** Reçoit du JavaScript de la page le titre, l'artiste et l'état de lecture. */
class WebBridge {
    @JavascriptInterface
    fun update(title: String?, artist: String?, playing: Boolean) {
        PlaybackService.instance?.update(title ?: "", artist ?: "", playing)
    }
}
