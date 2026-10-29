package com.ansub.tvhome;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

// What is playing right now. Written by MediaListener, read by the music widget.
final class Music {
    static volatile boolean playing, active, shown;
    static volatile String title = "", artist = "", album = "";
    static volatile Bitmap art;
    static volatile long durationMs, posMs, stampMs;
    static volatile float speed = 1f;
    static volatile String trackKey = "";
    static volatile String nextTitle = "", nextArtist = "";
    static volatile Bitmap nextArt;
    static volatile int sel = 2; // focused on-screen button: 0 back, 1 previous, 2 play/pause, 3 next
    static volatile long controlsUntil; // uptime ms until which the buttons are shown
    // synced lyrics: times (ms) and lines; null while unknown
    static volatile long[] lyricT;
    static volatile String[] lyricL;
    static volatile boolean lyricsLoading;

    static long position(long now) {
        if (!playing) return posMs;
        long p = posMs + (long) ((now - stampMs) * speed);
        return durationMs > 0 ? Math.min(p, durationMs) : p;
    }

    // "[01:23.45] text" lines
    static void parseLrc(String lrc) {
        List<Long> ts = new ArrayList<>();
        List<String> ls = new ArrayList<>();
        for (String line : lrc.split("\n")) {
            int e = line.indexOf(']');
            if (!line.startsWith("[") || e < 0) continue;
            String tag = line.substring(1, e);
            try {
                int c = tag.indexOf(':');
                double sec = Double.parseDouble(tag.substring(c + 1));
                long ms = (long) ((Integer.parseInt(tag.substring(0, c)) * 60 + sec) * 1000);
                ts.add(ms);
                ls.add(line.substring(e + 1).trim());
            } catch (Exception ignored) {}
        }
        long[] t = new long[ts.size()];
        for (int i = 0; i < t.length; i++) t[i] = ts.get(i);
        lyricT = t;
        lyricL = ls.toArray(new String[0]);
    }
}
