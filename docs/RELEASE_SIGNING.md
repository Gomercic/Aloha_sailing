# Potpisani release (APK / AAB)

## 1. Jednokratno: izradi keystore

U korijenu projekta (`StartLine/`):

```bash
keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias startline -storetype PKCS12
```

Zapamti lozinke i alias (`startline`). **Čuvaj `release-keystore.jks` i lozinke** — bez njih nećeš moći ažurirati istu aplikaciju na Playu ili istim APK-om.

## 2. Konfiguracija

```bash
cp keystore.properties.example keystore.properties
```

Uredi `keystore.properties`: stvarne lozinke i putanju do `.jks` (relativno od korijena projekta, npr. `release-keystore.jks`).

`keystore.properties` i `*.jks` su u `.gitignore` — ne commitaj ih.

## 3. Build

- **APK:** `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- **AAB (Play Store):** `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`

Bez `keystore.properties` release taskovi neće proći (Gradle javlja grešku).

## 4. Instalacija na uređaj

Korisnik može instalirati APK bez developer moda ako dopusti instalaciju iz izvora (npr. Files / Drive). Za Play trebaš AAB i Play Console.
