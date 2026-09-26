package com.itools.app.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itools.app.ui.components.*
import com.itools.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/* ======================= Utilidades IPv4 ======================= */

object Ipv4 {
    fun parse(s: String): Long? {
        val p = s.trim().split(".")
        if (p.size != 4) return null
        var v = 0L
        for (x in p) {
            if (x.isEmpty() || x.length > 3 || !x.all { it.isDigit() }) return null
            val n = x.toInt(); if (n > 255) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }
    fun str(v: Long) = (3 downTo 0).joinToString(".") { ((v shr (it * 8)) and 0xff).toString() }
    fun bin(v: Long) = (3 downTo 0).joinToString(".") { ((v shr (it * 8)) and 0xff).toString(2).padStart(8, '0') }
    fun mask(bits: Int): Long = if (bits == 0) 0 else (0xffffffffL shl (32 - bits)) and 0xffffffffL
    fun maskToBits(m: Long): Int? {
        val bits = java.lang.Long.bitCount(m)
        return if (mask(bits) == m) bits else null
    }
    fun ipClass(v: Long): String {
        val first = (v shr 24).toInt()
        return when {
            first < 128 -> "A"; first < 192 -> "B"; first < 224 -> "C"; first < 240 -> "D"; else -> "E"
        }
    }

    data class Subnet(val ip: Long, val bits: Int) {
        val maskV get() = mask(bits)
        val network get() = ip and maskV
        val broadcast get() = network or (maskV.inv() and 0xffffffffL)
        val size get() = 1L shl (32 - bits)
    }

    /** Acepta "a.b.c.d/nn", "a.b.c.d nn.nn.nn.nn", "a.b.c.d/nn.nn.nn.nn" o "a.b.c.d" (/32). */
    fun parseSubnet(s: String): Subnet? {
        val parts = s.trim().split(Regex("[/\\s]+"))
        val ip = parse(parts[0]) ?: return null
        if (parts.size == 1) return Subnet(ip, 32)
        if (parts.size != 2) return null
        val bits = parts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: parse(parts[1])?.let { maskToBits(it) } ?: return null
        return Subnet(ip, bits)
    }
}

/* ======================= IPv4 subnet calculator ======================= */

@Composable
fun Ipv4SubnetTool() {
    var input by remember { mutableStateOf("192.168.0.1/24") }
    val sub = remember(input) { Ipv4.parseSubnet(input) }
    ToolColumn {
        InputField(input, { input = it }, t("An IPv4 address with or without mask", "Dirección IPv4 con o sin máscara"), mono = true,
            placeholder = "192.168.0.1/24",
            error = if (sub == null) t("We cannot parse this address, check the format", "No se puede interpretar la dirección, revisa el formato") else null)
        sub?.let { s ->
            val usable = when (s.bits) { 32 -> 1L; 31 -> 2L; else -> s.size - 2 }
            val first = if (s.bits >= 31) s.network else s.network + 1
            val last = if (s.bits >= 31) s.broadcast else s.broadcast - 1
            KeyValueTable(listOf(
                t("Netmask", "Máscara") to "${Ipv4.str(s.network)}/${s.bits}",
                t("Network base IP", "IP base de la red") to Ipv4.str(s.network),
                t("Network mask", "Máscara de red") to Ipv4.str(s.maskV),
                t("Network mask in binary", "Máscara en binario") to Ipv4.bin(s.maskV),
                t("CIDR notation", "Notación CIDR") to "/${s.bits}",
                t("Wildcard mask", "Máscara wildcard") to Ipv4.str(s.maskV.inv() and 0xffffffffL),
                t("Network size", "Tamaño de la red") to s.size.toString(),
                t("Usable hosts", "Hosts utilizables") to usable.toString(),
                t("First address", "Primera dirección") to Ipv4.str(first),
                t("Last address", "Última dirección") to Ipv4.str(last),
                t("Broadcast address", "Dirección de broadcast") to if (s.bits >= 31) t("No broadcast address with this mask", "Sin broadcast con esta máscara") else Ipv4.str(s.broadcast),
                t("IP class", "Clase de IP") to Ipv4.ipClass(s.ip),
            ))
            ButtonRow {
                OutlinedButton(onClick = { input = "${Ipv4.str((s.network - s.size) and 0xffffffffL)}/${s.bits}" }, enabled = s.network >= s.size) {
                    Text(t("Previous block", "Bloque anterior"))
                }
                OutlinedButton(onClick = { input = "${Ipv4.str(s.broadcast + 1)}/${s.bits}" }, enabled = s.broadcast < 0xffffffffL) {
                    Text(t("Next block", "Bloque siguiente"))
                }
            }
        }
    }
}

/* ======================= IPv4 address converter ======================= */

@Composable
fun Ipv4ConverterTool() {
    var input by remember { mutableStateOf("192.168.1.1") }
    val v = remember(input) { Ipv4.parse(input) }
    ToolColumn {
        InputField(input, { input = it }, t("The ipv4 address", "Dirección IPv4"), mono = true,
            error = if (v == null && input.isNotBlank()) t("Invalid ipv4 address", "Dirección IPv4 no válida") else null)
        val rows = if (v == null) listOf("Decimal", "Hexadecimal", t("Binary", "Binario"), "IPv6", t("IPv6 (short)", "IPv6 (corta)")).map { it to "" }
        else {
            val hex = v.toString(16).padStart(8, '0')
            listOf(
                "Decimal" to v.toString(),
                "Hexadecimal" to hex.uppercase(),
                t("Binary", "Binario") to v.toString(2).padStart(32, '0'),
                "IPv6" to "0000:0000:0000:0000:0000:ffff:${hex.substring(0, 4)}:${hex.substring(4)}",
                t("IPv6 (short)", "IPv6 (corta)") to "::ffff:${hex.substring(0, 4).trimStart('0').ifEmpty { "0" }}:${hex.substring(4).trimStart('0').ifEmpty { "0" }}",
            )
        }
        KeyValueTable(rows)
    }
}

/* ======================= IPv4 range expander ======================= */

@Composable
fun Ipv4RangeTool() {
    var start by remember { mutableStateOf("192.168.1.1") }
    var end by remember { mutableStateOf("192.168.6.255") }
    val a = Ipv4.parse(start); val b = Ipv4.parse(end)
    ToolColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InputField(start, { start = it }, t("Start address", "Dirección inicial"), Modifier.weight(1f), mono = true,
                error = if (a == null) t("Invalid", "No válida") else null)
            InputField(end, { end = it }, t("End address", "Dirección final"), Modifier.weight(1f), mono = true,
                error = if (b == null) t("Invalid", "No válida") else null)
        }
        if (a != null && b != null) {
            if (a > b) {
                ErrorText(t("The end IPv4 address is lower than the start IPv4 address.", "La dirección final es menor que la inicial."))
                OutlinedButton(onClick = { val s = start; start = end; end = s }) { Text(t("Switch start and end", "Intercambiar inicio y fin")) }
            } else {
                val diff = a xor b
                val bits = 32 - (64 - java.lang.Long.numberOfLeadingZeros(diff))
                val m = Ipv4.mask(bits)
                val ns = a and m; val ne = ns or (m.inv() and 0xffffffffL)
                Panel {
                    Row(Modifier.fillMaxWidth()) {
                        Text("", Modifier.weight(1.2f)); Text(t("old value", "valor original"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text(t("new value", "valor nuevo"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    }
                    listOf(
                        Triple(t("Start address", "Dirección inicial"), Ipv4.str(a), Ipv4.str(ns)),
                        Triple(t("End address", "Dirección final"), Ipv4.str(b), Ipv4.str(ne)),
                        Triple(t("Addresses in range", "Direcciones en el rango"), (b - a + 1).toString(), (ne - ns + 1).toString()),
                        Triple("CIDR", "", "${Ipv4.str(ns)}/$bits"),
                    ).forEach { (l, o, n) ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth()) {
                            Text(l, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                            Text(o, Modifier.weight(1f), style = MonoStyle)
                            Text(n, Modifier.weight(1f), style = MonoStyle)
                        }
                    }
                }
                OutputField("${Ipv4.str(ns)}/$bits", "CIDR", singleLine = true)
            }
        }
    }
}

/* ======================= MAC lookup ======================= */

private val MAC_RE = Regex("^([0-9A-Fa-f]{2}[:\\-. ]?){2}[0-9A-Fa-f]{2}([:\\-. ]?[0-9A-Fa-f]{2}){0,3}$")

@Composable
fun MacLookupTool() {
    var mac by remember { mutableStateOf("20:37:06:12:34:56") }
    var vendor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val valid = MAC_RE.matches(mac.trim())
    val unknown = t("Unknown vendor for this address", "Fabricante desconocido para esta dirección")
    val netErr = t("Network error: ", "Error de red: ")
    LaunchedEffect(mac) {
        vendor = null
        if (!valid) return@LaunchedEffect
        delay(500) // debounce
        loading = true
        vendor = withContext(Dispatchers.IO) {
            runCatching {
                val clean = mac.trim().replace(Regex("[^0-9A-Fa-f]"), "").take(6)
                val c = URL("https://api.macvendors.com/$clean").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "ITools-Android")
                try {
                    if (c.responseCode == 200) c.inputStream.bufferedReader().readText().trim() else unknown
                } finally { c.disconnect() }
            }.getOrElse { netErr + (it.message ?: it.javaClass.simpleName) }
        }
        loading = false
    }
    ToolColumn {
        InputField(mac, { mac = it }, t("MAC address", "Dirección MAC"), mono = true, placeholder = "20:37:06:12:34:56",
            error = if (!valid && mac.isNotBlank()) t("Invalid MAC address", "Dirección MAC no válida") else null)
        Panel(t("Vendor info", "Fabricante")) {
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                vendor != null -> Row { Text(vendor!!, Modifier.weight(1f)); CopyButton(vendor!!) }
                else -> Hint("—")
            }
        }
        Hint(t("Lookup performed online via api.macvendors.com (only the OUI prefix is sent).",
            "Consulta en línea mediante api.macvendors.com (solo se envía el prefijo OUI)."))
    }
}

/* ======================= MAC generator ======================= */

@Composable
fun MacGeneratorTool() {
    var qty by remember { mutableIntStateOf(1) }
    var prefix by remember { mutableStateOf("64:16:7F") }
    var upper by remember { mutableStateOf(true) }
    var sep by remember { mutableStateOf(":") }
    var seed by remember { mutableIntStateOf(0) }
    val prefixHex = prefix.replace(Regex("[^0-9A-Fa-f]"), "")
    val prefixOk = prefixHex.length <= 12 && prefixHex.length % 2 == 0 && prefix.replace(Regex("[0-9A-Fa-f:\\-. ]"), "").isEmpty()
    val out = remember(qty, prefixHex, upper, sep, seed, prefixOk) {
        if (!prefixOk) "" else (1..qty).joinToString("\n") {
            val hex = prefixHex + randomBytes(6).toHex().substring(prefixHex.length)
            val h = if (upper) hex.uppercase() else hex.lowercase()
            if (sep == ".") h.chunked(4).joinToString(".") else h.chunked(2).joinToString(sep)
        }
    }
    ToolColumn {
        Panel {
            SliderRow(t("Quantity", "Cantidad"), qty, 1..100) { qty = it }
            InputField(prefix, { prefix = it }, t("MAC address prefix", "Prefijo MAC"), mono = true, placeholder = "64:16:7F",
                error = if (!prefixOk) t("Invalid partial MAC address", "Prefijo MAC no válido") else null)
            ChipChoice(t("Case", "Formato"), listOf(true to t("Uppercase", "Mayúsculas"), false to t("Lowercase", "Minúsculas")), upper) { upper = it }
            ChipChoice(t("Separator", "Separador"), listOf(":" to ":", "-" to "-", "." to ".", "" to t("None", "Ninguno")), sep) { sep = it }
        }
        OutputField(out, t("MAC addresses", "Direcciones MAC"), minLines = 3)
        ButtonRow { Button(onClick = { seed++ }) { Text(t("Refresh", "Regenerar")) } }
    }
}

/* ======================= IPv6 ULA ======================= */

@Composable
fun Ipv6UlaTool() {
    var mac by remember { mutableStateOf("20:37:06:12:34:56") }
    var seed by remember { mutableIntStateOf(0) }
    val valid = MAC_RE.matches(mac.trim())
    val ula = remember(mac, seed, valid) {
        if (!valid) null else {
            val ts = System.currentTimeMillis().toString()
            val sha = digest(HashAlgo.SHA1, (ts + mac.trim()).toByteArray()).toHex()
            val h = sha.substring(30)
            "fd${h.substring(0, 2)}:${h.substring(2, 6)}:${h.substring(6)}"
        }
    }
    ToolColumn {
        Hint(t("This tool uses the first method suggested by IETF using the current timestamp plus the mac address, sha1 hashed, and the lower 40 bits to generate your random ULA.",
            "Usa el primer método sugerido por el IETF: marca de tiempo actual más la MAC, con hash SHA1, y los 40 bits inferiores para generar tu ULA aleatoria."))
        InputField(mac, { mac = it }, t("MAC address", "Dirección MAC"), mono = true,
            error = if (!valid) t("Invalid MAC address", "Dirección MAC no válida") else null)
        ula?.let {
            KeyValueTable(listOf(
                "IPv6 ULA" to "$it::/48",
                t("First routable block", "Primer bloque enrutable") to "$it:0::/64",
                t("Last routable block", "Último bloque enrutable") to "$it:ffff::/64",
            ))
            ButtonRow { Button(onClick = { seed++ }) { Text(t("Regenerate", "Regenerar")) } }
        }
    }
}
