package com.spotibjork.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Service de premier plan : garde le processus (et donc la WebView) en vie pendant la lecture,
 * affiche la notification média et gère les boutons de l'écran de verrouillage.
 */
public class PlayerService extends Service {

    static final String ACTION_UPDATE = "com.spotibjork.app.UPDATE";
    static final String ACTION_PLAYPAUSE = "com.spotibjork.app.PLAYPAUSE";
    static final String ACTION_NEXT = "com.spotibjork.app.NEXT";
    static final String ACTION_PREV = "com.spotibjork.app.PREV";

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIF_ID = 1;

    static volatile PlayerService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private PowerManager.WakeLock wakeLock;

    private String title = "Spotibjork";
    private String artist = "";
    private String album = "";
    private String artUrl = "";
    private Bitmap art;
    private boolean playing = false;
    private boolean foregrounded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        session = new MediaSession(this, "Spotibjork");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { if (!playing) MainActivity.sendCommand("playpause"); }
            @Override public void onPause() { if (playing) MainActivity.sendCommand("playpause"); }
            @Override public void onSkipToNext() { MainActivity.sendCommand("next"); }
            @Override public void onSkipToPrevious() { MainActivity.sendCommand("previous"); }
        });
        session.setActive(true);

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spotibjork:playback");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_UPDATE:
                    update(intent.getStringExtra("title"), intent.getStringExtra("artist"),
                            intent.getStringExtra("album"), intent.getStringExtra("art"),
                            intent.getBooleanExtra("playing", false));
                    break;
                case ACTION_PLAYPAUSE: MainActivity.sendCommand("playpause"); break;
                case ACTION_NEXT: MainActivity.sendCommand("next"); break;
                case ACTION_PREV: MainActivity.sendCommand("previous"); break;
                default: break;
            }
        }
        return START_NOT_STICKY;
    }

    /** Peut être appelé depuis n'importe quel thread. */
    void update(String t, String a, String al, String artworkUrl, boolean isPlaying) {
        main.post(() -> {
            title = (t == null || t.isEmpty()) ? "Spotibjork" : t;
            artist = a == null ? "" : a;
            album = al == null ? "" : al;
            playing = isPlaying;
            String newArt = artworkUrl == null ? "" : artworkUrl;
            if (!newArt.equals(artUrl)) {
                artUrl = newArt;
                art = null;
                if (newArt.startsWith("https://")) loadArt(newArt);
            }
            ensureForeground();
            apply();
        });
    }

    private void ensureForeground() {
        if (foregrounded) return;
        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
            foregrounded = true;
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void apply() {
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album);
        if (art != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(mb.build());

        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());

        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());

        if (playing && !wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 60 * 1000L);
        else if (!playing && wakeLock.isHeld()) wakeLock.release();
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(title)
                .setContentText(artist.isEmpty() ? album : artist)
                .setLargeIcon(art)
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action(android.R.drawable.ic_media_previous, "Précédent", ACTION_PREV, 1))
                .addAction(playing
                        ? action(android.R.drawable.ic_media_pause, "Pause", ACTION_PLAYPAUSE, 2)
                        : action(android.R.drawable.ic_media_play, "Lecture", ACTION_PLAYPAUSE, 2))
                .addAction(action(android.R.drawable.ic_media_next, "Suivant", ACTION_NEXT, 3))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private Notification.Action action(int icon, String label, String act, int code) {
        PendingIntent pi = PendingIntent.getService(this, code,
                new Intent(this, PlayerService.class).setAction(act),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }

    private void loadArt(final String url) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                try (InputStream in = c.getInputStream()) {
                    final Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) {
                        main.post(() -> {
                            if (url.equals(artUrl)) {
                                art = b;
                                apply();
                            }
                        });
                    }
                }
            } catch (Exception ignored) { }
        }).start();
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (session != null) session.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
