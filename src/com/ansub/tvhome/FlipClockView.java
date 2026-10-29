package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.Calendar;

// Full-screen flip clock on pure black: the screensaver. Each digit is a card whose top half folds
// down and whose new bottom half unfolds, like the real thing. 24 h, small date underneath.
final class FlipClockView extends View {
    private static final long FLIP_MS = 640;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG),
            shade = new Paint(), small = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG),
            seam = new Paint();
    private final RectF r = new RectF();
    private final int[] cur = {-1, -1, -1, -1}, prev = {-1, -1, -1, -1};
    private final long[] anim = new long[4];
    private boolean active;
    private String date = "";

    FlipClockView(Context c, Typeface face) {
        super(c);
        setBackgroundColor(0xFF000000);
        card.setColor(0xFF111113);
        txt.setColor(0xFFEDEDF0);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(face != null ? face : Typeface.create("sans-serif", Typeface.BOLD));
        shade.setColor(0xFF000000);
        small.setColor(0xFF6B6B73);
        small.setTextAlign(Paint.Align.CENTER);
        dot.setColor(0xFF3A3A40);
        seam.setColor(0xFF000000);
    }

    void setActive(boolean on) {
        active = on;
        if (on) { tickTime(false); invalidate(); }
    }

    private void tickTime(boolean animate) {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        int[] d = {h / 10, h % 10, m / 10, m % 10};
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < 4; i++) {
            if (d[i] != cur[i]) {
                prev[i] = cur[i] < 0 ? d[i] : cur[i];
                cur[i] = d[i];
                anim[i] = animate && prev[i] != cur[i] ? now : 0;
            }
        }
        date = android.text.format.DateFormat.format(Strings.datePattern(), c).toString();
    }

    @Override protected void onDraw(Canvas c) {
        if (!active) return;
        tickTime(true);
        int w = getWidth(), h = getHeight();
        float u = w / 1920f;
        float cw = 300 * u, ch = 420 * u, gap = 24 * u, mid = 96 * u;
        float total = 4 * cw + 2 * gap + mid;
        // slide a few pixels each minute so nothing stays lit in the same place
        int minute = Calendar.getInstance().get(Calendar.MINUTE);
        float sx = ((minute % 7) - 3) * 4 * u, sy = ((minute % 5) - 2) * 4 * u;
        float x0 = (w - total) / 2f + sx, y0 = (h - ch) / 2f - 30 * u + sy;
        txt.setTextSize(ch * 0.82f);
        small.setTextSize(34 * u);
        long now = SystemClock.uptimeMillis();
        boolean animating = false;
        float x = x0;
        for (int i = 0; i < 4; i++) {
            float p = anim[i] == 0 ? 1f : Math.min(1f, (now - anim[i]) / (float) FLIP_MS);
            if (p < 1f) animating = true; else anim[i] = 0;
            drawCard(c, i, x, y0, cw, ch, p * p * (3 - 2 * p));
            x += cw + (i == 1 ? mid : gap);
            if (i == 1) {
                float cx = x - mid / 2 - gap / 2 + gap / 2, cy = y0 + ch / 2;
                c.drawCircle(x0 + 2 * cw + gap + mid / 2, cy - 38 * u, 9 * u, dot);
                c.drawCircle(x0 + 2 * cw + gap + mid / 2, cy + 38 * u, 9 * u, dot);
            }
        }
        c.drawText(date, w / 2f + sx, y0 + ch + 72 * u, small);
        postInvalidateDelayed(animating ? 16 : 500);
    }

    private void drawCard(Canvas c, int i, float x, float y, float w, float h, float p) {
        float cy = y + h / 2f;
        half(c, x, y, w, h, cur[i], true, 1f, 0f);
        half(c, x, y, w, h, p < 1f ? prev[i] : cur[i], false, 1f, 0f);
        if (p < 1f) {
            if (p < 0.5f) half(c, x, y, w, h, prev[i], true, 1 - 2 * p, p * 0.9f);
            else half(c, x, y, w, h, cur[i], false, 2 * p - 1, (1 - p) * 0.9f);
        }
        c.drawRect(x, cy - 2.5f, x + w, cy + 2.5f, seam);
    }

    // one half of a card: top or bottom, optionally squashed towards the centre line (the flap) and darkened
    private void half(Canvas c, float x, float y, float w, float h, int digit, boolean top, float sc, float dark) {
        float cy = y + h / 2f;
        c.save();
        c.scale(1f, Math.max(0.001f, sc), x, cy);
        if (top) c.clipRect(x, y, x + w, cy); else c.clipRect(x, cy, x + w, y + h);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, 26f * w / 300f, 26f * w / 300f, card);
        Paint.FontMetrics fm = txt.getFontMetrics();
        c.drawText(String.valueOf(digit), x + w / 2f, cy - (fm.ascent + fm.descent) / 2f, txt);
        if (dark > 0f) { shade.setAlpha(Math.round(255 * dark)); c.drawRect(x, y, x + w, y + h, shade); }
        c.restore();
    }
}
