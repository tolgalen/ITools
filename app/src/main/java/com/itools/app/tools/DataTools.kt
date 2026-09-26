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
import java.math.BigInteger
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
    /** Longitud del IBAN por país (registro SWIFT). */
    val LENGTHS: Map<String, Int> = ("AD24 AE23 AL28 AT20 AZ28 BA20 BE16 BG22 BH22 BI27 BR29 BY28 CH21 CR22 CY28 CZ24 DE22 DJ27 DK18 DO28 EE20 EG29 " +
        "ES24 FI18 FK18 FO18 FR27 GB22 GE22 GI23 GL18 GR27 GT28 HR21 HU28 IE22 IL23 IQ23 IS26 IT27 JO30 KW30 KZ20 LB28 LC32 LI21 LT20 " +
        "LU20 LV21 LY25 MC27 MD24 ME22 MK19 MN20 MR27 MT31 MU30 NI28 NL18 NO15 OM23 PK24 PL28 PS29 PT25 QA29 RO24 RS22 RU33 SA24 SC31 " +
        "SD18 SE24 SI19 SK24 SM27 SO23 ST25 SV28 TL23 TN24 TR26 UA29 VA22 VG24 XK20 YE30 " +
        // territorios que usan el formato de su país
        "GF27 GP27 MQ27 RE27 PF27 TF27 YT27 NC27 BL27 MF27 PM27 WF27 AX18 GG22 IM22 JE22")
        .split(" ").associate { it.substring(0, 2) to it.substring(2).toInt() }

    enum class Error(val en: String, val es: String) {
        CHARS("Invalid characters", "Caracteres no válidos"),
        COUNTRY("Unknown country code", "Código de país desconocido"),
        LENGTH("Wrong length for this country", "Longitud incorrecta para este país"),
        CHECKSUM("Invalid checksum (mod 97)", "Dígitos de control incorrectos (mod 97)"),
    }

    fun clean(s: String) = s.replace(Regex("[\\s-]"), "").uppercase()

    fun mod97(iban: String): Int {
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        val digits = rearranged.map { if (it.isDigit()) it.toString() else (it - 'A' + 10).toString() }.joinToString("")
        return BigInteger(digits).mod(BigInteger.valueOf(97)).toInt()
    }

    fun validate(raw: String): List<Error> {
        val s = clean(raw)
        if (!Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$").matches(s)) return listOf(Error.CHARS)
        val errs = mutableListOf<Error>()
        val len = LENGTHS[s.substring(0, 2)]
        if (len == null) errs += Error.COUNTRY else if (s.length != len) errs += Error.LENGTH
        if (s.length < 5 || mod97(s) != 1) errs += Error.CHECKSUM
        return errs
    }

    /** QR-IBAN (Suiza / Liechtenstein): IID entre 30000 y 31999. */
    fun isQrIban(raw: String): Boolean {
        val s = clean(raw)
        if (s.length < 9 || s.substring(0, 2) !in setOf("CH", "LI")) return false
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
