package com.itools.app.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.bip39.Mnemonics
import com.itools.app.ui.components.*
import com.itools.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.crypto.engines.RC4Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.mindrot.jbcrypt.BCrypt
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/* ======================= Token generator ======================= */

@Composable
fun TokenGeneratorTool() {
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var numbers by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(false) }
    var length by remember { mutableIntStateOf(64) }
    var seed by remember { mutableIntStateOf(0) }
    val token = remember(upper, lower, numbers, symbols, length, seed) {
        val alphabet = buildString {
            if (upper) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (lower) append("abcdefghijklmnopqrstuvwxyz")
            if (numbers) append("0123456789")
            if (symbols) append(".,;:!?./-\"'#{([-|\\@)]=}*+")
        }
        if (alphabet.isEmpty()) "" else String(CharArray(length) { alphabet[secureRandom.nextInt(alphabet.length)] })
    }
    ToolColumn {
        Panel {
            SwitchRow(t("Uppercase (ABC...)", "Mayúsculas (ABC...)"), upper) { upper = it }
            SwitchRow(t("Lowercase (abc...)", "Minúsculas (abc...)"), lower) { lower = it }
            SwitchRow(t("Numbers (123...)", "Números (123...)"), numbers) { numbers = it }
            SwitchRow(t("Symbols (;-!...)", "Símbolos (;-!...)"), symbols) { symbols = it }
            SliderRow(t("Length", "Longitud"), length, 1..512) { length = it }
        }
        OutputField(token, t("Token", "Token"), minLines = 3)
        ButtonRow { Button(onClick = { seed++ }) { Text(t("Refresh", "Regenerar")) } }
    }
}

/* ======================= Password generator ======================= */

object PasswordGen {
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    const val SYMBOLS = "!#$%&*+-.:;=?@_~"

    /** Random password that contains at least one character of each selected set (when the length allows it). */
    fun generate(length: Int, sets: List<String>): String {
        if (length <= 0 || sets.isEmpty()) return ""
        val all = sets.joinToString("")
        val chars = CharArray(length) { all[secureRandom.nextInt(all.length)] }
        if (length >= sets.size) {
            // One character of each set, placed at distinct random positions
            val positions = MutableList(length) { it }.also { java.util.Collections.shuffle(it, secureRandom) }
            sets.forEachIndexed { i, set -> chars[positions[i]] = set[secureRandom.nextInt(set.length)] }
        }
        return String(chars)
    }

    fun pin(length: Int): String = String(CharArray(length) { DIGITS[secureRandom.nextInt(DIGITS.length)] })

    /** Entropy in bits of a string of [length] symbols drawn uniformly from an alphabet of [alphabetSize]. */
    fun entropy(length: Int, alphabetSize: Int): Double = if (alphabetSize <= 1) 0.0 else length * log2(alphabetSize.toDouble())

    enum class Strength(val en: String, val es: String, val color: Color) {
        VERY_WEAK("Very weak", "Muy débil", Color(0xFFD03050)),
        WEAK("Weak", "Débil", Color(0xFFD05A2A)),
        FAIR("Fair", "Aceptable", Color(0xFFC98300)),
        STRONG("Strong", "Segura", Color(0xFF5C9E2E)),
        VERY_STRONG("Very strong", "Muy segura", Color(0xFF18A058));

        companion object {
            fun of(bits: Double) = when {
                bits < 28 -> VERY_WEAK; bits < 36 -> WEAK; bits < 60 -> FAIR; bits < 80 -> STRONG; else -> VERY_STRONG
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasswordGeneratorTool() {
    var pinMode by remember { mutableStateOf(false) }
    var length by remember { mutableIntStateOf(16) }
    var pinLength by remember { mutableIntStateOf(6) }
    var upper by remember { mutableStateOf(true) }
    var lower by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(false) }
    var seed by remember { mutableIntStateOf(0) }
    val sets = listOfNotNull(
        PasswordGen.UPPER.takeIf { upper }, PasswordGen.LOWER.takeIf { lower },
        PasswordGen.DIGITS.takeIf { digits }, PasswordGen.SYMBOLS.takeIf { symbols },
    )
    val password = remember(pinMode, length, pinLength, sets, seed) {
        if (pinMode) PasswordGen.pin(pinLength) else PasswordGen.generate(length, sets)
    }
    val bits = if (pinMode) PasswordGen.entropy(pinLength, 10) else PasswordGen.entropy(length, sets.sumOf { it.length })
    val strength = PasswordGen.Strength.of(bits)
    val es = isSpanish()
    ToolColumn {
        ChipChoice(t("Type", "Tipo"), listOf(false to t("Random password", "Contraseña aleatoria"), true to "PIN"), pinMode) { pinMode = it }
        OutputField(password, if (pinMode) "PIN" else t("Password", "Contraseña"), singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = strength.color, shape = RoundedCornerShape(6.dp)) {
                Text(if (es) strength.es else strength.en, color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Text("%.0f bits".format(bits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { seed++ }) { Icon(Icons.Outlined.Refresh, t("Regenerate", "Regenerar")) }
        }
        Hint(t("Time to crack by brute force: ", "Tiempo para romperla por fuerza bruta: ") + humanDuration(2.0.pow(bits) / 1e9 / 2))
        if (pinMode) Hint(t("A PIN is only safe where the number of attempts is limited (phone lock screen, bank card...).",
            "Un PIN solo es seguro donde el número de intentos está limitado (bloqueo del móvil, tarjeta bancaria...)."))
        Panel {
            if (pinMode) LengthStepper(t("PIN length", "Longitud del PIN"), pinLength, 4..12) { pinLength = it }
            else {
                LengthStepper(t("Password length", "Longitud de la contraseña"), length, 4..64) { length = it }
                Text(t("Characters used", "Caracteres usados"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The last selected set cannot be unchecked, so the password is never empty
                    listOf(
                        Triple("ABC", upper) { v: Boolean -> upper = v }, Triple("abc", lower) { v: Boolean -> lower = v },
                        Triple("123", digits) { v: Boolean -> digits = v }, Triple("#$&", symbols) { v: Boolean -> symbols = v },
                    ).forEach { (label, checked, onChange) ->
                        CharsetCheck(label, checked, enabled = !(checked && sets.size == 1), onChange = onChange)
                    }
                }
            }
        }
        Hint(t("Passwords are generated on the device with a cryptographically secure random generator.",
            "Las contraseñas se generan en el dispositivo con un generador aleatorio criptográficamente seguro."))
    }
}

/** Slider with − / + buttons for fine adjustment. */
@Composable
private fun LengthStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Icon(Icons.Outlined.Remove, t("Decrease", "Reducir")) }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Icon(Icons.Outlined.Add, t("Increase", "Aumentar")) }
        }
    }
}

@Composable
private fun CharsetCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, fontFamily = Mono)
    }
}

/* ======================= Hash text ======================= */

@Composable
fun HashTextTool() {
    var text by remember { mutableStateOf("") }
    var enc by remember { mutableStateOf(OutEncoding.HEX) }
    ToolColumn {
        InputField(text, { text = it }, t("Your text to hash", "Texto a hashear"), singleLine = false, minLines = 3)
        Dropdown(t("Digest encoding", "Codificación del resultado"), OutEncoding.entries.map { it to it.label }, enc) { enc = it }
        val hashes = remember(text, enc) {
            val bytes = text.toByteArray()
            HashAlgo.entries.map { it.label to digest(it, bytes).encode(enc) }
        }
        hashes.forEach { (label, h) -> OutputField(h, label, singleLine = true) }
    }
}

/* ======================= Bcrypt ======================= */

@Composable
fun BcryptTool() {
    var input by remember { mutableStateOf("") }
    var rounds by remember { mutableIntStateOf(10) }
    var hash by remember { mutableStateOf("") }
    LaunchedEffect(input, rounds) {
        hash = withContext(Dispatchers.Default) { BCrypt.hashpw(input, BCrypt.gensalt(rounds)) }
    }
    var cmpText by remember { mutableStateOf("") }
    var cmpHash by remember { mutableStateOf("") }
    val matches = remember(cmpText, cmpHash) { runCatching { BCrypt.checkpw(cmpText, cmpHash) }.getOrDefault(false) }
    ToolColumn {
        Panel(t("Hash", "Generar hash")) {
            InputField(input, { input = it }, t("Your string", "Tu texto"))
            SliderRow(t("Salt rounds", "Rondas de sal"), rounds, 4..16) { rounds = it }
            OutputField(hash, "Bcrypt hash", singleLine = true)
        }
        Panel(t("Compare string with hash", "Comparar texto con hash")) {
            InputField(cmpText, { cmpText = it }, t("Your string", "Tu texto"))
            InputField(cmpHash, { cmpHash = it }, t("Your hash", "Tu hash"), mono = true)
            Text(
                t("Do they match? ", "¿Coinciden? ") + if (matches) t("Yes", "Sí") else "No",
                color = if (matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

/* ======================= UUID ======================= */

object Uuids {
    val NS_DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
    val NS_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8")
    val NS_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8")
    val NS_X500 = UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8")

    fun nameBased(ns: UUID, name: String, version: Int): UUID {
        val md = MessageDigest.getInstance(if (version == 3) "MD5" else "SHA-1")
        md.update(ByteBuffer.allocate(16).putLong(ns.mostSignificantBits).putLong(ns.leastSignificantBits).array())
        val h = md.digest(name.toByteArray()).copyOf(16)
        h[6] = ((h[6].toInt() and 0x0f) or (version shl 4)).toByte()
        h[8] = ((h[8].toInt() and 0x3f) or 0x80).toByte()
        val bb = ByteBuffer.wrap(h)
        return UUID(bb.long, bb.long)
    }

    private var clockSeq = secureRandom.nextInt(0x4000)
    private val node = randomBytes(6).also { it[0] = (it[0].toInt() or 1).toByte() }
    fun v1(): UUID {
        // timestamp in 100 ns intervals since 1582-10-15
        val ts = System.currentTimeMillis() * 10_000 + 0x01B21DD213814000L + (System.nanoTime() / 100 % 10_000)
        val timeLow = ts and 0xffffffffL
        val timeMid = (ts ushr 32) and 0xffff
        val timeHi = ((ts ushr 48) and 0x0fff) or 0x1000
        val msb = (timeLow shl 32) or (timeMid shl 16) or timeHi
        var lsb = ((clockSeq.toLong() or 0x8000L) shl 48)
        node.forEachIndexed { i, b -> lsb = lsb or ((b.toLong() and 0xff) shl (8 * (5 - i))) }
        return UUID(msb, lsb)
    }
}

@Composable
fun UuidGeneratorTool() {
    var version by remember { mutableStateOf("v4") }
    var qty by remember { mutableIntStateOf(1) }
    var ns by remember { mutableStateOf(Uuids.NS_URL.toString()) }
    var name by remember { mutableStateOf("") }
    var seed by remember { mutableIntStateOf(0) }
    val nsValid = runCatching { UUID.fromString(ns) }.isSuccess
    val result = remember(version, qty, ns, name, seed) {
        (1..qty).joinToString("\n") {
            when (version) {
                "NIL" -> "00000000-0000-0000-0000-000000000000"
                "v1" -> Uuids.v1().toString()
                "v3", "v5" -> if (nsValid) Uuids.nameBased(UUID.fromString(ns), name, version.last().digitToInt()).toString() else ""
                else -> UUID.randomUUID().toString()
            }
        }
    }
    ToolColumn {
        ChipChoice(t("UUID version", "Versión de UUID"), listOf("NIL", "v1", "v3", "v4", "v5").map { it to it }, version) { version = it }
        SliderRow(t("Quantity", "Cantidad"), qty, 1..50) { qty = it }
        if (version == "v3" || version == "v5") {
            ChipChoice(t("Namespace", "Espacio de nombres"),
                listOf(Uuids.NS_DNS to "DNS", Uuids.NS_URL to "URL", Uuids.NS_OID to "OID", Uuids.NS_X500 to "X500").map { it.first.toString() to it.second },
                ns) { ns = it }
            InputField(ns, { ns = it }, t("Namespace UUID", "UUID del espacio"), mono = true, error = if (nsValid) null else t("Invalid UUID", "UUID no válido"))
            InputField(name, { name = it }, t("Name", "Nombre"))
        }
        OutputField(result, "UUID", minLines = 2)
        ButtonRow { Button(onClick = { seed++ }) { Text(t("Refresh", "Regenerar")) } }
    }
}

/* ======================= ULID ======================= */

object Ulid {
    private const val C = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    fun generate(time: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        var t = time
        val ts = CharArray(10); for (i in 9 downTo 0) { ts[i] = C[(t % 32).toInt()]; t /= 32 }
        sb.append(ts)
        repeat(16) { sb.append(C[secureRandom.nextInt(32)]) }
        return sb.toString()
    }
}

@Composable
fun UlidGeneratorTool() {
    var qty by remember { mutableIntStateOf(1) }
    var json by remember { mutableStateOf(false) }
    var seed by remember { mutableIntStateOf(0) }
    val ids = remember(qty, seed) { List(qty) { Ulid.generate() } }
    val out = if (json) ids.joinToString(",\n  ", "[\n  ", "\n]") { "\"$it\"" } else ids.joinToString("\n")
    ToolColumn {
        SliderRow(t("Quantity", "Cantidad"), qty, 1..100) { qty = it }
        ChipChoice(t("Format", "Formato"), listOf(false to "Raw", true to "JSON"), json) { json = it }
        OutputField(out, "ULID", minLines = 2)
        ButtonRow { Button(onClick = { seed++ }) { Text(t("Refresh", "Regenerar")) } }
    }
}

/* ======================= Encrypt / decrypt (CryptoJS compatible) ======================= */

object CryptoJs {
    enum class Algo(val label: String, val keyLen: Int, val ivLen: Int) { AES("AES", 32, 16), TDES("TripleDES", 24, 8), RC4("RC4", 32, 0) }

    /** EVP_BytesToKey with MD5, like OpenSSL/CryptoJS. */
    private fun evp(pass: ByteArray, salt: ByteArray, keyLen: Int, ivLen: Int): Pair<ByteArray, ByteArray> {
        val out = java.io.ByteArrayOutputStream()
        var prev = ByteArray(0)
        val md = MessageDigest.getInstance("MD5")
        while (out.size() < keyLen + ivLen) {
            md.reset(); md.update(prev); md.update(pass); md.update(salt)
            prev = md.digest(); out.write(prev)
        }
        val all = out.toByteArray()
        return all.copyOfRange(0, keyLen) to all.copyOfRange(keyLen, keyLen + ivLen)
    }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val e = RC4Engine(); e.init(true, KeyParameter(key))
        return ByteArray(data.size).also { e.processBytes(data, 0, data.size, it, 0) }
    }

    private fun block(a: Algo, mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val (name, tr) = if (a == Algo.AES) "AES" to "AES/CBC/PKCS5Padding" else "DESede" to "DESede/CBC/PKCS5Padding"
        val c = Cipher.getInstance(tr)
        c.init(mode, SecretKeySpec(key, name), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    fun encrypt(a: Algo, text: String, pass: String): String {
        val salt = randomBytes(8)
        val (k, iv) = evp(pass.toByteArray(), salt, a.keyLen, a.ivLen)
        val ct = if (a == Algo.RC4) rc4(k, text.toByteArray()) else block(a, Cipher.ENCRYPT_MODE, k, iv, text.toByteArray())
        return ("Salted__".toByteArray() + salt + ct).toB64()
    }

    fun decrypt(a: Algo, b64: String, pass: String): String {
        val raw = decodeB64(b64)
        require(raw.size > 16 && String(raw.copyOfRange(0, 8)) == "Salted__") { "Not a salted ciphertext" }
        val salt = raw.copyOfRange(8, 16)
        val ct = raw.copyOfRange(16, raw.size)
        val (k, iv) = evp(pass.toByteArray(), salt, a.keyLen, a.ivLen)
        val pt = if (a == Algo.RC4) rc4(k, ct) else block(a, Cipher.DECRYPT_MODE, k, iv, ct)
        return String(pt, Charsets.UTF_8)
    }
}

@Composable
fun EncryptionTool() {
    var encText by remember { mutableStateOf("Lorem ipsum dolor sit amet") }
    var encKey by remember { mutableStateOf("my secret key") }
    var encAlgo by remember { mutableStateOf(CryptoJs.Algo.AES) }
    var decText by remember { mutableStateOf("") }
    var decKey by remember { mutableStateOf("my secret key") }
    var decAlgo by remember { mutableStateOf(CryptoJs.Algo.AES) }
    val encrypted = remember(encText, encKey, encAlgo) { runCatching { CryptoJs.encrypt(encAlgo, encText, encKey) }.getOrElse { "" } }
    LaunchedEffect(Unit) { if (decText.isEmpty()) decText = encrypted }
    val decrypted = remember(decText, decKey, decAlgo) { runCatching { CryptoJs.decrypt(decAlgo, decText, decKey) } }
    val algos = CryptoJs.Algo.entries.map { it to it.label }
    ToolColumn {
        Panel(t("Encrypt", "Cifrar")) {
            InputField(encText, { encText = it }, t("Your text", "Tu texto"), singleLine = false, minLines = 3)
            InputField(encKey, { encKey = it }, t("Your secret key", "Tu clave secreta"))
            ChipChoice(t("Encryption algorithm", "Algoritmo"), algos, encAlgo) { encAlgo = it }
            OutputField(encrypted, t("Your text encrypted", "Texto cifrado"), minLines = 2)
        }
        Panel(t("Decrypt", "Descifrar")) {
            InputField(decText, { decText = it }, t("Your encrypted text", "Tu texto cifrado"), singleLine = false, minLines = 3, mono = true)
            InputField(decKey, { decKey = it }, t("Your secret key", "Tu clave secreta"))
            ChipChoice(t("Encryption algorithm", "Algoritmo"), algos, decAlgo) { decAlgo = it }
            if (decrypted.isFailure) ErrorText(t("Unable to decrypt your text", "No se pudo descifrar el texto"))
            OutputField(decrypted.getOrDefault(""), t("Your decrypted text", "Texto descifrado"), minLines = 2, mono = false)
        }
        Hint(t("Output uses the OpenSSL/CryptoJS \"Salted__\" format, so it is interchangeable with it-tools.",
            "La salida usa el formato \"Salted__\" de OpenSSL/CryptoJS, compatible con it-tools."))
    }
}

/* ======================= BIP39 ======================= */

@Composable
fun Bip39Tool() {
    var entropy by remember { mutableStateOf(randomBytes(16).toHex()) }
    var phrase by remember { mutableStateOf("") }
    var entropyError by remember { mutableStateOf<String?>(null) }
    var phraseError by remember { mutableStateOf<String?>(null) }
    val badEntropy = t("Entropy must be 16, 20, 24, 28 or 32 bytes in hex", "La entropía debe ser de 16, 20, 24, 28 o 32 bytes en hex")
    val badPhrase = t("Invalid mnemonic", "Frase mnemónica no válida")

    fun fromEntropy(e: String) {
        entropy = e
        runCatching {
            val b = e.hexToBytes(); require(b.size in listOf(16, 20, 24, 28, 32))
            String(Mnemonics.MnemonicCode(b).chars)
        }.onSuccess { phrase = it; entropyError = null; phraseError = null }.onFailure { entropyError = badEntropy }
    }
    fun fromPhrase(p: String) {
        phrase = p
        runCatching {
            val code = Mnemonics.MnemonicCode(p.trim().lowercase().replace(Regex("\\s+"), " "))
            code.validate(); code.toEntropy().toHex()
        }.onSuccess { entropy = it; phraseError = null; entropyError = null }.onFailure { phraseError = badPhrase }
    }
    LaunchedEffect(Unit) { fromEntropy(entropy) }
    ToolColumn {
        Hint(t("Language: English (BIP39 standard wordlist)", "Idioma: inglés (lista de palabras estándar BIP39)"))
        InputField(entropy, ::fromEntropy, t("Entropy (seed)", "Entropía (semilla)"), mono = true, error = entropyError,
            trailing = { IconButton(onClick = { fromEntropy(randomBytes(16).toHex()) }) { Icon(Icons.Outlined.Refresh, null) } })
        ButtonRow {
            listOf(12 to 16, 15 to 20, 18 to 24, 21 to 28, 24 to 32).forEach { (w, b) ->
                OutlinedButton(onClick = { fromEntropy(randomBytes(b).toHex()) }) { Text("$w " + t("words", "palabras")) }
            }
        }
        InputField(phrase, ::fromPhrase, t("Passphrase (mnemonic)", "Frase (mnemónico)"), singleLine = false, minLines = 3, error = phraseError)
        OutputField(phrase, t("Copy passphrase", "Copiar frase"), mono = false)
    }
}

/* ======================= HMAC ======================= */

@Composable
fun HmacTool() {
    var text by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var algo by remember { mutableStateOf(HashAlgo.SHA256) }
    var enc by remember { mutableStateOf(OutEncoding.HEX) }
    val out = remember(text, key, algo, enc) { hmac(algo, key.toByteArray(), text.toByteArray()).encode(enc) }
    ToolColumn {
        InputField(text, { text = it }, t("Plain text to compute the hash", "Texto plano"), singleLine = false, minLines = 3)
        InputField(key, { key = it }, t("Secret key", "Clave secreta"))
        Dropdown(t("Hashing function", "Función hash"), HashAlgo.entries.map { it to it.label }, algo) { algo = it }
        Dropdown(t("Output encoding", "Codificación"), OutEncoding.entries.map { it to it.label }, enc) { enc = it }
        OutputField(out, "HMAC", minLines = 2)
    }
}

/* ======================= RSA ======================= */

private fun pem(type: String, der: ByteArray) =
    "-----BEGIN $type-----\n" + der.toB64().chunked(64).joinToString("\n") + "\n-----END $type-----"

@Composable
fun RsaTool() {
    var bits by remember { mutableIntStateOf(2048) }
    var seed by remember { mutableIntStateOf(0) }
    var pub by remember { mutableStateOf("") }
    var priv by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(bits, seed) {
        busy = true
        val kp = withContext(Dispatchers.Default) {
            KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
        }
        pub = pem("PUBLIC KEY", kp.public.encoded)
        // PKCS#1 ("RSA PRIVATE KEY"), same as node-forge in it-tools
        val pkcs1 = PrivateKeyInfo.getInstance(kp.private.encoded).parsePrivateKey().toASN1Primitive().encoded
        priv = pem("RSA PRIVATE KEY", pkcs1)
        busy = false
    }
    ToolColumn {
        ChipChoice(t("Bits", "Bits"), listOf(512, 1024, 2048, 3072, 4096).map { it to it.toString() }, bits) { bits = it }
        ButtonRow { Button(onClick = { seed++ }, enabled = !busy) { Text(t("Refresh key-pair", "Regenerar claves")) } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutputField(pub, t("Public key", "Clave pública"), minLines = 4)
        OutputField(priv, t("Private key", "Clave privada"), minLines = 6)
    }
}

/* ======================= Password strength ======================= */

object PasswordStrength {
    data class Report(val crackTimeSeconds: Double, val length: Int, val entropy: Double, val charset: Int, val score: Double)
    fun analyse(p: String): Report {
        var cs = 0
        if (p.any { it.isLowerCase() }) cs += 26
        if (p.any { it.isUpperCase() }) cs += 26
        if (p.any { it.isDigit() }) cs += 10
        if (p.any { !it.isLetterOrDigit() }) cs += 32
        val entropy = if (cs == 0) 0.0 else p.length * log2(cs.toDouble())
        val guessesPerSecond = 1e9
        val seconds = 2.0.pow(entropy) / guessesPerSecond / 2
        return Report(seconds, p.length, entropy, cs, (entropy / 128).coerceIn(0.0, 1.0))
    }
}

@Composable
fun humanDuration(seconds: Double): String {
    if (seconds < 1) return t("Instantly", "Al instante")
    val units = listOf(
        t("millennia", "milenios") to 3.1536e10, t("centuries", "siglos") to 3.1536e9, t("years", "años") to 3.1536e7,
        t("months", "meses") to 2.592e6, t("days", "días") to 86400.0, t("hours", "horas") to 3600.0,
        t("minutes", "minutos") to 60.0, t("seconds", "segundos") to 1.0,
    )
    if (seconds > 3.1536e10 * 1e6) return t("Forever", "Una eternidad")
    var rest = seconds
    val parts = mutableListOf<String>()
    for ((name, s) in units) {
        val n = (rest / s).toLong()
        if (n > 0) { parts += "$n $name"; rest -= n * s }
        if (parts.size == 2) break
    }
    return parts.joinToString(", ")
}

@Composable
fun PasswordStrengthTool() {
    var pwd by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val r = remember(pwd) { PasswordStrength.analyse(pwd) }
    ToolColumn {
        InputField(pwd, { pwd = it }, t("Enter a password...", "Introduce una contraseña..."), password = !show)
        SwitchRow(t("Show password", "Mostrar contraseña"), show) { show = it }
        Panel {
            Text(t("Duration to crack this password with brute force", "Tiempo para romperla por fuerza bruta"),
                style = MaterialTheme.typography.bodySmall)
            Text(humanDuration(r.crackTimeSeconds), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { r.score.toFloat() }, modifier = Modifier.fillMaxWidth(),
                color = when { r.score < 0.3 -> Color(0xFFD03050); r.score < 0.6 -> Color(0xFFF0A020); else -> MaterialTheme.colorScheme.primary }
            )
        }
        KeyValueTable(listOf(
            t("Password length", "Longitud") to r.length.toString(),
            t("Entropy", "Entropía") to "%.2f".format(r.entropy),
            t("Character set size", "Tamaño del alfabeto") to r.charset.toString(),
            t("Score", "Puntuación") to "${(r.score * 100).toInt()} / 100",
        ), copy = false)
        Hint(t("Note: the computed strength is based on the time it would take to crack the password using a brute force approach, it does not take into account the possibility of a dictionary attack.",
            "Nota: la robustez se calcula según el tiempo de un ataque por fuerza bruta; no tiene en cuenta ataques de diccionario."))
    }
}

/* ======================= PDF signature checker ======================= */

object PdfSignatures {
    data class Signer(val subject: String, val issuer: String, val serial: String, val from: Date, val to: Date, val valid: Boolean, val signingTime: String?)
    data class Sig(val index: Int, val coversWholeFile: Boolean, val signers: List<Signer>, val error: String? = null)

    /** Signatures are verified with the platform JCA provider (the BC provider would need all of BouncyCastle kept from R8). */
    fun check(pdf: ByteArray): List<Sig> {
        val latin = String(pdf, Charsets.ISO_8859_1)
        val re = Regex("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*]")
        return re.findAll(latin).mapIndexed { i, m ->
            runCatching {
                val (a, b, c, d) = m.destructured.toList().map { it.toInt() }
                val signed = pdf.copyOfRange(a, a + b) + pdf.copyOfRange(c, c + d)
                val hex = latin.substring(a + b, c).trim().removePrefix("<").removeSuffix(">").let { if (it.length % 2 == 1) it + "0" else it }
                val cms = CMSSignedData(CMSProcessableByteArray(signed), hex.hexToBytes())
                val certs = cms.certificates
                val signers = cms.signerInfos.signers.map { s ->
                    @Suppress("UNCHECKED_CAST")
                    val holder = certs.getMatches(s.sid as org.bouncycastle.util.Selector<X509CertificateHolder>).firstOrNull() as X509CertificateHolder?
                    val ok = holder != null && runCatching { s.verify(JcaSimpleSignerInfoVerifierBuilder().build(holder)) }.getOrDefault(false)
                    val st = s.signedAttributes?.get(org.bouncycastle.asn1.cms.CMSAttributes.signingTime)?.attrValues?.getObjectAt(0)
                        ?.let { runCatching { org.bouncycastle.asn1.cms.Time.getInstance(it).date.toString() }.getOrNull() }
                    Signer(
                        holder?.subject?.toString() ?: "?", holder?.issuer?.toString() ?: "?",
                        holder?.serialNumber?.toString(16) ?: "?", holder?.notBefore ?: Date(0), holder?.notAfter ?: Date(0), ok, st
                    )
                }
                Sig(i + 1, c + d == pdf.size, signers)
            }.getOrElse { Sig(i + 1, false, emptyList(), it.message ?: it.javaClass.simpleName) }
        }.toList()
    }
}

@Composable
fun PdfSignatureTool() {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf<List<PdfSignatures.Sig>?>(null) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pick = it }
    LaunchedEffect(pick) {
        val uri = pick ?: return@LaunchedEffect
        busy = true
        name = uri.lastPathSegment ?: ""
        result = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { PdfSignatures.check(it) }
        }
        busy = false
    }
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    ToolColumn {
        Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) { Text(t("Pick a PDF file", "Elegir un PDF")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (name.isNotEmpty()) Hint(name)
        result?.let { list ->
            if (list.isEmpty()) ErrorText(t("No signatures found in the provided file.", "No se encontraron firmas en el archivo."))
            list.forEach { sig ->
                Panel(t("Signature", "Firma") + " ${sig.index}") {
                    sig.error?.let { ErrorText(it) }
                    KeyValue(t("Covers whole file", "Cubre todo el archivo"), if (sig.coversWholeFile) t("Yes", "Sí") else t("No (modified after signing)", "No (modificado tras firmar)"), false)
                    sig.signers.forEach { s ->
                        HorizontalDivider()
                        KeyValue(t("Valid", "Válida"), if (s.valid) "✔" else "✘", false)
                        KeyValue(t("Subject", "Sujeto"), s.subject)
                        KeyValue(t("Issuer", "Emisor"), s.issuer)
                        KeyValue(t("Serial", "Nº de serie"), s.serial)
                        KeyValue(t("Valid from", "Válido desde"), df.format(s.from), false)
                        KeyValue(t("Valid to", "Válido hasta"), df.format(s.to), false)
                        s.signingTime?.let { KeyValue(t("Signing time", "Fecha de firma"), it, false) }
                    }
                }
            }
        }
    }
}
