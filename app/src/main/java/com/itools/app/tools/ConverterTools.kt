package com.itools.app.tools

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itools.app.ui.components.*
import com.itools.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.math.BigInteger
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/* ======================= Generic: text → text converter ======================= */

@Composable
fun FormatConverter(
    inLabel: String, outLabel: String, sample: String,
    convert: (String) -> String,
) {
    var input by remember { mutableStateOf(sample) }
    val result = remember(input) { if (input.isBlank()) Result.success("") else runCatching { convert(input) } }
    ToolColumn {
        InputField(input, { input = it }, inLabel, singleLine = false, minLines = 8, mono = true,
            error = result.exceptionOrNull()?.let { (it.message ?: it.javaClass.simpleName).take(300) })
        OutputField(result.getOrDefault(""), outLabel, minLines = 8)
    }
}

private const val SAMPLE_JSON = "{\n  \"foo\": \"bar\",\n  \"list\": [\"name\", \"lastname\"],\n  \"object\": {\n    \"key\": \"value\",\n    \"n\": 42\n  }\n}"
private const val SAMPLE_YAML = "foo: bar\nlist:\n  - name\n  - lastname\nobject:\n  key: value\n  n: 42\n"
private const val SAMPLE_TOML = "foo = \"bar\"\nlist = [ \"name\", \"lastname\" ]\n\n[object]\nkey = \"value\"\nn = 42\n"
private const val SAMPLE_XML = "<a x=\"1.234\" y=\"It's\"><b>hello</b><c>world</c></a>"

@Composable fun YamlToJsonTool() = FormatConverter("YAML", "JSON", SAMPLE_YAML) { Json.stringify(Yaml.parse(it), 2) }
@Composable fun YamlToTomlTool() = FormatConverter("YAML", "TOML", SAMPLE_YAML) { TomlFmt.stringify(Yaml.parse(it)) }
@Composable fun JsonToYamlTool() = FormatConverter("JSON", "YAML", SAMPLE_JSON) { Yaml.stringify(Json.parse(it)) }
@Composable fun JsonToTomlTool() = FormatConverter("JSON", "TOML", SAMPLE_JSON) { TomlFmt.stringify(Json.parse(it)) }
@Composable fun TomlToJsonTool() = FormatConverter("TOML", "JSON", SAMPLE_TOML) { Json.stringify(TomlFmt.parse(it), 2) }
@Composable fun TomlToYamlTool() = FormatConverter("TOML", "YAML", SAMPLE_TOML) { Yaml.stringify(TomlFmt.parse(it)) }
@Composable fun XmlToJsonTool() = FormatConverter("XML", "JSON", SAMPLE_XML) { Json.stringify(XmlFmt.toTree(it), 2) }
@Composable fun JsonToXmlTool() = FormatConverter("JSON", "XML", "{\"a\":{\"_attributes\":{\"x\":\"1.234\"},\"b\":{\"_text\":\"hello\"},\"c\":\"world\"}}") { XmlFmt.fromTree(Json.parse(it)) }

/* ======================= Date-time converter ======================= */

object Dates {
    val excelEpoch: LocalDateTime = LocalDateTime.of(1899, 12, 30, 0, 0)
    private val MONGO_ID = Regex("^[0-9a-fA-F]{24}$")
    private val INTEGER = Regex("^-?\\d+$")
    private val DECIMAL = Regex("^\\d+\\.\\d+$")
    private val ISO_9075 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val ISO_9075_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    fun parse(s: String, zone: ZoneId): Pair<Instant, String>? {
        val t = s.trim()
        if (t.isEmpty()) return null
        if (MONGO_ID.matches(t) && !t.all { it.isDigit() })
            return Instant.ofEpochSecond(t.substring(0, 8).toLong(16)) to "Mongo ObjectID"
        if (INTEGER.matches(t)) {
            val n = t.toLong()
            return if (t.trimStart('-').length >= 12) Instant.ofEpochMilli(n) to "Timestamp (ms)" else Instant.ofEpochSecond(n) to "Unix timestamp"
        }
        if (DECIMAL.matches(t))
            return excelEpoch.plusNanos((t.toDouble() * 86400e9).toLong()).atZone(zone).toInstant() to "Excel"
        val tries: List<Pair<String, () -> Instant>> = listOf(
            "ISO 8601" to { OffsetDateTime.parse(t).toInstant() },
            "ISO 8601" to { ZonedDateTime.parse(t).toInstant() },
            "ISO 8601" to { Instant.parse(t) },
            "ISO 8601" to { LocalDateTime.parse(t).atZone(zone).toInstant() },
            "ISO 9075" to { LocalDateTime.parse(t, ISO_9075).atZone(zone).toInstant() },
            "ISO 9075" to { LocalDateTime.parse(t, ISO_9075_SHORT).atZone(zone).toInstant() },
            "ISO date" to { LocalDate.parse(t).atStartOfDay(zone).toInstant() },
            "RFC 7231" to { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
            "RFC 3339" to { OffsetDateTime.parse(t.replace(' ', 'T')).toInstant() },
        )
        for ((name, f) in tries) runCatching(f).getOrNull()?.let { return it to name }
        return null
    }

    fun formats(i: Instant, zone: ZoneId, locale: Locale): List<Pair<String, String>> {
        val z = i.atZone(zone)
        val excel = Duration.between(excelEpoch.atZone(zone), z).toMillis() / 86_400_000.0
        return listOf(
            "Locale" to z.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).withLocale(locale)),
            "ISO 8601" to z.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "ISO 9075" to z.format(ISO_9075),
            "RFC 3339" to z.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "RFC 7231" to i.atZone(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME),
            "Unix timestamp" to i.epochSecond.toString(),
            "Timestamp (ms)" to i.toEpochMilli().toString(),
            "UTC format" to i.toString(),
            "Mongo ObjectID" to "%08x".format(i.epochSecond) + "0000000000000000",
            "Excel date/time" to "%.6f".format(Locale.US, excel).trimEnd('0').trimEnd('.'),
        )
    }
}

@Composable
fun DateConverterTool() {
    var input by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1000) } }
    val zone = remember { ZoneId.systemDefault() }
    val locale = if (isSpanish()) Locale("es", "ES") else Locale.ENGLISH
    val parsed = remember(input) { Dates.parse(input, zone) }
    val instant = if (input.isBlank()) now else parsed?.first
    ToolColumn {
        InputField(input, { input = it }, t("Put your date string here...", "Escribe aquí tu fecha..."),
            placeholder = t("Empty = now", "Vacío = ahora"),
            error = if (input.isNotBlank() && parsed == null) t("This date is invalid for any known format", "Fecha no válida en ningún formato conocido") else null)
        parsed?.let { Hint(t("Detected format: ", "Formato detectado: ") + it.second) }
        Hint(t("Time zone: ", "Zona horaria: ") + zone.id)
        if (instant != null) KeyValueTable(Dates.formats(instant, zone, locale))
    }
}

/* ======================= Integer base converter ======================= */

object BaseConv {
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/"
    fun parse(s: String, base: Int): BigInteger {
        val str = s.trim().let { if (base <= 36) it.uppercase() else it }
        require(str.isNotEmpty())
        val neg = str.startsWith("-")
        var r = BigInteger.ZERO
        val b = BigInteger.valueOf(base.toLong())
        for (c in str.removePrefix("-")) {
            val d = DIGITS.indexOf(c)
            require(d in 0 until base) { "Invalid digit '$c' for base $base" }
            r = r * b + BigInteger.valueOf(d.toLong())
        }
        return if (neg) r.negate() else r
    }
    fun format(n: BigInteger, base: Int): String {
        if (n.signum() == 0) return "0"
        var x = n.abs(); val b = BigInteger.valueOf(base.toLong())
        val sb = StringBuilder()
        while (x.signum() > 0) { val (q, r) = x.divideAndRemainder(b); sb.append(DIGITS[r.toInt()]); x = q }
        if (n.signum() < 0) sb.append('-')
        return sb.reverse().toString()
    }
}

@Composable
fun BaseConverterTool() {
    var input by remember { mutableStateOf("42") }
    var inBase by remember { mutableIntStateOf(10) }
    var custom by remember { mutableIntStateOf(42) }
    val n = remember(input, inBase) { runCatching { BaseConv.parse(input, inBase) } }
    ToolColumn {
        InputField(input, { input = it }, t("Input number", "Número de entrada"), mono = true,
            error = n.exceptionOrNull()?.let { t("Invalid number for this base", "Número no válido para esta base") })
        NumberField(inBase, { inBase = it }, t("Input base", "Base de entrada"), min = 2, max = 64)
        val v = n.getOrNull()
        if (v != null) {
            OutputField(BaseConv.format(v, 2), t("Binary (2)", "Binario (2)"), singleLine = true)
            OutputField(BaseConv.format(v, 8), t("Octal (8)", "Octal (8)"), singleLine = true)
            OutputField(BaseConv.format(v, 10), t("Decimal (10)", "Decimal (10)"), singleLine = true)
            OutputField(BaseConv.format(v, 16), t("Hexadecimal (16)", "Hexadecimal (16)"), singleLine = true)
            OutputField(BaseConv.format(v, 64), "Base64 (64)", singleLine = true)
            NumberField(custom, { custom = it }, t("Custom base", "Base personalizada"), min = 2, max = 64)
            OutputField(BaseConv.format(v, custom.coerceIn(2, 64)), t("Custom", "Personalizada") + " ($custom)", singleLine = true)
        }
    }
}

/* ======================= Roman numerals ======================= */

object Roman {
    private val table = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    private val VALID = Regex("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")
    fun toRoman(n: Int): String {
        require(n in 1..3999)
        var x = n; val sb = StringBuilder()
        for ((v, s) in table) while (x >= v) { sb.append(s); x -= v }
        return sb.toString()
    }
    fun fromRoman(s: String): Int {
        val up = s.trim().uppercase()
        require(up.isNotEmpty() && VALID.matches(up))
        var i = 0; var r = 0
        for ((v, sym) in table) while (up.startsWith(sym, i)) { r += v; i += sym.length }
        return r
    }
}

@Composable
fun RomanTool() {
    var arabic by remember { mutableStateOf("42") }
    var roman by remember { mutableStateOf("XLII") }
    val toR = remember(arabic) { runCatching { Roman.toRoman(arabic.trim().toInt()) } }
    val toA = remember(roman) { runCatching { Roman.fromRoman(roman) } }
    ToolColumn {
        Panel(t("Arabic to roman", "Arábigo a romano")) {
            InputField(arabic, { arabic = it }, t("Number (1-3999)", "Número (1-3999)"),
                error = if (toR.isFailure) t("We can only convert numbers between 1 and 3,999", "Solo se pueden convertir números entre 1 y 3999") else null)
            OutputField(toR.getOrDefault(""), t("Roman", "Romano"), singleLine = true)
        }
        Panel(t("Roman to arabic", "Romano a arábigo")) {
            InputField(roman, { roman = it }, t("Roman numeral", "Número romano"),
                error = if (toA.isFailure && roman.isNotBlank()) t("Invalid roman number", "Número romano no válido") else null)
            OutputField(toA.getOrNull()?.toString() ?: "", t("Arabic", "Arábigo"), singleLine = true)
        }
    }
}

/* ======================= Base64 string ======================= */

@Composable
fun Base64StringTool() {
    var urlSafe by remember { mutableStateOf(false) }
    var plain by remember { mutableStateOf("Hello world") }
    var encoded by remember { mutableStateOf("SGVsbG8gd29ybGQ=") }
    val enc = remember(plain, urlSafe) { plain.toByteArray().let { if (urlSafe) it.toB64Url() else it.toB64() } }
    val dec = remember(encoded) { runCatching { String(decodeB64(encoded), Charsets.UTF_8) } }
    ToolColumn {
        SwitchRow(t("Encode URL safe", "Codificación segura para URL"), urlSafe) { urlSafe = it }
        Panel(t("String to base64", "Texto a Base64")) {
            InputField(plain, { plain = it }, t("String to encode", "Texto a codificar"), singleLine = false, minLines = 3)
            OutputField(enc, "Base64", minLines = 2)
        }
        Panel(t("Base64 to string", "Base64 a texto")) {
            InputField(encoded, { encoded = it }, t("Base64 string to decode", "Base64 a decodificar"), singleLine = false, minLines = 3, mono = true,
                error = if (dec.isFailure) t("Invalid base64 string", "Cadena Base64 no válida") else null)
            OutputField(dec.getOrDefault(""), t("Decoded string", "Texto decodificado"), minLines = 2, mono = false)
        }
    }
}

/* ======================= Base64 file ======================= */

@Composable
fun Base64FileTool() {
    val ctx = LocalContext.current
    var fileB64 by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var mime by remember { mutableStateOf("") }
    var dataUri by remember { mutableStateOf(true) }
    var pastedB64 by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked = it }
    LaunchedEffect(picked) {
        val u = picked ?: return@LaunchedEffect
        mime = ctx.contentResolver.getType(u) ?: "application/octet-stream"
        fileName = ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: ""
        fileB64 = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes().toB64() } ?: "" }
    }
    val decoded = remember(pastedB64) { runCatching { decodeB64(pastedB64) }.getOrNull()?.takeIf { it.isNotEmpty() } }
    val pastedMime = remember(pastedB64) { Regex("^data:([^;]+);base64,").find(pastedB64.trim())?.groupValues?.get(1) ?: "application/octet-stream" }
    val bmp = remember(decoded) { decoded?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(pastedMime)) { u ->
        if (u != null && decoded != null) ctx.contentResolver.openOutputStream(u)?.use { it.write(decoded) }
    }
    ToolColumn {
        Panel(t("File to base64", "Archivo a Base64")) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text(t("Pick a file", "Elegir archivo")) }
            if (fileName.isNotEmpty()) Hint("$fileName · $mime · ${fileB64.length} chars")
            SwitchRow(t("Include data URI prefix", "Incluir prefijo data URI"), dataUri) { dataUri = it }
            OutputField(if (fileB64.isEmpty()) "" else if (dataUri) "data:$mime;base64,$fileB64" else fileB64, "Base64", minLines = 3)
        }
        Panel(t("Base64 to file", "Base64 a archivo")) {
            InputField(pastedB64, { pastedB64 = it }, t("Paste your base64 string here", "Pega aquí la cadena Base64"), singleLine = false, minLines = 3, mono = true,
                error = if (pastedB64.isNotBlank() && decoded == null) t("Invalid base64 string", "Cadena Base64 no válida") else null)
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.heightIn(max = 240.dp)) }
            Button(onClick = { save.launch("file." + (android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(pastedMime) ?: "bin")) },
                enabled = decoded != null) { Text(t("Download file", "Guardar archivo")) }
        }
    }
}

/* ======================= Color converter ======================= */

@Composable
fun ColorConverterTool() {
    var input by remember { mutableStateOf("#1ea54c") }
    val color = remember(input) { ColorConv.parse(input) }
    val formats = remember(color) { color?.let { ColorConv.formats(it) } }
    ToolColumn {
        InputField(input, { input = it }, t("Any CSS color (hex, rgb, hsl, hwb, lch, cmyk, name)", "Cualquier color CSS (hex, rgb, hsl, hwb, lch, cmyk, nombre)"),
            mono = true, error = if (color == null) t("Invalid color", "Color no válido") else null)
        color?.let { c ->
            Box(
                Modifier.fillMaxWidth().height(64.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(Color(c.argb), RoundedCornerShape(8.dp))
            )
            listOf("R" to c.r8, "G" to c.g8, "B" to c.b8).forEachIndexed { idx, (l, v) ->
                SliderRow(l, v, 0..255) { nv ->
                    val n = when (idx) { 0 -> c.copy(r = nv / 255.0); 1 -> c.copy(g = nv / 255.0); else -> c.copy(b = nv / 255.0) }
                    input = ColorConv.formats(n)[1].second
                }
            }
            formats?.let { KeyValueTable(it) }
        }
    }
}

/* ======================= Case converter ======================= */

object Case {
    private val LOWER_UPPER = Regex("([\\p{Ll}\\d])(\\p{Lu})")
    private val UPPER_WORD = Regex("(\\p{Lu})(\\p{Lu}\\p{Ll})")
    private val SEPARATORS = Regex("[^\\p{L}\\d]+")
    fun words(s: String): List<String> = s
        .replace(LOWER_UPPER, "$1 $2")
        .replace(UPPER_WORD, "$1 $2")
        .split(SEPARATORS).filter { it.isNotEmpty() }
    private fun cap(w: String) = w.lowercase().replaceFirstChar { it.uppercase() }
    fun all(s: String): List<Pair<String, String>> {
        val w = words(s)
        return listOf(
            "Lowercase" to s.lowercase(),
            "Uppercase" to s.uppercase(),
            "Camelcase" to w.mapIndexed { i, x -> if (i == 0) x.lowercase() else cap(x) }.joinToString(""),
            "Capitalcase" to w.joinToString(" ") { cap(it) },
            "Constantcase" to w.joinToString("_") { it.uppercase() },
            "Dotcase" to w.joinToString(".") { it.lowercase() },
            "Headercase" to w.joinToString("-") { cap(it) },
            "Nocase" to w.joinToString(" ") { it.lowercase() },
            "Paramcase" to w.joinToString("-") { it.lowercase() },
            "Pascalcase" to w.joinToString("") { cap(it) },
            "Pathcase" to w.joinToString("/") { it.lowercase() },
            "Sentencecase" to w.mapIndexed { i, x -> if (i == 0) cap(x) else x.lowercase() }.joinToString(" "),
            "Snakecase" to w.joinToString("_") { it.lowercase() },
            "Mockingcase" to String(CharArray(s.length) { i -> if (i % 2 == 0) s[i].lowercaseChar() else s[i].uppercaseChar() }),
        )
    }
}

@Composable
fun CaseConverterTool() {
    var input by remember { mutableStateOf("lorem ipsum dolor sit amet") }
    ToolColumn {
        InputField(input, { input = it }, t("Your string", "Tu texto"))
        val cases = remember(input) { Case.all(input) }
        cases.forEach { (k, v) -> OutputField(v, k, singleLine = true) }
    }
}

/* ======================= NATO ======================= */

object Nato {
    private val words = listOf("Alfa", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliett", "Kilo", "Lima",
        "Mike", "November", "Oscar", "Papa", "Quebec", "Romeo", "Sierra", "Tango", "Uniform", "Victor", "Whiskey", "X-ray", "Yankee", "Zulu")
    private val digits = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")
    fun convert(s: String) = s.map { c ->
        val u = c.uppercaseChar()
        when { u in 'A'..'Z' -> words[u - 'A']; u in '0'..'9' -> digits[u - '0']; c == ' ' -> "(space)"; else -> c.toString() }
    }.joinToString(" ")
}

@Composable
fun NatoTool() {
    var input by remember { mutableStateOf("") }
    ToolColumn {
        InputField(input, { input = it }, t("Your text to convert to NATO phonetic alphabet", "Texto a convertir al alfabeto OTAN"))
        OutputField(Nato.convert(input), t("Your text in NATO phonetic alphabet", "Tu texto en alfabeto OTAN"), minLines = 3, mono = false)
    }
}

/* ======================= Text ↔ binary / unicode ======================= */

private val NON_BINARY = Regex("[^01]")
private val UNICODE_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
private val SPACE_BETWEEN_ENTITIES = Regex("(?<=;) (?=&#)")

@Composable
fun TextToBinaryTool() {
    var text by remember { mutableStateOf("") }
    var bin by remember { mutableStateOf("") }
    val toBin = remember(text) { text.map { it.code.toString(2).padStart(8, '0') }.joinToString(" ") }
    val toText = remember(bin) {
        runCatching {
            bin.replace(NON_BINARY, "").chunked(8).filter { it.length == 8 }.map { it.toInt(2).toChar() }.joinToString("")
        }
    }
    ToolColumn {
        Panel(t("Text to ASCII binary", "Texto a binario ASCII")) {
            InputField(text, { text = it }, t("Enter text to convert to binary", "Texto a convertir"), singleLine = false, minLines = 2)
            OutputField(toBin, t("Binary from your text", "Binario"), minLines = 2)
        }
        Panel(t("ASCII binary to text", "Binario ASCII a texto")) {
            InputField(bin, { bin = it }, t("Enter binary to convert to text", "Binario a convertir"), singleLine = false, minLines = 2, mono = true)
            OutputField(toText.getOrDefault(""), t("Text from your binary", "Texto"), minLines = 2, mono = false)
        }
    }
}

@Composable
fun TextToUnicodeTool() {
    var text by remember { mutableStateOf("") }
    var uni by remember { mutableStateOf("") }
    val toUni = remember(text) { text.codePoints().toArray().joinToString(" ") { "&#$it;" } }
    val toText = remember(uni) {
        UNICODE_ENTITY.replace(uni) { m ->
            val cp = m.groupValues[2].toIntOrNull(if (m.groupValues[1] == "x") 16 else 10) ?: return@replace m.value
            String(Character.toChars(cp))
        }.replace(SPACE_BETWEEN_ENTITIES, "")
    }
    ToolColumn {
        Panel(t("Text to Unicode", "Texto a Unicode")) {
            InputField(text, { text = it }, t("Enter text to convert to unicode", "Texto a convertir"), singleLine = false, minLines = 2)
            OutputField(toUni, t("Unicode from your text", "Unicode"), minLines = 2)
        }
        Panel(t("Unicode to text", "Unicode a texto")) {
            InputField(uni, { uni = it }, t("Enter unicode to convert to text", "Unicode a convertir"), singleLine = false, minLines = 2, mono = true)
            OutputField(toText, t("Text from your Unicode", "Texto"), minLines = 2, mono = false)
        }
    }
}

/* ======================= List converter ======================= */

@Composable
fun ListConverterTool() {
    var input by remember { mutableStateOf("1\n2\n3\n4\n4\n5") }
    var trim by remember { mutableStateOf(true) }
    var dedupe by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(false) }
    var keepLines by remember { mutableStateOf(false) }
    var reverse by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("none") }
    var sep by remember { mutableStateOf(", ") }
    var itemPre by remember { mutableStateOf("") }
    var itemSuf by remember { mutableStateOf("") }
    var listPre by remember { mutableStateOf("") }
    var listSuf by remember { mutableStateOf("") }
    val out = remember(input, trim, dedupe, lower, keepLines, reverse, sort, sep, itemPre, itemSuf, listPre, listSuf) {
        var l = input.split("\n").let { if (trim) it.map(String::trim) else it }.filter { it.isNotEmpty() }
        if (lower) l = l.map { it.lowercase() }
        if (dedupe) l = l.distinct()
        l = when (sort) { "asc" -> l.sorted(); "desc" -> l.sortedDescending(); else -> l }
        if (reverse) l = l.reversed()
        listPre + l.joinToString(sep + if (keepLines) "\n" else "") { itemPre + it + itemSuf } + listSuf
    }
    ToolColumn {
        Panel {
            SwitchRow(t("Trim list items", "Recortar espacios"), trim) { trim = it }
            SwitchRow(t("Remove duplicates", "Eliminar duplicados"), dedupe) { dedupe = it }
            SwitchRow(t("Convert to lowercase", "Convertir a minúsculas"), lower) { lower = it }
            SwitchRow(t("Keep line breaks", "Mantener saltos de línea"), keepLines) { keepLines = it }
            SwitchRow(t("Reverse list", "Invertir lista"), reverse) { reverse = it }
            ChipChoice(t("Sort list", "Ordenar"), listOf("none" to t("None", "No"), "asc" to t("Ascending", "Ascendente"), "desc" to t("Descending", "Descendente")), sort) { sort = it }
            InputField(sep, { sep = it }, t("Separator", "Separador"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(itemPre, { itemPre = it }, t("Item prefix", "Prefijo elemento"), Modifier.weight(1f))
                InputField(itemSuf, { itemSuf = it }, t("Item suffix", "Sufijo elemento"), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputField(listPre, { listPre = it }, t("List prefix", "Prefijo lista"), Modifier.weight(1f))
                InputField(listSuf, { listSuf = it }, t("List suffix", "Sufijo lista"), Modifier.weight(1f))
            }
        }
        InputField(input, { input = it }, t("Your input data", "Datos de entrada"), singleLine = false, minLines = 6)
        OutputField(out, t("Your transformed data", "Datos transformados"), minLines = 4)
    }
}

/* ======================= Markdown → HTML ======================= */

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownToHtmlTool() {
    val ctx = LocalContext.current
    var md by remember { mutableStateOf("# Hello world\n\nSome **bold** and _italic_ text.\n\n- item 1\n- item 2\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n```\ncode\n```") }
    val ext = remember { listOf(TablesExtension.create()) }
    val parser = remember { Parser.builder().extensions(ext).build() }
    val renderer = remember { HtmlRenderer.builder().extensions(ext).build() }
    val html = remember(md) { renderer.render(parser.parse(md)) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    val page = "<html><head><meta name='viewport' content='width=device-width'><style>body{font-family:sans-serif;padding:8px;" +
        (if (dark) "background:${cs.surface.css()};color:${cs.onSurface.css()};" else "") +
        "}table{border-collapse:collapse}td,th{border:1px solid #888;padding:4px 8px}pre{background:#8882;padding:8px;overflow:auto}</style></head><body>$html</body></html>"
    ToolColumn {
        InputField(md, { md = it }, t("Your Markdown content", "Tu contenido Markdown"), singleLine = false, minLines = 8, mono = true)
        OutputField(html, t("Output HTML", "HTML resultante"), minLines = 6)
        Text(t("Preview", "Vista previa"), style = MaterialTheme.typography.labelLarge)
        Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(8.dp)) {
            AndroidView(
                factory = { WebView(it).also { w -> web = w } },
                update = { it.loadDataWithBaseURL(null, page, "text/html", "utf-8", null) },
                modifier = Modifier.fillMaxWidth().height(320.dp)
            )
        }
        Button(onClick = {
            val w = web ?: return@Button
            val pm = ctx.getSystemService(PrintManager::class.java)
            pm.print("ITools-markdown", w.createPrintDocumentAdapter("markdown"), PrintAttributes.Builder().build())
        }) { Text(t("Print as PDF", "Imprimir como PDF")) }
    }
}
