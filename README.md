# TV Home

A small custom home screen (launcher) for Android TV, written as plain Java with no Gradle or AndroidX.

- Clock, greeting and weather (Open-Meteo, no API key)
- Optional home info row (room temperature, windshield cover) from a webhook that returns `{"room": "23.0", "windshield": false}`
- Glossy black "now playing" widget with cover, title, artist, progress and synced lyrics from lrclib.net
- Full-screen music screensaver with a blurred cover background and on-screen remote controls (back / previous / play-pause / next); a flip-clock screensaver when nothing plays
- Reads the playing song through Android's notification access (`MediaListener`); it never reads notifications

## Set it up

1. Edit `InfoPanel.java`: `NAME`, the latitude/longitude in the weather URL, the place name and `HOME_URL` (leave the host as is to turn the home row off).
2. Optionally put a photo at `assets/bg.jpg`; without it only the black theme is used.
3. Put your app packages in `apps.txt`. The music widget is tuned for a player with the package `com.musik.app`, change it in `MediaListener.java` and `MainActivity.java` for another player.
4. Build with the Android SDK tools (aapt2, javac, d8, zipalign, apksigner) and sign with your own key.
5. After installing, allow notification access: `adb shell cmd notification allow_listener com.ansub.tvhome/com.ansub.tvhome.MediaListener`
6. Set it as the home app on the TV.

Back up your TV before changing system apps. Only disable apps, never delete them.
