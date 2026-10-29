package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

// A small weather icon drawn with shapes, so it needs no image files.
final class WeatherIcon extends View {
    static final int CLEAR = 0, PARTLY = 1, CLOUD = 2, FOG = 3, RAIN = 4, SNOW = 5, THUNDER = 6;
    private int kind = CLOUD;
    private boolean day = true;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    WeatherIcon(Context c) { super(c); }

    void set(int code, boolean isDay) {
        kind = kindOf(code);
        day = isDay;
        invalidate();
    }

    static int kindOf(int code) {
        if (code == 0) return CLEAR;
        if (code == 1 || code == 2) return PARTLY;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return THUNDER;
        return CLOUD;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        c.save();
        c.translate((w - s) / 2f, (h - s) / 2f);
        switch (kind) {
            case CLEAR:
                celestial(c, 0.5f * s, 0.5f * s, 0.21f * s, s);
                break;
            case PARTLY:
                celestial(c, 0.36f * s, 0.36f * s, 0.15f * s, s);
                cloud(c, s, 0.06f, 0xFFE8EEF5);
                break;
            case FOG:
                cloud(c, s, -0.06f, 0xFFD5DCE6);
                lines(c, s);
                break;
            case RAIN:
                cloud(c, s, -0.10f, 0xFFC9D2DE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(0.05f * s);
                p.setColor(0xFF6EC1FF);
                for (int i = 0; i < 3; i++) {
                    float x = (0.36f + 0.15f * i) * s;
                    c.drawLine(x, 0.68f * s, x - 0.05f * s, 0.84f * s, p);
                }
                break;
            case SNOW:
                cloud(c, s, -0.10f, 0xFFD5DCE6);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) {
                    c.drawCircle((0.36f + 0.15f * i) * s, (0.76f + (i % 2) * 0.07f) * s, 0.032f * s, p);
                }
                break;
            case THUNDER:
                cloud(c, s, -0.10f, 0xFFA9B4C2);
                Path bolt = new Path();
                bolt.moveTo(0.54f * s, 0.58f * s);
                bolt.lineTo(0.40f * s, 0.82f * s);
                bolt.lineTo(0.50f * s, 0.82f * s);
                bolt.lineTo(0.45f * s, 0.98f * s);
                bolt.lineTo(0.64f * s, 0.72f * s);
                bolt.lineTo(0.53f * s, 0.72f * s);
                bolt.lineTo(0.60f * s, 0.58f * s);
                bolt.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFD84D);
                c.drawPath(bolt, p);
                break;
            default:
                cloud(c, s, 0f, 0xFFE8EEF5);
        }
        c.restore();
    }

    // Sun with rays by day, a crescent moon by night.
    private void celestial(Canvas c, float cx, float cy, float r, float s) {
        if (day) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFC94D);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(0.045f * s);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float c0 = (float) Math.cos(a), s0 = (float) Math.sin(a);
                c.drawLine(cx + c0 * r * 1.38f, cy + s0 * r * 1.38f, cx + c0 * r * 1.78f, cy + s0 * r * 1.78f, p);
            }
        } else {
            Path moon = new Path();
            moon.addCircle(cx, cy, r * 1.1f, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(cx + r * 0.55f, cy - r * 0.3f, r * 0.95f, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFF4E9B8);
            c.drawPath(moon, p);
        }
    }

    private void cloud(Canvas c, float s, float dy, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        float d = dy * s;
        c.drawCircle(0.34f * s, 0.52f * s + d, 0.15f * s, p);
        c.drawCircle(0.55f * s, 0.44f * s + d, 0.20f * s, p);
        c.drawCircle(0.74f * s, 0.56f * s + d, 0.13f * s, p);
        c.drawRoundRect(new RectF(0.20f * s, 0.54f * s + d, 0.82f * s, 0.69f * s + d), 0.075f * s, 0.075f * s, p);
    }

    private void lines(Canvas c, float s) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(0.045f * s);
        p.setColor(0xCCFFFFFF);
        c.drawLine(0.24f * s, 0.74f * s, 0.80f * s, 0.74f * s, p);
        c.drawLine(0.32f * s, 0.83f * s, 0.72f * s, 0.83f * s, p);
        c.drawLine(0.24f * s, 0.92f * s, 0.80f * s, 0.92f * s, p);
    }
}
