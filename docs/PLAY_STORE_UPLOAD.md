# Google Play Console (ručno)

Nakon `./gradlew :app:bundleRelease` uploadaj **`app/build/outputs/bundle/release/app-release.aab`** (ne APK za normalan production release).

## Kratki checklist

1. [play.google.com/console](https://play.google.com/console) → app → **Production** ili **Internal testing** → **Create new release**.
2. Upload **`app-release.aab`**, pregledaj upozorenja (dozvole, target API).
3. **Store listing**: naslov, kratki/puni opis, slike (ikona, feature graphic, screenshoti).
4. **Policy**: ako app koristi lokaciju ili druge osjetljive podatke, dodaj **Privacy policy** URL.
5. **Content rating** i deklaracije (ads, COVID, itd.) prema čarobnjaku u konzoli.
6. Svako novo objavljivanje: povećaj **`versionCode`** u `app/build.gradle.kts`, ponovno `bundleRelease`, zatim novi release u konzoli.

Detalji potpisivanja: [RELEASE_SIGNING.md](RELEASE_SIGNING.md).
