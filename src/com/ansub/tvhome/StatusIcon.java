package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

// Small icons drawn with shapes: thermometer, windshield, a green check and an orange cross.
final class StatusIcon extends View {
    static final int THERMO = 0, WINDSHIELD = 1, CHECK = 2, CROSS = 3, PIN = 4;
    private int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    StatusIcon(Context c, int kind) {
        super(c);
        this.kind = kind;
    }

    void setKind(int k) {
        if (k == kind) return;
        kind = k;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        c.save();
        c.translate((w - s) / 2f, (h - s) / 2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case THERMO: {
                p.setColor(0xFFFFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.09f * s);
                c.drawRoundRect(new RectF(0.40f * s, 0.10f * s, 0.60f * s, 0.62f * s), 0.10f * s, 0.10f * s, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFF6B5E);
                c.drawCircle(0.50f * s, 0.74f * s, 0.20f * s, p);
                c.drawRect(0.46f * s, 0.36f * s, 0.54f * s, 0.74f * s, p);
                break;
            }
            case WINDSHIELD: {
                // front window of a car: glass in a trapezoid, a wiper across it
                p.setColor(0xFFFFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.08f * s);
                Path glass = new Path();
                glass.moveTo(0.28f * s, 0.22f * s);
                glass.lineTo(0.72f * s, 0.22f * s);
                glass.lineTo(0.90f * s, 0.74f * s);
                glass.lineTo(0.10f * s, 0.74f * s);
                glass.close();
                c.drawPath(glass, p);
                p.setStrokeWidth(0.07f * s);
                c.drawLine(0.22f * s, 0.62f * s, 0.62f * s, 0.34f * s, p);
                break;
            }
            case CHECK: {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF3DDC84);
                c.drawCircle(0.5f * s, 0.5f * s, 0.46f * s, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFFFFFFF);
                p.setStrokeWidth(0.11f * s);
                Path ck = new Path();
                ck.moveTo(0.28f * s, 0.52f * s);
                ck.lineTo(0.44f * s, 0.67f * s);
                ck.lineTo(0.73f * s, 0.34f * s);
                c.drawPath(ck, p);
                break;
            }
            case CROSS: {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFF7043);
                c.drawCircle(0.5f * s, 0.5f * s, 0.46f * s, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFFFFFFFF);
                p.setStrokeWidth(0.11f * s);
                c.drawLine(0.33f * s, 0.33f * s, 0.67f * s, 0.67f * s, p);
                c.drawLine(0.67f * s, 0.33f * s, 0.33f * s, 0.67f * s, p);
                break;
            }
            default: { // PIN
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                Path pin = new Path();
                pin.addCircle(0.5f * s, 0.38f * s, 0.26f * s, Path.Direction.CW);
                Path tip = new Path();
                tip.moveTo(0.28f * s, 0.52f * s);
                tip.lineTo(0.72f * s, 0.52f * s);
                tip.lineTo(0.5f * s, 0.92f * s);
                tip.close();
                c.drawPath(pin, p);
                c.drawPath(tip, p);
                p.setColor(0xFF1E1E1E);
                c.drawCircle(0.5f * s, 0.38f * s, 0.10f * s, p);
            }
        }
        c.restore();
    }
}
