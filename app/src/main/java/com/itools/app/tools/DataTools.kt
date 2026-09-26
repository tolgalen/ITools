package com.itools.app.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.itools.app.ui.components.*
import com.itools.app.util.secureRandom
import java.util.Locale

/* ======================= Phone parser ======================= */

@Composable
fun PhoneParserTool() {
    val util = remember { PhoneNumberUtil.getInstance() }
    val locale = Locale.getDefault()
    val regions = remember(locale) {
        util.supportedRegions.map { it to "${Locale("", it).getDisplayCountry(locale)} (+${util.getCountryCodeForRegion(it)})" }.sortedBy { it.second }
    }
    var region by remember { mutableStateOf(locale.country.takeIf { it in util.supportedRegions } ?: "ES") }
    var input by remember { mutableStateOf("") }
    val parsed = remember(input, region) { if (input.isBlank()) null else runCatching { util.parse(input, region) } }
    val yes = t("Yes", "Sí"); val no = t("No", "No")
    val typeNames = mapOf(
        PhoneNumberType.FIXED_LINE to t("Fixed line", "Fijo"), PhoneNumberType.MOBILE to t("Mobile", "Móvil"),
        PhoneNumberType.FIXED_LINE_OR_MOBILE to t("Fixed line or mobile", "Fijo o móvil"), PhoneNumberType.TOLL_FREE to t("Toll free", "Gratuito"),
        PhoneNumberType.PREMIUM_RATE to t("Premium rate", "Tarificación adicional"), PhoneNumberType.SHARED_COST to t("Shared cost", "Coste compartido"),
        PhoneNumberType.VOIP to "VoIP", PhoneNumberType.PERSONAL_NUMBER to t("Personal number", "Número personal"),
        PhoneNumberType.PAGER to t("Pager", "Buscapersonas"), PhoneNumberType.UAN to "UAN", PhoneNumberType.VOICEMAIL to t("Voicemail", "Buzón de voz"),
        PhoneNumberType.UNKNOWN to t("Unknown", "Desconocido"),
    )
    ToolColumn {
        Dropdown(t("Default country code", "País por defecto"), regions, region) { region = it }
        InputField(input, { input = it }, t("Phone number", "Número de teléfono"), keyboard = KeyboardType.Phone,
            placeholder = t("Enter a phone number", "Introduce un número de teléfono"),
            error = if (parsed?.isFailure == true) t("Invalid phone number", "Número de teléfono no válido") else null)
        parsed?.getOrNull()?.let { n ->
            val rc = util.getRegionCodeForNumber(n)
            KeyValueTable(listOf(
                t("Country", "País") to (rc ?: ""),
                t("Country name", "Nombre del país") to (rc?.let { Locale("", it).getDisplayCountry(locale) } ?: ""),
                t("Country calling code", "Prefijo internacional") to "+${n.countryCode}",
                t("Is valid?", "¿Es válido?") to if (util.isValidNumber(n)) yes else no,
                t("Is possible?", "¿Es posible?") to if (util.isPossibleNumber(n)) yes else no,
                t("Type", "Tipo") to (typeNames[util.getNumberType(n)] ?: util.getNumberType(n).name),
                t("International format", "Formato internacional") to util.format(n, PhoneNumberFormat.INTERNATIONAL),
                t("National format", "Formato nacional") to util.format(n, PhoneNumberFormat.NATIONAL),
                t("E.164 format", "Formato E.164") to util.format(n, PhoneNumberFormat.E164),
                t("RFC3966 format", "Formato RFC3966") to util.format(n, PhoneNumberFormat.RFC3966),
            ))
        }
    }
}

/* ======================= IBAN ======================= */

object Iban {
    /** IBAN length per country (SWIFT registry). */
    val LENGTHS: Map<String, Int> = ("AD24 AE23 AL28 AT20 AZ28 BA20 BE16 BG22 BH22 BI27 BR29 BY28 CH21 CR22 CY28 CZ24 DE22 DJ27 DK18 DO28 EE20 EG29 " +
        "ES24 FI18 FK18 FO18 FR27 GB22 GE22 GI23 GL18 GR27 GT28 HR21 HU28 IE22 IL23 IQ23 IS26 IT27 JO30 KW30 KZ20 LB28 LC32 LI21 LT20 " +
        "LU20 LV21 LY25 MC27 MD24 ME22 MK19 MN20 MR27 MT31 MU30 NI28 NL18 NO15 OM23 PK24 PL28 PS29 PT25 QA29 RO24 RS22 RU33 SA24 SC31 " +
        "SD18 SE24 SI19 SK24 SM27 SO23 ST25 SV28 TL23 TN24 TR26 UA29 VA22 VG24 XK20 YE30 " +
        // territories that use their country's format
        "GF27 GP27 MQ27 RE27 PF27 TF27 YT27 NC27 BL27 MF27 PM27 WF27 AX18 GG22 IM22 JE22")
        .split(" ").associate { it.substring(0, 2) to it.substring(2).toInt() }

    enum class Error(val en: String, val es: String) {
        CHARS("Invalid characters", "Caracteres no válidos"),
        COUNTRY("Unknown country code", "Código de país desconocido"),
        LENGTH("Wrong length for this country", "Longitud incorrecta para este país"),
        CHECKSUM("Invalid checksum (mod 97)", "Dígitos de control incorrectos (mod 97)"),
    }

    private val SEPARATORS = Regex("[\\s-]")
    private val FORMAT = Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$")
    private val QR_COUNTRIES = setOf("CH", "LI")

    fun clean(s: String) = s.replace(SEPARATORS, "").uppercase()

    /** Mod 97 remainder computed digit by digit (avoids building a BigInteger). */
    fun mod97(iban: String): Int {
        var r = 0
        for (c in iban.substring(4) + iban.substring(0, 4)) {
            r = if (c.isDigit()) (r * 10 + (c - '0')) % 97 else (r * 100 + (c - 'A' + 10)) % 97
        }
        return r
    }

    fun validate(raw: String): List<Error> {
        val s = clean(raw)
        if (!FORMAT.matches(s)) return listOf(Error.CHARS)
        val errs = mutableListOf<Error>()
        val len = LENGTHS[s.substring(0, 2)]
        if (len == null) errs += Error.COUNTRY else if (s.length != len) errs += Error.LENGTH
        if (s.length < 5 || mod97(s) != 1) errs += Error.CHECKSUM
        return errs
    }

    /** QR-IBAN (Switzerland / Liechtenstein): IID between 30000 and 31999. */
    fun isQrIban(raw: String): Boolean {
        val s = clean(raw)
        if (s.length < 9 || s.substring(0, 2) !in QR_COUNTRIES) return false
        val iid = s.substring(4, 9).toIntOrNull() ?: return false
        return iid in 30000..31999
    }

    fun friendly(raw: String) = clean(raw).chunked(4).joinToString(" ")
}

@Composable
fun IbanTool() {
    var input by remember { mutableStateOf("") }
    val es = isSpanish()
    val s = Iban.clean(input)
    val errs = remember(s) { if (s.isEmpty()) emptyList() else Iban.validate(s) }
    val yes = t("Yes", "Sí"); val no = t("No", "No")
    ToolColumn {
        InputField(input, { input = it }, "IBAN", mono = true, placeholder = t("Enter an IBAN to check for validity...", "Introduce un IBAN para comprobarlo..."))
        if (s.isNotEmpty()) {
            val cc = s.take(2)
            KeyValueTable(listOf(
                t("Is IBAN valid ?", "¿IBAN válido?") to if (errs.isEmpty()) yes else no,
                t("IBAN errors", "Errores") to errs.joinToString(", ") { if (es) it.es else it.en },
                t("Is IBAN a QR-IBAN ?", "¿Es QR-IBAN?") to if (Iban.isQrIban(s)) yes else no,
                t("Country code", "Código de país") to cc + (if (cc.length == 2 && cc.all { it.isLetter() }) " · " + Locale("", cc).getDisplayCountry(Locale.getDefault()) else ""),
                t("Check digits", "Dígitos de control") to s.drop(2).take(2),
                "BBAN" to s.drop(4),
                t("IBAN friendly format", "Formato legible") to Iban.friendly(s),
            ))
        }
        Panel(t("Valid IBAN examples", "Ejemplos de IBAN válidos")) {
            listOf("FR7630006000011234567890189", "DE89370400440532013000", "ES9121000418450200051332", "CH4431999123000889012").forEach {
                KeyValue("", Iban.friendly(it))
            }
        }
    }
}

/* ======================= Spanish DNI / NIE ======================= */

object Dni {
    private const val LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"
    private val SEPARATORS = Regex("[\\s.-]")
    private val DNI = Regex("^(\\d{1,8})([A-Z]?)$")
    private val NIE = Regex("^([XYZ])(\\d{1,7})([A-Z]?)$")

    enum class Kind { DNI, NIE }

    /** [letter] is the letter entered (null if missing); [expected] is the correct control letter. */
    data class Info(val kind: Kind, val number: String, val letter: Char?, val expected: Char) {
        val valid get() = letter == expected
        val normalized get() = "$number$expected"
    }

    /** Control letter: remainder of the number divided by 23. */
    fun letterFor(n: Long): Char = LETTERS[(n % 23).toInt()]

    fun parse(raw: String): Info? {
        val s = raw.replace(SEPARATORS, "").uppercase()
        DNI.matchEntire(s)?.let { m ->
            val number = m.groupValues[1].padStart(8, '0')
            return Info(Kind.DNI, number, m.groupValues[2].firstOrNull(), letterFor(number.toLong()))
        }
        NIE.matchEntire(s)?.let { m ->
            // For a NIE the initial X, Y or Z counts as 0, 1 or 2
            val prefix = m.groupValues[1][0]; val digits = m.groupValues[2].padStart(7, '0')
            return Info(Kind.NIE, "$prefix$digits", m.groupValues[3].firstOrNull(), letterFor("${"XYZ".indexOf(prefix)}$digits".toLong()))
        }
        return null
    }

    fun random(): String = (secureRandom.nextInt(100_000_000)).toLong().let { n -> "%08d".format(n) + letterFor(n) }
}

@Composable
fun DniTool() {
    var input by remember { mutableStateOf("12345678Z") }
    val info = remember(input) { Dni.parse(input) }
    val yes = t("Yes", "Sí"); val no = t("No", "No")
    ToolColumn {
        InputField(input, { input = it }, "DNI / NIE", mono = true, placeholder = "12345678Z",
            error = if (input.isNotBlank() && info == null)
                t("Invalid format: 8 digits and a letter (DNI) or X/Y/Z, 7 digits and a letter (NIE)",
                    "Formato no válido: 8 dígitos y una letra (DNI) o X/Y/Z, 7 dígitos y una letra (NIE)") else null)
        info?.let { d ->
            Text(
                when {
                    d.letter == null -> t("Control letter: ", "Letra de control: ") + d.expected
                    d.valid -> t("Valid ", "") + d.kind.name + t("", " válido")
                    else -> t("Wrong control letter: it should be ", "Letra de control incorrecta: debería ser ") + d.expected
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (d.letter != null && !d.valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            KeyValueTable(listOf(
                t("Is valid?", "¿Es válido?") to if (d.valid) yes else no,
                t("Type", "Tipo") to if (d.kind == Dni.Kind.DNI) t("DNI (Spanish citizens)", "DNI (ciudadanos españoles)")
                    else t("NIE (foreign residents)", "NIE (extranjeros residentes)"),
                t("Number", "Número") to d.number,
                t("Letter entered", "Letra introducida") to (d.letter?.toString() ?: ""),
                t("Correct control letter", "Letra de control correcta") to d.expected.toString(),
                t("Normalized", "Normalizado") to d.normalized,
            ))
        }
        ButtonRow { OutlinedButton(onClick = { input = Dni.random() }) { Text(t("Random valid DNI (for testing)", "DNI válido aleatorio (para pruebas)")) } }
        Hint(t("The control letter is the remainder of dividing the number by 23, looked up in TRWAGMYFPDXBNJZSQVHLCKE. For a NIE the initial X, Y or Z is replaced by 0, 1 or 2. You can omit the letter to calculate it.",
            "La letra de control es el resto de dividir el número entre 23, buscado en TRWAGMYFPDXBNJZSQVHLCKE. En un NIE la X, Y o Z inicial se sustituye por 0, 1 o 2. Puedes omitir la letra para calcularla."))
        Panel(t("Valid examples", "Ejemplos válidos")) {
            listOf("12345678Z", "00000000T", "X1234567L", "Y1234567X").forEach { KeyValue("", it) }
        }
    }
}
