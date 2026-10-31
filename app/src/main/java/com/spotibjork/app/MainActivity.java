package com.spotibjork.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;

/** Affiche le lecteur web de Spotify dans une WebView et relaie les infos de lecture au service. */
public class MainActivity extends Activity {

    private static final String HOME = "https://open.spotify.com/";

    /** WebView courante, pour que le service puisse envoyer lecture/pause/suivant/précédent. */
    private static WebView current;

    private WebView web;

    /** Script injecté : relève toutes les secondes les métadonnées et l'état de lecture de la page. */
    private static final String MEDIA_JS = """
            (function () {
              if (window.__sbjMedia) return;
              window.__sbjMedia = true;
              var last = '';
              setInterval(function () {
                try {
                  var ms = navigator.mediaSession;
                  var m = ms ? ms.metadata : null;
                  var playing = !!ms && ms.playbackState === 'playing';
                  var art = '';
                  if (m && m.artwork && m.artwork.length) {
                    art = m.artwork[m.artwork.length - 1].src || '';
                  }
                  var t = m ? (m.title || '') : '';
                  var a = m ? (m.artist || '') : '';
                  var al = m ? (m.album || '') : '';
                  var key = [t, a, al, art, playing].join('|');
                  if (key !== last) {
                    last = key;
                    SpotibjorkBridge.onMedia(t, a, al, art, playing);
                  }
                } catch (e) {}
              }, 1000);
            })();
            """;

    /** Clique sur un bouton du lecteur web (appelé depuis le service). */
    static void sendCommand(String command) {
        final WebView w = current;
        if (w == null) return;
        String testId;
        switch (command) {
            case "next": testId = "control-button-skip-forward"; break;
            case "previous": testId = "control-button-skip-back"; break;
            default: testId = "control-button-playpause"; break;
        }
        final String js = "(function(){var e=document.querySelector('[data-testid=\"" + testId
                + "\"]');if(e)e.click();})();";
        w.post(() -> w.evaluateJavascript(js, null));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        web = new WebView(this);
        web.setBackgroundColor(0xFF121212);
        setContentView(web);
        current = web;

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setUseWideViewPort(false);
        // Spotify renvoie une page « télécharge l'app » aux navigateurs mobiles : on se présente en bureau.
        s.setUserAgentString(s.getUserAgentString()
                .replaceAll("\\(Linux; Android[^)]*\\)", "(X11; Linux x86_64)")
                .replace(" Mobile", "")
                .replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "SpotibjorkBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost();
                boolean inside = host.endsWith("spotify.com") || host.endsWith("scdn.co")
                        || host.endsWith("spotifycdn.com");
                if (inside) return false;
                if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) { }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(MEDIA_JS, null);
            }
        });

        // Lecture protégée (Widevine) : Spotify la demande via cette permission.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    List<String> ok = new ArrayList<>();
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) ok.add(r);
                    }
                    if (ok.isEmpty()) request.deny();
                    else request.grant(ok.toArray(new String[0]));
                });
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME);
        }
    }

    /** Appelé par le JavaScript de la page ; tourne sur un thread de fond. */
    private class Bridge {
        @JavascriptInterface
        public void onMedia(String title, String artist, String album, String art, boolean playing) {
            PlayerService service = PlayerService.instance;
            if (service != null) {
                service.update(title, artist, album, art, playing);
            } else if (playing) {
                Intent i = new Intent(getApplicationContext(), PlayerService.class)
                        .setAction(PlayerService.ACTION_UPDATE)
                        .putExtra("title", title)
                        .putExtra("artist", artist)
                        .putExtra("album", album)
                        .putExtra("art", art)
                        .putExtra("playing", true);
                try {
                    startForegroundService(i);
                } catch (Exception ignored) { }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        // Pas de web.onPause() : la musique doit continuer en arrière-plan.
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (current == web) current = null;
        stopService(new Intent(this, PlayerService.class));
        web.destroy();
        super.onDestroy();
    }
}
