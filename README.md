# ITools (Android)

Native Android app (Kotlin + Jetpack Compose + Material 3) that brings the **86 utilities** of [it-tools.tech](https://it-tools.tech/) to your phone, grouped in the same categories: Crypto, Converter, Web, Images and videos, Development, Network, Math, Measurement, Text and Data.

- **Android 10+** (minSdk 29, targetSdk 35)
- **Themes**: system default, light, dark, dark blue, black (OLED) and it-tools green; optional dynamic color (Material You) on Android 12+
- **Multilingual**: English and Spanish, selectable inside the app (per-app language; on Android 13+ also from the system settings)
- **Tile menu**: Favorites (when there are any), All and one tile per category; each tile opens the list of its tools with their descriptions
- Search (accent-insensitive, in both languages) and favorites
- **Backup**: export/restore theme, language and favorites to a JSON file (not encrypted) from Settings
- Everything works **offline** except *MAC address lookup*, which queries `api.macvendors.com` (only the OUI prefix is sent)

## Release build

Copy `keystore.properties.example` to `keystore.properties` (kept out of version control) and fill in your keystore details. `gradle assembleRelease bundleRelease` then produces the signed APK and AAB; without that file they are built unsigned.

## Building

1. Open the `ITools` folder with a recent **Android Studio** (it must support AGP 9.4).
2. The project does not include the Gradle wrapper scripts or `gradle-wrapper.jar` (binary). Android Studio regenerates them when syncing; alternatively, with Gradle installed: `gradle wrapper --gradle-version 9.6.0`.
3. Sync and run the `app` module (JDK 21 for Gradle; the code targets Java 17).

Versions: AGP 9.4.1 · Kotlin 2.2.10 · Compose BOM 2024.12.01 · Gradle 9.6.0.

## Structure

```
app/src/main/java/com/itools/app/
├── MainActivity.kt            # AppCompatActivity (per-app language) + Compose
├── data/SettingsStore.kt      # DataStore: theme, dynamic color, favorites
├── data/SettingsBackup.kt     # JSON settings backup
├── ui/                        # Navigation, theme, screens and shared components
├── util/                      # Encoding, hashes, JSON/YAML/TOML/XML/CSV, colors, QR, user-agent
└── tools/
    ├── ToolCatalog.kt         # Catalog (id, category, texts)
    ├── ToolRegistry.kt        # id → screen
    ├── CryptoTools.kt  ConverterTools.kt  WebTools.kt  HttpStatus.kt
    ├── ImageTools.kt   DevTools.kt  NetworkTools.kt
    └── MathTools.kt    TextTools.kt  DataTools.kt
```

Catalog names and descriptions live in `res/values/strings.xml` and `res/values-es/strings.xml`; the labels inside each tool use the `t("english", "spanish")` helper.

## Differences from the website

- **Encrypt/decrypt**: AES, TripleDES and RC4, compatible with CryptoJS (`Salted__` format). Rabbit is not included.
- **Text to ASCII art**: custom 5-line fonts (block, shadow, banner, etc.) instead of FIGlet fonts.
- **Emoji picker**: curated list (~490 emojis) with search by English name.
- **SQL prettify**: custom formatter (keywords, indentation, subqueries); it does not distinguish SQL dialects.
- **SVG placeholder**: the preview is drawn with Canvas (Android does not render SVG natively); the generated SVG is the same.
- **Camera recorder**: CameraX; photos go to `Pictures/ITools` and videos to `Movies/ITools`.
- **Keycode info**: requires a physical keyboard or key events (the on-screen keyboard does not generate every event).

## Notes

- Check the `cash.z.ecc.android:kotlin-bip39:1.0.8` dependency when syncing; if the published version changes, update it in `app/build.gradle.kts`.
- The code is an original Kotlin reimplementation of the ideas behind it-tools (Corentin Thomasset), which is also distributed under GPL-3.0.

## License

ITools is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License version 3** as published by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY. See [LICENSE](LICENSE) for the full text.
