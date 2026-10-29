package com.ansub.tvhome;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

// Having notification access is what lets Android hand us the active media sessions. We never read
// notifications; this only follows the player (Issa Music first) and fetches synced lyrics from lrclib.net.
public class MediaListener extends NotificationListenerService {
    private MediaSessionManager msm;
    private final List<MediaController> watched = new ArrayList<>();
    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata m) { refresh(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override public void onActiveSessionsChanged(List<MediaController> cs) { attach(cs); }
    };

    @Override public void onListenerConnected() {
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, MediaListener.class);
            msm.addOnActiveSessionsChangedListener(changed, me);
            attach(msm.getActiveSessions(me));
        } catch (Exception ignored) {}
    }

    @Override public void onListenerDisconnected() {
        for (MediaController c : watched) try { c.unregisterCallback(cb); } catch (Exception ignored) {}
        watched.clear();
        Music.playing = false; Music.active = false;
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {}
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {}

    private void attach(List<MediaController> cs) {
        for (MediaController c : watched) try { c.unregisterCallback(cb); } catch (Exception ignored) {}
        watched.clear();
        if (cs != null) for (MediaController c : cs) {
            try { c.registerCallback(cb); watched.add(c); } catch (Exception ignored) {}
        }
        refresh();
    }

    private void refresh() {
        MediaController best = null;
        for (MediaController c : watched) {
            PlaybackState s = c.getPlaybackState();
            boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
            if (c.getPackageName().equals("com.musik.app") && (playing || best == null)) { best = c; if (playing) break; continue; }
            if (playing && (best == null || !best.getPackageName().equals("com.musik.app"))) best = c;
        }
        if (best == null) { Music.playing = false; Music.active = false; return; }
        PlaybackState s = best.getPlaybackState();
        MediaMetadata m = best.getMetadata();
        Music.active = m != null;
        Music.playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        if (s != null) {
            float sp = s.getPlaybackSpeed() == 0 ? 1f : s.getPlaybackSpeed();
            long pos = Math.max(0, s.getPosition());
            long lu = s.getLastPositionUpdateTime();
            if (Music.playing && lu > 0) pos += (long) ((SystemClock.elapsedRealtime() - lu) * sp);
            Music.posMs = pos;
            Music.stampMs = SystemClock.uptimeMillis();
            Music.speed = s.getPlaybackSpeed() == 0 ? 1f : s.getPlaybackSpeed();
        }
        try { // the next song in the player's queue, when the player shares one
            String nt = "", na = ""; Bitmap nb = null;
            java.util.List<android.media.session.MediaSession.QueueItem> q = best.getQueue();
            if (q != null && s != null) {
                long id = s.getActiveQueueItemId();
                for (int i = 0; i < q.size() - 1; i++) if (q.get(i).getQueueId() == id) {
                    android.media.MediaDescription d = q.get(i + 1).getDescription();
                    nt = nz(d.getTitle() == null ? null : d.getTitle().toString());
                    na = nz(d.getSubtitle() == null ? null : d.getSubtitle().toString());
                    nb = d.getIconBitmap();
                    break;
                }
            }
            if (nt.isEmpty()) { // fallback: a player can also share it as session extras (nextTitle / nextArtist)
                android.os.Bundle ex = best.getExtras();
                if (ex != null) { nt = nz(ex.getString("nextTitle")); na = nz(ex.getString("nextArtist")); }
            }
            Music.nextTitle = nt; Music.nextArtist = na; Music.nextArt = nb;
        } catch (Exception ignored) {}
        if (m == null) return;
        String title = nz(m.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = nz(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
        if (artist.isEmpty()) artist = nz(m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
        String album = nz(m.getString(MediaMetadata.METADATA_KEY_ALBUM));
        long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art != null && (art.getWidth() > 600)) art = Bitmap.createScaledBitmap(art, 600, 600 * art.getHeight() / art.getWidth(), true);
        Music.durationMs = dur;
        if (art != null) Music.art = art;
        String key = title + "|" + artist;
        if (!key.equals(Music.trackKey)) {
            Music.trackKey = key;
            Music.title = title; Music.artist = artist; Music.album = album;
            if (art == null) Music.art = null;
            Music.lyricT = null; Music.lyricL = null;
            fetchLyrics(title, artist, album, dur, key);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private void fetchLyrics(final String title, final String artist, final String album, final long dur, final String key) {
        if (title.isEmpty()) return;
        Music.lyricsLoading = true;
        new Thread(new Runnable() {
            @Override public void run() {
                String lrc = null;
                try {
                    String u = "https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                        + (album.isEmpty() ? "" : "&album_name=" + enc(album)) + (dur > 0 ? "&duration=" + dur / 1000 : "");
                    String body = http(u);
                    if (body != null) lrc = new JSONObject(body).optString("syncedLyrics", null);
                    if (lrc == null || lrc.isEmpty() || lrc.equals("null")) {
                        body = http("https://lrclib.net/api/search?track_name=" + enc(title) + "&artist_name=" + enc(artist));
                        if (body != null) {
                            JSONArray a = new JSONArray(body);
                            for (int i = 0; i < a.length() && i < 8; i++) {
                                String l = a.getJSONObject(i).optString("syncedLyrics", "");
                                if (l.length() > 10 && !l.equals("null")) { lrc = l; break; }
                            }
                        }
                    }
                } catch (Exception ignored) {}
                if (key.equals(Music.trackKey)) {
                    if (lrc != null && lrc.length() > 10 && !lrc.equals("null")) Music.parseLrc(lrc);
                    Music.lyricsLoading = false;
                }
            }
        }).start();
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String http(String u) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "TVHome/1.7");
            if (c.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            r.close();
            return sb.toString();
        } catch (Exception e) { return null; }
        finally { if (c != null) c.disconnect(); }
    }
}
