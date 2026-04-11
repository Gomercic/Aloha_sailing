# Aloha Sailing (StartLine)

Android aplikacija za startnu liniju, WindShift analizu, sidrenje i GPX tragove.

## Build

Zahtijeva Android Studio ili JDK 17+ i Android SDK.

```bash
./gradlew assembleDebug
```

Release potpisivanje: vidi [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md).

## Struktura

- `app/` — Android modul (`com.aloha.startline`)
- `docker/` — NAS / kontejner pomoć
- `docs/` — upute za release i Play Store
