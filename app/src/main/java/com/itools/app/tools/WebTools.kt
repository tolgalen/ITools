package com.itools.app.tools

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.KeyEvent as NativeKeyEvent
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itools.app.ui.components.*
import com.itools.app.util.*
import kotlinx.coroutines.delay
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/* ======================= URL encode / decode ======================= */

fun encodeUriComponent(s: String): String = URLEncoder.encode(s, "UTF-8")
    .replace("+", "%20").replace("%21", "!").replace("%27", "'")
    .replace("%28", "(").replace("%29", ")").replace("%7E", "~")

@Composable
fun UrlEncoderTool() {
    var a by remember { mutableStateOf("Hello world :)") }
    var b by remember { mutableStateOf("Hello%20world%20%3A)") }
    val dec = remember(b) { runCatching { URLDecoder.decode(b.replace("+", "%2B"), "UTF-8") } }
    ToolColumn {
        Panel(t("Encode", "Codificar")) {
            InputField(a, { a = it }, t("Your string", "Tu texto"), singleLine = false, minLines = 2)
            OutputField(encodeUriComponent(a), t("Your string encoded", "Texto codificado"), minLines = 2)
        }
        Panel(t("Decode", "Decodificar")) {
            InputField(b, { b = it }, t("Your encoded string", "Texto codificado"), singleLine = false, minLines = 2, mono = true,
                error = if (dec.isFailure) t("Impossible to decode this string", "No se puede decodificar") else null)
            OutputField(dec.getOrDefault(""), t("Your string decoded", "Texto decodificado"), minLines = 2, mono = false)
        }
    }
}

/* ======================= HTML entities ======================= */

object HtmlEntities {
    private val named = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "#39" to "'", "nbsp" to "\u00a0",
        "copy" to "©", "reg" to "®", "trade" to "™", "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢", "sect" to "§",
        "deg" to "°", "plusmn" to "±", "times" to "×", "divide" to "÷", "hellip" to "…", "mdash" to "—", "ndash" to "–",
        "laquo" to "«", "raquo" to "»", "iexcl" to "¡", "iquest" to "¿", "ntilde" to "ñ", "Ntilde" to "Ñ",
        "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú", "uuml" to "ü")
    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    fun unescape(s: String) = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[e]
        } ?: m.value
    }
}

@Composable
fun HtmlEntitiesTool() {
    var a by remember { mutableStateOf("<title>IT Tool</title>") }
    var b by remember { mutableStateOf("&lt;title&gt;IT Tool&lt;/title&gt;") }
    ToolColumn {
        Panel(t("Escape html entities", "Escapar entidades HTML")) {
            InputField(a, { a = it }, t("Your string", "Tu texto"), singleLine = false, minLines = 2)
            OutputField(HtmlEntities.escape(a), t("Your string escaped", "Texto escapado"), minLines = 2)
        }
        Panel(t("Unescape html entities", "Desescapar entidades HTML")) {
            InputField(b, { b = it }, t("Your escaped string", "Texto escapado"), singleLine = false, minLines = 2, mono = true)
            OutputField(HtmlEntities.unescape(b), t("Your string unescaped", "Texto desescapado"), minLines = 2)
        }
    }
}

/* ======================= URL parser ======================= */

@Composable
fun UrlParserTool() {
    var input by remember { mutableStateOf("https://me:pwd@it-tools.tech:3000/url-parser?key1=value&key2=value2#the-hash") }
    val uri = remember(input) { runCatching { URI(input.trim()).also { require(it.scheme != null) } } }
    ToolColumn {
        InputField(input, { input = it }, t("Your url to parse", "URL a analizar"), mono = true,
            error = if (uri.isFailure) t("Invalid url", "URL no válida") else null)
        uri.getOrNull()?.let { u ->
            val user = u.rawUserInfo?.substringBefore(':') ?: ""
            val pwd = u.rawUserInfo?.substringAfter(':', "") ?: ""
            KeyValueTable(listOf(
                t("Protocol", "Protocolo") to "${u.scheme}:",
                t("Username", "Usuario") to user,
                t("Password", "Contraseña") to pwd,
                "Hostname" to (u.host ?: ""),
                t("Port", "Puerto") to (if (u.port >= 0) u.port.toString() else ""),
                t("Path", "Ruta") to (u.rawPath ?: ""),
                "Params" to (u.rawQuery?.let { "?$it" } ?: ""),
                "Hash" to (u.rawFragment?.let { "#$it" } ?: ""),
                "Origin" to "${u.scheme}://${u.host ?: ""}${if (u.port >= 0) ":${u.port}" else ""}",
            ))
            val params = u.rawQuery?.split("&")?.filter { it.isNotEmpty() }?.map {
                val k = it.substringBefore('='); val v = it.substringAfter('=', "")
                runCatching { URLDecoder.decode(k, "UTF-8") }.getOrDefault(k) to runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
            } ?: emptyList()
            if (params.isNotEmpty()) {
                Text(t("Query parameters", "Parámetros"), style = MaterialTheme.typography.titleSmall)
                KeyValueTable(params)
            }
        }
    }
}

/* ======================= Device information ======================= */

@Composable
fun DeviceInfoTool() {
    val ctx = LocalContext.current
    val conf = LocalConfiguration.current
    val dm: DisplayMetrics = ctx.resources.displayMetrics
    val ua = remember { runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrDefault(System.getProperty("http.agent") ?: "") }
    val screen = listOf(
        t("Screen size", "Tamaño de pantalla") to "${dm.widthPixels} x ${dm.heightPixels}",
        t("Orientation", "Orientación") to if (conf.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait",
        t("Pixel ratio", "Densidad de píxeles") to "${dm.density} dppx (${dm.densityDpi} dpi)",
        t("Window size", "Tamaño de ventana") to "${conf.screenWidthDp} x ${conf.screenHeightDp} dp",
        t("Color depth / dark mode", "Modo oscuro del sistema") to if ((conf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "on" else "off",
    )
    val device = listOf(
        t("Manufacturer", "Fabricante") to Build.MANUFACTURER,
        t("Model", "Modelo") to Build.MODEL,
        t("Device", "Dispositivo") to Build.DEVICE,
        "OS" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "CPU ABI" to Build.SUPPORTED_ABIS.joinToString(", "),
        t("Cores", "Núcleos") to Runtime.getRuntime().availableProcessors().toString(),
        t("Languages", "Idiomas") to conf.locales.toLanguageTags(),
        t("Time zone", "Zona horaria") to TimeZone.getDefault().id,
        "User agent" to ua,
    )
    ToolColumn {
        Text(t("Screen", "Pantalla"), style = MaterialTheme.typography.titleSmall)
        KeyValueTable(screen)
        Text(t("Device", "Dispositivo"), style = MaterialTheme.typography.titleSmall)
        KeyValueTable(device)
    }
}

/* ======================= Basic auth ======================= */

@Composable
fun BasicAuthTool() {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val header = "Authorization: Basic " + "$user:$pass".toByteArray().toB64()
    ToolColumn {
        InputField(user, { user = it }, t("Username", "Usuario"))
        InputField(pass, { pass = it }, t("Password", "Contraseña"), password = true)
        OutputField(header, t("Authorization header", "Cabecera Authorization"), minLines = 2)
    }
}

/* ======================= Open Graph meta generator ======================= */

@Composable
fun MetaTagTool() {
    var type by remember { mutableStateOf("website") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var imageAlt by remember { mutableStateOf("") }
    var siteName by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("summary_large_image") }
    var twSite by remember { mutableStateOf("") }
    var twCreator by remember { mutableStateOf("") }
    val q = { s: String -> HtmlEntities.escape(s) }
    val html = buildString {
        append("<!-- Primary Meta Tags -->\n")
        if (title.isNotEmpty()) append("<title>${q(title)}</title>\n<meta name=\"title\" content=\"${q(title)}\" />\n")
        if (desc.isNotEmpty()) append("<meta name=\"description\" content=\"${q(desc)}\" />\n")
        append("\n<!-- Open Graph / Facebook -->\n<meta property=\"og:type\" content=\"$type\" />\n")
        if (siteName.isNotEmpty()) append("<meta property=\"og:site_name\" content=\"${q(siteName)}\" />\n")
        if (url.isNotEmpty()) append("<meta property=\"og:url\" content=\"${q(url)}\" />\n")
        if (title.isNotEmpty()) append("<meta property=\"og:title\" content=\"${q(title)}\" />\n")
        if (desc.isNotEmpty()) append("<meta property=\"og:description\" content=\"${q(desc)}\" />\n")
        if (image.isNotEmpty()) append("<meta property=\"og:image\" content=\"${q(image)}\" />\n")
        if (imageAlt.isNotEmpty()) append("<meta property=\"og:image:alt\" content=\"${q(imageAlt)}\" />\n")
        append("\n<!-- Twitter -->\n<meta name=\"twitter:card\" content=\"$card\" />\n")
        if (twSite.isNotEmpty()) append("<meta name=\"twitter:site\" content=\"${q(twSite)}\" />\n")
        if (twCreator.isNotEmpty()) append("<meta name=\"twitter:creator\" content=\"${q(twCreator)}\" />\n")
        if (title.isNotEmpty()) append("<meta name=\"twitter:title\" content=\"${q(title)}\" />\n")
        if (desc.isNotEmpty()) append("<meta name=\"twitter:description\" content=\"${q(desc)}\" />\n")
        if (image.isNotEmpty()) append("<meta name=\"twitter:image\" content=\"${q(image)}\" />\n")
    }.trimEnd()
    ToolColumn {
        Panel(t("General information", "Información general")) {
            Dropdown(t("Page type", "Tipo de página"), listOf("website", "article", "book", "profile", "music.song", "music.album", "video.movie", "video.episode").map { it to it }, type, { type = it })
            InputField(title, { title = it }, t("Title", "Título"))
            InputField(desc, { desc = it }, t("Description", "Descripción"), singleLine = false, minLines = 2)
            InputField(url, { url = it }, t("Page URL", "URL de la página"))
            InputField(siteName, { siteName = it }, t("Site name", "Nombre del sitio"))
        }
        Panel(t("Image", "Imagen")) {
            InputField(image, { image = it }, t("Image URL", "URL de la imagen"))
            InputField(imageAlt, { imageAlt = it }, t("Image alt", "Texto alternativo"))
        }
        Panel("Twitter") {
            Dropdown(t("Card type", "Tipo de tarjeta"), listOf("summary", "summary_large_image", "app", "player").map { it to it }, card, { card = it })
            InputField(twSite, { twSite = it }, t("Site account (@...)", "Cuenta del sitio (@...)"))
            InputField(twCreator, { twCreator = it }, t("Creator account (@...)", "Cuenta del autor (@...)"))
        }
        OutputField(html, t("Your meta tags", "Tus meta tags"), minLines = 6)
    }
}

/* ======================= OTP ======================= */

object Otp {
    fun hotp(secret: ByteArray, counter: Long, digits: Int = 6, algo: HashAlgo = HashAlgo.SHA1): String {
        val msg = java.nio.ByteBuffer.allocate(8).putLong(counter).array()
        val h = hmac(algo, secret, msg)
        val o = h.last().toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        return (bin % Math.pow(10.0, digits.toDouble()).toInt()).toString().padStart(digits, '0')
    }
}

@Composable
fun OtpTool() {
    var secret by remember { mutableStateOf(Base32.encode(randomBytes(10))) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val period = 30
    val counter = now / 1000 / period
    val key = runCatching { Base32.decode(secret) }.getOrNull()?.takeIf { it.isNotEmpty() }
    val uri = "otpauth://totp/IT-Tools:demo-user?issuer=IT-Tools&secret=${secret.uppercase()}&algorithm=SHA1&digits=6&period=$period"
    val qr = remember(uri) { Qr.bitmap(uri, 480) }
    ToolColumn {
        InputField(secret, { secret = it.uppercase() }, t("Secret (base32)", "Secreto (base32)"), mono = true,
            error = if (key == null) t("Secret should be a base32 string", "El secreto debe estar en base32") else null,
            trailing = { IconButton(onClick = { secret = Base32.encode(randomBytes(10)) }) { Icon(androidx.compose.material.icons.Icons.Outlined.Refresh, null) } })
        if (key != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1L to t("Previous", "Anterior"), 0L to t("Current OTP", "OTP actual"), 1L to t("Next", "Siguiente")).forEach { (d, l) ->
                    Panel {
                        Text(l, style = MaterialTheme.typography.labelSmall)
                        Text(Otp.hotp(key, counter + d), style = if (d == 0L) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (d == 0L) FontWeight.Bold else FontWeight.Normal, fontFamily = Mono)
                    }.let { }
                }
            }
            val remaining = period - (now / 1000 % period)
            LinearProgressIndicator(progress = { remaining / period.toFloat() }, modifier = Modifier.fillMaxWidth())
            Hint(t("Next in ${remaining}s", "Siguiente en ${remaining}s"))
            OutputField(Otp.hotp(key, counter), t("Current code", "Código actual"), singleLine = true)
            qr?.let { Image(it.asImageBitmap(), null, Modifier.size(220.dp).align(Alignment.CenterHorizontally)) }
            OutputField(uri, t("Key URI", "URI de la clave"), minLines = 2)
            KeyValueTable(listOf(
                t("Secret in hexadecimal", "Secreto en hexadecimal") to key.toHex(),
                t("Epoch", "Epoch") to (now / 1000).toString(),
                t("Iteration count", "Contador") to counter.toString(),
                t("Padded hex", "Contador en hex") to "%016x".format(counter),
            ))
        }
    }
}

/* ======================= MIME types ======================= */

private val COMMON_MIME = listOf(
    "aac" to "audio/aac", "avif" to "image/avif", "avi" to "video/x-msvideo", "bin" to "application/octet-stream", "bmp" to "image/bmp",
    "bz2" to "application/x-bzip2", "css" to "text/css", "csv" to "text/csv", "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "epub" to "application/epub+zip",
    "gz" to "application/gzip", "gif" to "image/gif", "htm" to "text/html", "html" to "text/html", "ico" to "image/vnd.microsoft.icon",
    "ics" to "text/calendar", "jar" to "application/java-archive", "jpeg" to "image/jpeg", "jpg" to "image/jpeg", "js" to "text/javascript",
    "json" to "application/json", "jsonld" to "application/ld+json", "md" to "text/markdown", "mid" to "audio/midi", "mjs" to "text/javascript",
    "mp3" to "audio/mpeg", "mp4" to "video/mp4", "mpeg" to "video/mpeg", "odp" to "application/vnd.oasis.opendocument.presentation",
    "ods" to "application/vnd.oasis.opendocument.spreadsheet", "odt" to "application/vnd.oasis.opendocument.text", "oga" to "audio/ogg",
    "ogv" to "video/ogg", "otf" to "font/otf", "png" to "image/png", "pdf" to "application/pdf", "php" to "application/x-httpd-php",
    "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "rar" to "application/vnd.rar", "rtf" to "application/rtf", "sh" to "application/x-sh", "svg" to "image/svg+xml", "tar" to "application/x-tar",
    "tif" to "image/tiff", "tiff" to "image/tiff", "toml" to "application/toml", "ts" to "video/mp2t", "ttf" to "font/ttf", "txt" to "text/plain",
    "wasm" to "application/wasm", "wav" to "audio/wav", "weba" to "audio/webm", "webm" to "video/webm", "webp" to "image/webp", "woff" to "font/woff",
    "woff2" to "font/woff2", "xhtml" to "application/xhtml+xml", "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xml" to "application/xml", "yaml" to "application/yaml",
    "yml" to "application/yaml", "zip" to "application/zip", "7z" to "application/x-7z-compressed", "apk" to "application/vnd.android.package-archive",
)

@Composable
fun MimeTypesTool() {
    val map = MimeTypeMap.getSingleton()
    var mime by remember { mutableStateOf("image/png") }
    var ext by remember { mutableStateOf("pdf") }
    val exts = remember(mime) {
        (COMMON_MIME.filter { it.second == mime.trim().lowercase() }.map { it.first } +
            listOfNotNull(map.getExtensionFromMimeType(mime.trim().lowercase()))).distinct()
    }
    val cleanExt = ext.trim().trimStart('.').lowercase()
    val mimeOfExt = remember(cleanExt) { map.getMimeTypeFromExtension(cleanExt) ?: COMMON_MIME.firstOrNull { it.first == cleanExt }?.second }
    ToolColumn {
        Panel(t("Mime type to extension", "Tipo MIME a extensión")) {
            InputField(mime, { mime = it }, t("Mime type", "Tipo MIME"), mono = true)
            OutputField(exts.joinToString(", ") { ".$it" }, t("Extensions", "Extensiones"), singleLine = true)
        }
        Panel(t("File extension to mime type", "Extensión a tipo MIME")) {
            InputField(ext, { ext = it }, t("Extension", "Extensión"), mono = true)
            OutputField(mimeOfExt ?: "", t("Mime type", "Tipo MIME"), singleLine = true)
        }
        Text(t("Common MIME types", "Tipos MIME comunes"), style = MaterialTheme.typography.titleSmall)
        KeyValueTable(COMMON_MIME.map { ".${it.first}" to it.second })
    }
}

/* ======================= JWT ======================= */

private val JWT_CLAIMS = mapOf(
    "alg" to ("Algorithm" to "Algoritmo"), "typ" to ("Type" to "Tipo"), "kid" to ("Key ID" to "ID de clave"), "cty" to ("Content type" to "Tipo de contenido"),
    "iss" to ("Issuer" to "Emisor"), "sub" to ("Subject" to "Sujeto"), "aud" to ("Audience" to "Audiencia"),
    "exp" to ("Expiration time" to "Expiración"), "nbf" to ("Not before" to "No antes de"), "iat" to ("Issued at" to "Emitido en"),
    "jti" to ("JWT ID" to "ID del JWT"), "name" to ("Full name" to "Nombre completo"), "email" to ("Email" to "Email"),
    "scope" to ("Scope" to "Ámbito"), "azp" to ("Authorized party" to "Parte autorizada"), "nonce" to ("Nonce" to "Nonce"),
)

@Composable
fun JwtParserTool() {
    var jwt by remember { mutableStateOf("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c") }
    val es = isSpanish()
    val parsed = remember(jwt) {
        runCatching {
            val parts = jwt.trim().split(".")
            require(parts.size >= 2)
            Json.parse(String(decodeB64(parts[0]))) as Map<*, *> to Json.parse(String(decodeB64(parts[1]))) as Map<*, *>
        }
    }
    @Composable
    fun claims(m: Map<*, *>) = KeyValueTable(m.map { (k, v) ->
        val d = JWT_CLAIMS[k.toString()]?.let { if (es) it.second else it.first }
        val value = if (k in listOf("exp", "nbf", "iat") && v is Number) "$v (${Instant.ofEpochSecond(v.toLong())})"
        else if (v is String) v else Json.stringify(v, 0)
        (if (d != null) "$k · $d" else k.toString()) to value
    })
    ToolColumn {
        InputField(jwt, { jwt = it }, "JWT", singleLine = false, minLines = 4, mono = true,
            error = if (parsed.isFailure) t("Invalid JWT", "JWT no válido") else null)
        parsed.getOrNull()?.let { (h, p) ->
            Text("Header", style = MaterialTheme.typography.titleSmall); claims(h)
            Text("Payload", style = MaterialTheme.typography.titleSmall); claims(p)
        }
    }
}

/* ======================= Keycode info ======================= */

@Composable
fun KeycodeTool() {
    var info by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var typed by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    ToolColumn {
        Box(
            Modifier.fillMaxWidth().height(140.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .focusRequester(fr).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        val n: NativeKeyEvent = e.nativeKeyEvent
                        val mods = listOfNotNull(
                            "Ctrl".takeIf { e.isCtrlPressed }, "Alt".takeIf { e.isAltPressed },
                            "Shift".takeIf { e.isShiftPressed }, "Meta".takeIf { e.isMetaPressed })
                        val ch = n.unicodeChar.takeIf { it > 0 }?.let { String(Character.toChars(it)) } ?: ""
                        info = listOf(
                            "Key" to (ch.ifEmpty { NativeKeyEvent.keyCodeToString(n.keyCode).removePrefix("KEYCODE_") }),
                            "Keycode (Android)" to n.keyCode.toString(),
                            "Code" to NativeKeyEvent.keyCodeToString(n.keyCode),
                            "Scan code" to n.scanCode.toString(),
                            "Unicode" to (if (ch.isNotEmpty()) "U+%04X".format(ch.codePointAt(0)) else ""),
                            "Modifiers" to mods.joinToString(" + "),
                        )
                    }
                    false
                },
            contentAlignment = Alignment.Center
        ) {
            Text(info?.first()?.second ?: t("Press any key on a physical keyboard", "Pulsa cualquier tecla de un teclado físico"),
                style = if (info == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.displaySmall)
        }
        info?.let { KeyValueTable(it) }
        Panel(t("Soft keyboard", "Teclado en pantalla")) {
            InputField(typed, { typed = it }, t("Type here", "Escribe aquí"))
            typed.lastOrNull()?.let { c ->
                KeyValue(t("Last character", "Último carácter"), c.toString())
                KeyValue("Char code", c.code.toString())
                KeyValue("Unicode", "U+%04X".format(c.code))
            }
        }
    }
}

/* ======================= Slugify ======================= */

fun slugify(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace("ß", "ss").replace("æ", "ae").replace("ø", "o").replace("đ", "d").replace("ł", "l")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')

@Composable
fun SlugifyTool() {
    var input by remember { mutableStateOf("") }
    ToolColumn {
        InputField(input, { input = it }, t("Your string to slugify", "Texto a convertir"), singleLine = false, minLines = 2)
        OutputField(slugify(input), t("Your slug", "Tu slug"), minLines = 2)
    }
}

/* ======================= HTML WYSIWYG editor ======================= */

class EditorBridge(val onHtml: (String) -> Unit) {
    @JavascriptInterface fun update(html: String) = onHtml(html)
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WysiwygTool() {
    var html by remember { mutableStateOf("<h1>Hey!</h1><p>Welcome to this html editor</p>") }
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val page = remember(dark) {
        """
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          body{margin:0;font-family:sans-serif;${if (dark) "background:#232323;color:#e6e6e6;" else ""}}
          #bar{position:sticky;top:0;display:flex;flex-wrap:wrap;gap:4px;padding:6px;background:${if (dark) "#2e2e2e" else "#f3f5f7"}}
          #bar button{min-width:34px;height:32px;border:1px solid #8886;border-radius:6px;background:transparent;color:inherit;font-size:14px}
          #ed{min-height:260px;padding:10px;outline:none}
          blockquote{border-left:3px solid #18a058;margin:0;padding-left:10px}
          pre{background:#8882;padding:8px}
        </style></head><body>
        <div id="bar">
          <button onclick="c('bold')"><b>B</b></button><button onclick="c('italic')"><i>I</i></button>
          <button onclick="c('underline')"><u>U</u></button><button onclick="c('strikeThrough')"><s>S</s></button>
          <button onclick="c('formatBlock','H1')">H1</button><button onclick="c('formatBlock','H2')">H2</button>
          <button onclick="c('formatBlock','H3')">H3</button><button onclick="c('formatBlock','P')">¶</button>
          <button onclick="c('insertUnorderedList')">• —</button><button onclick="c('insertOrderedList')">1.</button>
          <button onclick="c('formatBlock','BLOCKQUOTE')">❝</button><button onclick="c('formatBlock','PRE')">&lt;/&gt;</button>
          <button onclick="var u=prompt('URL');if(u)c('createLink',u)">🔗</button>
          <button onclick="c('insertHorizontalRule')">―</button>
          <button onclick="c('undo')">↶</button><button onclick="c('redo')">↷</button>
          <button onclick="c('removeFormat')">⌫</button>
        </div>
        <div id="ed" contenteditable="true"></div>
        <script>
          var ed=document.getElementById('ed');
          ed.innerHTML=${Json.quote(html)};
          function c(cmd,v){document.execCommand(cmd,false,v||null);ed.focus();send();}
          function send(){Android.update(ed.innerHTML);}
          ed.addEventListener('input',send);
        </script></body></html>
        """.trimIndent()
    }
    ToolColumn {
        Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(8.dp)) {
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        addJavascriptInterface(EditorBridge { h -> handler.post { html = h } }, "Android")
                        loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(340.dp)
            )
        }
        OutputField(prettyHtml(html), t("HTML source", "Código HTML"), minLines = 6)
    }
}

/** Salto de línea tras etiquetas de bloque para que el código sea legible. */
fun prettyHtml(h: String) = h.replace(Regex("(</(p|h[1-6]|li|ul|ol|blockquote|pre|div)>|<hr>|<br>)"), "$1\n").trim()

/* ======================= User-agent parser ======================= */

@Composable
fun UserAgentTool() {
    val ctx = LocalContext.current
    val default = remember { runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrDefault("") }
    var ua by remember { mutableStateOf(default) }
    val i = remember(ua) { UserAgentParser.parse(ua) }
    val none = t("No information available", "Sin información")
    fun v(s: String) = s.ifEmpty { none }
    ToolColumn {
        InputField(ua, { ua = it }, t("User agent string", "Cadena user-agent"), singleLine = false, minLines = 3, mono = true)
        KeyValueTable(listOf(
            t("Browser", "Navegador") to v(listOf(i.browser, i.browserVer).filter { it.isNotEmpty() }.joinToString(" ")),
            t("Engine", "Motor") to v(listOf(i.engine, i.engineVer).filter { it.isNotEmpty() }.joinToString(" ")),
            "OS" to v(listOf(i.os, i.osVer).filter { it.isNotEmpty() }.joinToString(" ")),
            t("Device type", "Tipo de dispositivo") to v(i.device),
            t("Device vendor", "Fabricante") to v(i.deviceVendor),
            t("Device model", "Modelo") to v(i.deviceModel),
            "CPU" to v(i.cpu),
        ), copy = false)
    }
}

/* ======================= HTTP status codes ======================= */

@Composable
fun HttpStatusTool() {
    var q by remember { mutableStateOf("") }
    val es = isSpanish()
    val filtered = HttpStatus.codes.filter {
        q.isBlank() || it.code.toString().contains(q) || it.name.contains(q, true) || (if (es) it.es else it.en).contains(q, true)
    }
    ToolColumn {
        InputField(q, { q = it }, t("Search http status...", "Buscar código HTTP..."))
        HttpStatus.categories.forEachIndexed { idx, (en, esLabel) ->
            val list = filtered.filter { it.code / 100 == idx + 1 }
            if (list.isNotEmpty()) {
                Text(if (es) esLabel else en, style = MaterialTheme.typography.titleSmall)
                list.forEach { c ->
                    Panel {
                        Text("${c.code} ${c.name}", fontWeight = FontWeight.SemiBold)
                        Text(if (es) c.es else c.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/* ======================= JSON diff ======================= */

object JsonDiff {
    sealed class D(val path: String) {
        class Added(p: String, val v: Any?) : D(p)
        class Removed(p: String, val v: Any?) : D(p)
        class Changed(p: String, val a: Any?, val b: Any?) : D(p)
        class Same(p: String, val v: Any?) : D(p)
    }
    fun diff(a: Any?, b: Any?, path: String = "$", out: MutableList<D> = mutableListOf()): List<D> {
        when {
            a is Map<*, *> && b is Map<*, *> -> (a.keys + b.keys).distinct().forEach { k ->
                val p = "$path.$k"
                when {
                    !b.containsKey(k) -> out += D.Removed(p, a[k])
                    !a.containsKey(k) -> out += D.Added(p, b[k])
                    else -> diff(a[k], b[k], p, out)
                }
            }
            a is List<*> && b is List<*> -> for (i in 0 until maxOf(a.size, b.size)) {
                val p = "$path[$i]"
                when { i >= b.size -> out += D.Removed(p, a[i]); i >= a.size -> out += D.Added(p, b[i]); else -> diff(a[i], b[i], p, out) }
            }
            numEq(a, b) || a == b -> out += D.Same(path, a)
            else -> out += D.Changed(path, a, b)
        }
        return out
    }
    private fun numEq(a: Any?, b: Any?) = a is Number && b is Number && a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal()) == 0
}

@Composable
fun JsonDiffTool() {
    var a by remember { mutableStateOf("{\"a\": 1, \"b\": [1, 2], \"c\": {\"d\": \"x\"}}") }
    var b by remember { mutableStateOf("{\"a\": 2, \"b\": [1, 2, 3], \"c\": {\"e\": \"y\"}}") }
    var onlyDiff by remember { mutableStateOf(true) }
    val pa = remember(a) { runCatching { Json.parse(a) } }
    val pb = remember(b) { runCatching { Json.parse(b) } }
    val green = androidx.compose.ui.graphics.Color(0xFF18A058)
    val red = MaterialTheme.colorScheme.error
    val amber = androidx.compose.ui.graphics.Color(0xFFF0A020)
    ToolColumn {
        InputField(a, { a = it }, t("Your first JSON", "Primer JSON"), singleLine = false, minLines = 5, mono = true, error = pa.exceptionOrNull()?.message)
        InputField(b, { b = it }, t("Your JSON to compare", "JSON a comparar"), singleLine = false, minLines = 5, mono = true, error = pb.exceptionOrNull()?.message)
        SwitchRow(t("Only show differences", "Mostrar solo diferencias"), onlyDiff) { onlyDiff = it }
        if (pa.isSuccess && pb.isSuccess) {
            val d = remember(a, b) { JsonDiff.diff(pa.getOrNull(), pb.getOrNull()) }
            val shown = if (onlyDiff) d.filter { it !is JsonDiff.D.Same } else d
            if (shown.isEmpty()) Text(t("The provided JSONs are the same", "Los JSON son idénticos"), color = green)
            Panel {
                shown.forEach { x ->
                    val (txt, col) = when (x) {
                        is JsonDiff.D.Added -> "+ ${x.path}: ${Json.stringify(x.v, 0)}" to green
                        is JsonDiff.D.Removed -> "- ${x.path}: ${Json.stringify(x.v, 0)}" to red
                        is JsonDiff.D.Changed -> "~ ${x.path}: ${Json.stringify(x.a, 0)} → ${Json.stringify(x.b, 0)}" to amber
                        is JsonDiff.D.Same -> "  ${x.path}: ${Json.stringify(x.v, 0)}" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(txt, color = col, fontFamily = Mono, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/* ======================= Outlook Safelink ======================= */

@Composable
fun SafelinkTool() {
    var input by remember { mutableStateOf("") }
    val out = remember(input) {
        runCatching {
            val u = URI(input.trim())
            require(u.host?.contains("safelinks.protection.outlook.com") == true)
            val q = u.rawQuery.split("&").first { it.startsWith("url=") }.removePrefix("url=")
            URLDecoder.decode(q, "UTF-8")
        }
    }
    ToolColumn {
        InputField(input, { input = it }, t("Your input Outlook SafeLink Url", "URL SafeLink de Outlook"), singleLine = false, minLines = 3, mono = true,
            placeholder = "https://aus01.safelinks.protection.outlook.com/?url=...",
            error = if (input.isNotBlank() && out.isFailure) t("Invalid SafeLinks URL provided", "URL SafeLinks no válida") else null)
        OutputField(out.getOrDefault(""), t("Output decoded URL", "URL decodificada"), minLines = 2)
    }
}
