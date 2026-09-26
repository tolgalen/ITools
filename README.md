# ITools (Android)

Aplicación Android nativa (Kotlin + Jetpack Compose + Material 3) que replica las **86 utilidades** de [it-tools.tech](https://it-tools.tech/), organizadas en las mismas categorías: Criptografía, Conversores, Web, Imágenes y vídeos, Desarrollo, Red, Matemáticas, Medidas, Texto y Datos.

- **Android 10+** (minSdk 29, targetSdk 35)
- **Modo oscuro**: claro / oscuro / seguir al sistema, y color dinámico (Material You) opcional en Android 12+
- **Multiidioma**: español e inglés, seleccionable dentro de la app (idioma por app; en Android 13+ también desde Ajustes del sistema)
- Búsqueda (sin acentos, en ambos idiomas), filtros por categoría y favoritos
- Todo funciona **sin conexión** salvo *MAC address lookup*, que consulta `api.macvendors.com` (solo se envía el prefijo OUI)

## Compilar

1. Abre la carpeta `ITools` con **Android Studio Ladybug (2024.2) o superior**.
2. El proyecto no incluye `gradle-wrapper.jar` (binario). Android Studio lo regenera al sincronizar; o bien, con Gradle instalado: `gradle wrapper --gradle-version 8.11.1`.
3. Sincroniza y ejecuta el módulo `app` (JDK 17).

Versiones: AGP 8.7.3 · Kotlin 2.1.0 · Compose BOM 2024.12.01 · Gradle 8.11.1.

## Estructura

```
app/src/main/java/com/itools/app/
├── MainActivity.kt            # AppCompatActivity (idioma por app) + Compose
├── data/SettingsStore.kt      # DataStore: tema, color dinámico, favoritos
├── ui/                        # Navegación, tema, pantallas y componentes comunes
├── util/                      # Codificación, hashes, JSON/YAML/TOML/XML/CSV, colores, QR, user-agent
└── tools/
    ├── ToolCatalog.kt         # Catálogo (id, categoría, textos)
    ├── ToolRegistry.kt        # id → pantalla
    ├── CryptoTools.kt  ConverterTools.kt  WebTools.kt  HttpStatus.kt
    ├── ImageTools.kt   DevTools.kt  NetworkTools.kt
    └── MathTools.kt    TextTools.kt  DataTools.kt
```

Los nombres y descripciones del catálogo están en `res/values/strings.xml` y `res/values-es/strings.xml`; las etiquetas internas de cada herramienta usan el helper `t("inglés", "español")`.

## Diferencias respecto a la web

- **Encrypt/decrypt**: AES, TripleDES y RC4 compatibles con CryptoJS (formato `Salted__`). Rabbit no está incluido.
- **Text to ASCII art**: fuentes propias de 5 líneas (block, shadow, banner, etc.) en lugar de las fuentes FIGlet.
- **Emoji picker**: lista curada (~490 emojis) con búsqueda por nombre en inglés.
- **SQL prettify**: formateador propio (palabras clave, sangría, subconsultas); no distingue dialectos.
- **SVG placeholder**: la vista previa se dibuja con Canvas (Android no renderiza SVG de forma nativa); el SVG generado es el mismo.
- **Camera recorder**: CameraX; fotos en `Pictures/ITools` y vídeos en `Movies/ITools`.
- **Keycode info**: requiere teclado físico o eventos de tecla (el teclado en pantalla no genera todos los eventos).

## Notas

- La dependencia `cash.z.ecc.android:kotlin-bip39:1.0.8` debe comprobarse al sincronizar; si cambia la versión publicada, ajústala en `app/build.gradle.kts`.
- El código es una reimplementación original en Kotlin. El proyecto original it-tools (Corentin Thomasset) se distribuye bajo **GPL-3.0**; si publicas esta app, revisa las implicaciones de licencia (textos, datos y nombres derivados).
