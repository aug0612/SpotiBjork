package com.spotibjork.app

import android.webkit.WebView

object Config {
    const val START_URL = "https://open.spotify.com"

    // Le lecteur web de Spotify est servi à un navigateur "bureau".
    const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Safari/537.36"

    // Largeur de mise en page forcée, en pixels CSS (ex. 700). 0 = laisser le site décider.
    // Plus la valeur est grande, plus tout est petit mais plus la page ressemble à la version PC.
    const val LAYOUT_WIDTH_PX = 0

    const val TEST_PLAY_PAUSE = "control-button-playpause"
    const val TEST_NEXT = "control-button-skip-forward"
    const val TEST_PREV = "control-button-skip-back"
}

/** Donne au service un accès à la WebView de l'activité pour piloter la lecture. */
object WebHolder {
    @Volatile
    var webView: WebView? = null

    fun click(testId: String) {
        val w = webView ?: return
        w.post {
            w.evaluateJavascript(
                "(function(){var b=document.querySelector('[data-testid=\"$testId\"]');if(b){b.click();}})();",
                null
            )
        }
    }
}
