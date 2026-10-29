package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

// The music widget: cover, title, artist, progress and synced lyrics. Same drawing for the card on the
// home screen (big = false) and for the full-screen black screensaver (big = true).
final class MusicView extends View {
    private final boolean big;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final Typeface bold, reg;
    private float lyricPos; // fractional line index, eased so the lyrics glide
    private long lastDraw;
    private boolean on;

    MusicView(Context c, boolean big, Typeface reg, Typeface bold) {
        super(c);
        this.big = big;
        this.reg = reg != null ? reg : Typeface.create("sans-serif", Typeface.NORMAL);
        this.bold = bold != null ? bold : Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    void setOn(boolean v) {
        on = v;
        if (v) { lastDraw = 0; invalidate(); }
    }

    private Bitmap bgSrc, bgSmall;
    private final Paint bgP = new Paint(Paint.FILTER_BITMAP_FLAG);

    // Whole-screen background made from the cover: shrunk to a few pixels and stretched back up, which
    // blurs it, then dimmed so the text stays readable.
    private void drawBackdrop(Canvas c, int W, int H) {
        Bitmap a = Music.art;
        if (a != bgSrc) {
            bgSrc = a;
            bgSmall = null;
            if (a != null) try { bgSmall = Bitmap.createScaledBitmap(a, 16, Math.max(1, 16 * a.getHeight() / Math.max(1, a.getWidth())), true); } catch (Throwable ignored) {}
        }
        if (bgSmall != null) {
            float s = Math.max((float) W / bgSmall.getWidth(), (float) H / bgSmall.getHeight());
            float dw = bgSmall.getWidth() * s, dh = bgSmall.getHeight() * s;
            r.set((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2);
            c.drawBitmap(bgSmall, null, r, bgP);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xB3000000);
            c.drawRect(0, 0, W, H, p);
        } else {
            c.drawColor(0xFF000000);
        }
    }

    private static String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, Typeface tf, float maxW, boolean center) {
        t.setTypeface(tf); t.setTextSize(size); t.setColor(color);
        t.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
        String out = s;
        if (t.measureText(out) > maxW) {
            int n = t.breakText(out, true, maxW - t.measureText("…"), null);
            out = out.substring(0, Math.max(0, n)) + "…";
        }
        c.drawText(out, x, y, t);
    }

    @Override protected void onDraw(Canvas c) {
        if (!on) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastDraw == 0 ? 0.016f : Math.min(0.1f, (now - lastDraw) / 1000f);
        lastDraw = now;
        int W = getWidth(), H = getHeight();
        float u = W / (big ? 1920f : 824f);
        if (big) drawBackdrop(c, W, H);
        // layout
        float artS, artX, artY, tx, tw, titleS, artistS, lyX, lyW, lyTop, lyS;
        if (big) {
            artS = 420 * u; artX = 250 * u; artY = 150 * u;
            tx = artX + artS + 70 * u; tw = W - tx - 160 * u;
            titleS = 64 * u; artistS = 38 * u;
            lyX = W / 2f; lyW = W - 300 * u; lyTop = 700 * u; lyS = 54 * u;
        } else {
            artS = 250 * u; artX = 40 * u; artY = 40 * u;
            tx = artX + artS + 36 * u; tw = W - tx - 40 * u;
            titleS = 44 * u; artistS = 28 * u;
            lyX = W / 2f; lyW = W - 80 * u; lyTop = 330 * u; lyS = 33 * u;
            // glossy black card
            r.set(0, 0, W, H);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, W, H, new int[] {0xFF15151A, 0xFF050506, 0xFF0C0C10}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 36 * u, 36 * u, p);
            p.setShader(new LinearGradient(0, 0, 0, H * 0.45f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            clip.reset(); clip.addRoundRect(r, 36 * u, 36 * u, Path.Direction.CW);
            c.save(); c.clipPath(clip); c.drawRect(0, 0, W, H * 0.45f, p); c.restore();
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1, 2 * u)); p.setColor(0x24FFFFFF);
            c.drawRoundRect(r, 36 * u, 36 * u, p);
            p.setStyle(Paint.Style.FILL);
        }
        // cover
        r.set(artX, artY, artX + artS, artY + artS);
        Bitmap art = Music.art;
        p.setColor(0x66000000);
        c.drawRoundRect(artX - 4 * u, artY + 10 * u, artX + artS + 4 * u, artY + artS + 22 * u, 30 * u, 30 * u, p);
        clip.reset(); clip.addRoundRect(r, 26 * u, 26 * u, Path.Direction.CW);
        c.save(); c.clipPath(clip);
        if (art != null) {
            int bw = art.getWidth(), bh = art.getHeight(), side = Math.min(bw, bh);
            c.drawBitmap(art, new android.graphics.Rect((bw - side) / 2, (bh - side) / 2, (bw + side) / 2, (bh + side) / 2), r, p);
        } else {
            p.setShader(new LinearGradient(artX, artY, artX + artS, artY + artS, 0xFF3A3A48, 0xFF14141A, Shader.TileMode.CLAMP));
            c.drawRect(r, p); p.setShader(null);
            text(c, "♪", artX + artS / 2, artY + artS * 0.68f, artS * 0.5f, 0x88FFFFFF, bold, artS, true);
        }
        // gloss on the cover
        p.setShader(new LinearGradient(artX, artY, artX + artS * 0.8f, artY + artS * 0.8f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(r, p); p.setShader(null);
        c.restore();

        // text
        boolean info = Music.active && !Music.title.isEmpty();
        String title = !info || Music.title.isEmpty() ? "Spelar musik" : Music.title;
        float ty = artY + artS * 0.30f;
        text(c, title, tx, ty, titleS, 0xFFFFFFFF, bold, tw, false);
        text(c, info ? Music.artist : "Issa Music", tx, ty + artistS * 1.5f, artistS, 0xB3FFFFFF, reg, tw, false);
        // equalizer bars + state
        float ex = tx, ey = ty + artistS * 3.1f;
        for (int i = 0; i < 4; i++) {
            float h = Music.shown ? (0.25f + 0.75f * Math.abs((float) Math.sin(now / 1000f * (3f + i * 1.3f) + i))) * 30 * u : 6 * u;
            p.setColor(0xFFFFFFFF);
            c.drawRoundRect(ex + i * 12 * u, ey - h, ex + i * 12 * u + 7 * u, ey, 3 * u, 3 * u, p);
        }
        // progress
        long pos = Music.position(now), dur = Music.durationMs;
        float by = artY + artS - 36 * u, bw = tw;
        p.setColor(0x33FFFFFF);
        c.drawRoundRect(tx, by, tx + bw, by + 8 * u, 4 * u, 4 * u, p);
        if (dur > 0) {
            p.setColor(0xFFFFFFFF);
            c.drawRoundRect(tx, by, tx + bw * Math.min(1f, pos / (float) dur), by + 8 * u, 4 * u, 4 * u, p);
            text(c, mmss(pos), tx, by + 38 * u, 24 * u * (big ? 1.2f : 1f), 0x99FFFFFF, reg, 200 * u, false);
            t.setTextAlign(Paint.Align.RIGHT); t.setColor(0x99FFFFFF); t.setTextSize(24 * u * (big ? 1.2f : 1f)); t.setTypeface(reg);
            c.drawText(mmss(dur), tx + bw, by + 38 * u, t);
        }

        // lyrics
        long[] lt = Music.lyricT; String[] ll = Music.lyricL;
        if (info && lt != null && lt.length > 0) {
            int cur = -1;
            for (int i = 0; i < lt.length; i++) if (lt[i] <= pos + 150) cur = i; else break;
            float tgt = Math.max(0, cur);
            lyricPos += (tgt - lyricPos) * Math.min(1f, dt * 7f);
            float step = lyS * 1.7f;
            c.save();
            c.clipRect(lyX - lyW / 2, lyTop - step * 1.2f, lyX + lyW / 2, lyTop + step * (big ? 3.6f : 2.2f));
            for (int i = Math.max(0, (int) lyricPos - 2); i < Math.min(lt.length, (int) lyricPos + 5); i++) {
                float off = i - lyricPos;
                float y = lyTop + off * step;
                float near = Math.max(0f, 1f - Math.abs(off));
                float alpha = Math.max(0.12f, 1f - Math.abs(off) * 0.45f);
                int a = Math.round(255 * alpha * (i == cur ? 1f : 0.55f + 0.0f * near));
                String line = ll[i].isEmpty() ? "♪" : ll[i];
                float size = lyS * (0.82f + 0.18f * near);
                text(c, line, lyX, y, size, (a << 24) | 0xFFFFFF, i == cur ? bold : reg, lyW, true);
            }
            c.restore();
        } else {
            String msg = !info ? "Appen delar inte låtnamnet ännu – lyrics kommer när den gör det" : Music.lyricsLoading ? "Söker lyrics …" : "Inga synkade lyrics hittades";
            text(c, msg, lyX, lyTop, lyS * 0.8f, 0x66FFFFFF, reg, lyW, true);
        }
        if (big && now < Music.controlsUntil) { // on-screen buttons, shown for a few seconds after a key press
            float fade = Math.min(1f, (Music.controlsUntil - now) / 400f);
            int A = Math.round(255 * fade);
            float cy = H - 130 * u, cx = W / 2f;
            float[] off = {-380, -190, 0, 190};
            for (int i = 0; i < 4; i++) {
                float x = cx + off[i] * u, rad = (i == 2 ? 62 : i == 0 ? 40 : 48) * u;
                boolean f = Music.sel == i;
                p.setStyle(Paint.Style.FILL);
                p.setColor((Math.round(A * (f ? 1f : 0.13f)) << 24) | 0xFFFFFF);
                c.drawCircle(x, cy, rad, p);
                if (!f) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * u); p.setColor((Math.round(A * 0.33f) << 24) | 0xFFFFFF); c.drawCircle(x, cy, rad, p); }
                int ic = f ? (A << 24) : (Math.round(A * 0.8f) << 24) | 0xFFFFFF;
                p.setColor(ic);
                float s = rad * 0.38f;
                if (i == 0) { // back arrow
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5 * u); p.setStrokeCap(Paint.Cap.ROUND);
                    c.drawLine(x + s, cy, x - s, cy, p);
                    c.drawLine(x - s, cy, x - s * 0.1f, cy - s * 0.9f, p);
                    c.drawLine(x - s, cy, x - s * 0.1f, cy + s * 0.9f, p);
                    p.setStrokeCap(Paint.Cap.BUTT);
                } else if (i == 2) {
                    p.setStyle(Paint.Style.FILL);
                    if (Music.playing) {
                        c.drawRect(x - s * 0.8f, cy - s, x - s * 0.2f, cy + s, p);
                        c.drawRect(x + s * 0.2f, cy - s, x + s * 0.8f, cy + s, p);
                    } else {
                        Path tri = new Path();
                        tri.moveTo(x - s * 0.6f, cy - s); tri.lineTo(x + s, cy); tri.lineTo(x - s * 0.6f, cy + s); tri.close();
                        c.drawPath(tri, p);
                    }
                } else {
                    p.setStyle(Paint.Style.FILL);
                    int d = i == 1 ? -1 : 1; // -1 previous (points left), +1 next (points right)
                    float tip = x + d * s, base = x - d * s * 0.7f;
                    Path tri = new Path();
                    tri.moveTo(tip, cy); tri.lineTo(base, cy - s); tri.lineTo(base, cy + s); tri.close();
                    c.drawPath(tri, p);
                    c.drawRect(tip - 3 * u, cy - s, tip + 3 * u, cy + s, p);
                }
                p.setStyle(Paint.Style.FILL);
            }
        }
        if (big) {
            // "Next up" card for the last 30 seconds of the song
            long left = Music.durationMs - pos;
            if (info && Music.nextTitle != null && !Music.nextTitle.isEmpty() && Music.durationMs > 0 && left > 0 && left <= 30000) {
                float al = Math.min(1f, Math.min((30000 - left) / 800f + 0.01f, left / 800f));
                int a255 = Math.round(255 * Math.max(0f, al));
                float cw = 640 * u, chh = 150 * u, x0 = W - cw - 90 * u, y0 = 60 * u;
                p.setStyle(Paint.Style.FILL); p.setColor((Math.round(a255 * 0.14f) << 24) | 0xFFFFFF);
                c.drawRoundRect(x0, y0, x0 + cw, y0 + chh, 24 * u, 24 * u, p);
                float tx2 = x0 + 28 * u;
                if (Music.nextArt != null) {
                    r.set(x0 + 20 * u, y0 + 20 * u, x0 + 130 * u, y0 + 130 * u);
                    p.setAlpha(a255);
                    c.drawBitmap(Music.nextArt, null, r, p);
                    p.setAlpha(255);
                    tx2 = x0 + 150 * u;
                }
                text(c, "NÄSTA", tx2, y0 + 42 * u, 22 * u, (a255 << 24) | 0x999999, bold, x0 + cw - tx2 - 20 * u, false);
                text(c, Music.nextTitle, tx2, y0 + 88 * u, 34 * u, (a255 << 24) | 0xFFFFFF, bold, x0 + cw - tx2 - 20 * u, false);
                text(c, Music.nextArtist, tx2, y0 + 125 * u, 26 * u, (a255 << 24) | 0xBBBBBB, reg, x0 + cw - tx2 - 20 * u, false);
            }
        }
        if (on && isShown()) postInvalidateDelayed(Music.shown ? 33 : 250);
    }
}
