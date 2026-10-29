package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

// One row under the clock: the greeting with your name on the left, the weather in Stockholm on the
// right (Open-Meteo, no key needed).
final class InfoPanel extends FrameLayout {
    private static final String NAME = "Your Name";
    private static final String WEATHER_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=59.3293&longitude=18.0686"
        + "&current=temperature_2m,weather_code,wind_speed_10m,is_day"
        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1"
        + "&wind_speed_unit=ms&timezone=Europe%2FStockholm";
    private static final long REFRESH_MS = 30L * 60L * 1000L;
    // Answered by your n8n workflow on the home network: {"room":"21.5","windshield":"Behövs inte"}
    private static final String HOME_URL = "http://YOUR-N8N-HOST:5678/webhook/tv-info";
    private static final long HOME_REFRESH_MS = 30L * 1000L;

    private final TextView greeting, temp, detail, roomText, wsText, sepText;
    private final LinearLayout homeRow, wsGroup;
    private final StatusIcon thermoIcon, stateIcon;
    private final WeatherIcon icon;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private long lastFetch;
    private boolean fetching;
    private long lastHomeFetch;
    private boolean fetchingHome;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            greeting.setText(greetingNow());
            if (!fetching && System.currentTimeMillis() - lastFetch > REFRESH_MS) fetch();
            if (!fetchingHome && System.currentTimeMillis() - lastHomeFetch > HOME_REFRESH_MS) fetchHome();
            handler.postDelayed(this, 15000);
        }
    };

    InfoPanel(Context c, Typeface regular, Typeface semibold, float scale) {
        super(c);
        int m = Math.round(96 * scale);

        LinearLayout stack = new LinearLayout(c);
        stack.setOrientation(LinearLayout.VERTICAL);

        greeting = new TextView(c);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(46 * scale));
        greeting.setTextColor(Color.WHITE);
        greeting.setShadowLayer(10f, 0f, 2f, 0xB0000000);
        if (semibold != null) greeting.setTypeface(semibold);
        greeting.setText(greetingNow());
        stack.addView(greeting, new LinearLayout.LayoutParams(-2, -2));

        // weather row: icon, then the temperature with the details under it
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        icon = new WeatherIcon(c);
        int is = Math.round(92 * scale);
        box.addView(icon, new LinearLayout.LayoutParams(is, is));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        temp = new TextView(c);
        temp.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(56 * scale));
        temp.setTextColor(Color.WHITE);
        temp.setShadowLayer(10f, 0f, 2f, 0xB0000000);
        if (semibold != null) temp.setTypeface(semibold);
        temp.setText("–°");
        col.addView(temp, new LinearLayout.LayoutParams(-2, -2));
        detail = new TextView(c);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(24 * scale));
        detail.setTextColor(0xDDFFFFFF);
        detail.setShadowLayer(8f, 0f, 2f, 0xB0000000);
        if (regular != null) detail.setTypeface(regular);
        detail.setText("Stockholm");
        col.addView(detail, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = Math.round(16 * scale);
        box.addView(col, cl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = Math.round(14 * scale);
        stack.addView(box, bl);

        // home row: room temperature, windshield
        int is2 = Math.round(36 * scale);
        homeRow = new LinearLayout(c);
        homeRow.setOrientation(LinearLayout.HORIZONTAL);
        homeRow.setGravity(Gravity.CENTER_VERTICAL);
        homeRow.setVisibility(GONE); // shown once the home server has answered
        thermoIcon = new StatusIcon(c, StatusIcon.THERMO);
        homeRow.addView(thermoIcon, new LinearLayout.LayoutParams(is2, is2));
        roomText = homeText(c, regular, scale, 28);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.leftMargin = Math.round(8 * scale);
        homeRow.addView(roomText, rl);
        sepText = homeText(c, regular, scale, 28);
        sepText.setText("   ·   ");
        homeRow.addView(sepText, new LinearLayout.LayoutParams(-2, -2));
        wsGroup = new LinearLayout(c);
        wsGroup.setOrientation(LinearLayout.HORIZONTAL);
        wsGroup.setGravity(Gravity.CENTER_VERTICAL);
        wsGroup.addView(new StatusIcon(c, StatusIcon.WINDSHIELD), new LinearLayout.LayoutParams(is2, is2));
        stateIcon = new StatusIcon(c, StatusIcon.CHECK);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(is2, is2);
        sl.leftMargin = Math.round(8 * scale);
        wsGroup.addView(stateIcon, sl);
        wsText = homeText(c, regular, scale, 28);
        LinearLayout.LayoutParams wl2 = new LinearLayout.LayoutParams(-2, -2);
        wl2.leftMargin = Math.round(8 * scale);
        wsGroup.addView(wsText, wl2);
        homeRow.addView(wsGroup, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = Math.round(14 * scale);
        stack.addView(homeRow, hl);

        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP);
        gl.leftMargin = m;
        addView(stack, gl);
    }

    private static TextView homeText(Context c, Typeface tf, float scale, int px) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.round(px * scale));
        t.setTextColor(0xEEFFFFFF);
        t.setShadowLayer(8f, 0f, 2f, 0xB0000000);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    void start() {
        if (running) return;
        running = true;
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private static String greetingNow() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g;
        if (h >= 5 && h < 12) g = "God morgon";
        else if (h >= 12 && h < 18) g = "God eftermiddag";
        else g = "God kväll";
        return g + ", " + NAME;
    }

    private void fetch() {
        fetching = true;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                int code = 3, isDay = 1;
                long t = 0, wind = 0, hi = 0, lo = 0;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(WEATHER_URL).openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject cur = root.getJSONObject("current");
                    JSONObject daily = root.getJSONObject("daily");
                    JSONArray mx = daily.getJSONArray("temperature_2m_max");
                    JSONArray mn = daily.getJSONArray("temperature_2m_min");
                    t = Math.round(cur.getDouble("temperature_2m"));
                    code = cur.getInt("weather_code");
                    isDay = cur.getInt("is_day");
                    wind = Math.round(cur.getDouble("wind_speed_10m"));
                    hi = Math.round(mx.getDouble(0));
                    lo = Math.round(mn.getDouble(0));
                    ok = true;
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final boolean fok = ok;
                final int fcode = code;
                final boolean fday = isDay == 1;
                final String tempText = t + "°";
                final String detailText = describe(code) + "  ·  H " + hi + "°  L " + lo + "°  ·  " + wind + " m/s";
                handler.post(new Runnable() {
                    @Override public void run() {
                        fetching = false;
                        // On failure keep the last values and try again in a minute, not after 30.
                        lastFetch = fok ? System.currentTimeMillis() : System.currentTimeMillis() - REFRESH_MS + 60000L;
                        if (fok) {
                            temp.setText(tempText);
                            detail.setText("Stockholm  ·  " + detailText);
                            icon.set(fcode, fday);
                        }
                    }
                });
            }
        }).start();
    }

    private void fetchHome() {
        fetchingHome = true;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                String room = "", ws = "", vac = null;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(HOME_URL).openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(6000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                    r.close();
                    JSONObject o = new JSONObject(sb.toString());
                    room = o.optString("room", "").replace('.', ',');
                    ws = o.optString("windshield", "").toLowerCase();
                    if (o.has("vacuum")) vac = o.optString("vacuum", "").toLowerCase();
                    ok = room.length() > 0 || ws.length() > 0 || vac != null;
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final boolean fok = ok;
                final String froom = room, fws = ws, fvac = vac;
                handler.post(new Runnable() {
                    @Override public void run() {
                        fetchingHome = false;
                        // Retry in a minute after a failure; keep showing the last answer.
                        lastHomeFetch = fok ? System.currentTimeMillis() : System.currentTimeMillis() - HOME_REFRESH_MS + 60000L;
                        if (!fok) return;
                        boolean hasRoom = froom.length() > 0, hasWs = fws.length() > 0;
                        thermoIcon.setVisibility(hasRoom ? VISIBLE : GONE);
                        roomText.setVisibility(hasRoom ? VISIBLE : GONE);
                        roomText.setText("Inne " + froom + "°");
                        sepText.setVisibility(hasRoom && hasWs ? VISIBLE : GONE);
                        wsGroup.setVisibility(hasWs ? VISIBLE : GONE);
                        if (hasWs) {
                            // "behövs inte" = not needed (green check). "behövs" = needed (orange cross).
                            boolean needed = fws.contains("behövs") && !fws.contains("behövs inte");
                            wsText.setText(needed ? "Vindruteskydd behövs" : "Inget vindruteskydd");
                            stateIcon.setKind(needed ? StatusIcon.CROSS : StatusIcon.CHECK);
                        }
                        homeRow.setVisibility(hasRoom || hasWs ? VISIBLE : GONE);
                    }
                });
            }
        }).start();
    }

    private static String describe(int code) {
        if (code == 0) return "Klart";
        if (code == 1) return "Mestadels klart";
        if (code == 2) return "Halvklart";
        if (code == 3) return "Mulet";
        if (code == 45 || code == 48) return "Dimma";
        if (code >= 51 && code <= 57) return "Duggregn";
        if (code == 66 || code == 67) return "Underkylt regn";
        if (code >= 61 && code <= 65) return "Regn";
        if (code >= 71 && code <= 77) return "Snö";
        if (code >= 80 && code <= 82) return "Regnskurar";
        if (code == 85 || code == 86) return "Snöbyar";
        if (code >= 95) return "Åska";
        return "";
    }
}
