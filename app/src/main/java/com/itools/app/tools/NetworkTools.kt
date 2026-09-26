package com.itools.app.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itools.app.ui.components.*
import com.itools.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

/* ======================= IPv4 utilities ======================= */

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

    /** Accepts "a.b.c.d/nn", "a.b.c.d nn.nn.nn.nn", "a.b.c.d/nn.nn.nn.nn" or "a.b.c.d" (/32). */
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

/* ======================= IPv6 utilities ======================= */

object Ipv6 {
    private val ALL_ONES: BigInteger = BigInteger.ONE.shiftLeft(128) - BigInteger.ONE
    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** Parses a textual IPv6 address ("::" compression and a trailing embedded IPv4 are supported). */
    fun parse(input: String): BigInteger? {
        var t = input.trim().removePrefix("[").removeSuffix("]")
        if (t.count { it == ':' } < 2) return null
        val lastColon = t.lastIndexOf(':')
        val tail = t.substring(lastColon + 1)
        if ('.' in tail) {
            val v4 = Ipv4.parse(tail) ?: return null
            t = t.substring(0, lastColon + 1) + "%x:%x".format(v4 shr 16, v4 and 0xffff)
        }
        val dbl = t.indexOf("::")
        if (dbl >= 0 && t.indexOf("::", dbl + 1) >= 0) return null
        fun groups(part: String) = if (part.isEmpty()) emptyList() else part.split(':')
        val head = groups(if (dbl >= 0) t.substring(0, dbl) else t)
        val rest = if (dbl >= 0) groups(t.substring(dbl + 2)) else emptyList()
        val n = head.size + rest.size
        if ((dbl < 0 && n != 8) || (dbl >= 0 && n > 7)) return null
        var v = BigInteger.ZERO
        for (g in head + List(8 - n) { "0" } + rest) {
            if (g.isEmpty() || g.length > 4 || !g.all(::isHex)) return null
            v = v.shiftLeft(16).or(BigInteger.valueOf(g.toLong(16)))
        }
        return v
    }

    private fun groups(v: BigInteger) = (7 downTo 0).map { v.shiftRight(it * 16).toInt() and 0xffff }

    /** Full form: 8 groups of 4 hex digits. */
    fun expanded(v: BigInteger) = groups(v).joinToString(":") { "%04x".format(it) }

    /** Canonical compressed form (RFC 5952): the longest run of two or more zero groups becomes "::". */
    fun compressed(v: BigInteger): String {
        val g = groups(v)
        var bestStart = -1; var bestLen = 0; var i = 0
        while (i < 8) {
            if (g[i] == 0) {
                var j = i; while (j < 8 && g[j] == 0) j++
                if (j - i > bestLen) { bestStart = i; bestLen = j - i }
                i = j
            } else i++
        }
        if (bestLen < 2) return g.joinToString(":") { it.toString(16) }
        val left = g.subList(0, bestStart).joinToString(":") { it.toString(16) }
        val right = g.subList(bestStart + bestLen, 8).joinToString(":") { it.toString(16) }
        return "$left::$right"
    }

    fun mask(bits: Int): BigInteger = ALL_ONES.shiftLeft(128 - bits).and(ALL_ONES)


    enum class Kind(val en: String, val es: String) {
        UNSPECIFIED("Unspecified", "No especificada"), LOOPBACK("Loopback", "Loopback"),
        IPV4_MAPPED("IPv4-mapped", "IPv4 mapeada"), NAT64("NAT64 (IPv4/IPv6 translation)", "NAT64 (traducción IPv4/IPv6)"),
        DOCUMENTATION("Documentation", "Documentación"), TEREDO("Teredo", "Teredo"), SIX_TO_FOUR("6to4", "6to4"),
        UNIQUE_LOCAL("Unique local (ULA)", "Local única (ULA)"), LINK_LOCAL("Link-local", "Enlace local"),
        MULTICAST("Multicast", "Multidifusión"), GLOBAL("Global unicast", "Unidifusión global"), RESERVED("Reserved", "Reservada"),
    }

    /** Special-purpose ranges, most specific first. */
    private val RANGES: List<Triple<Kind, BigInteger, Int>> by lazy {
        listOf(
            Triple(Kind.UNSPECIFIED, "::", 128), Triple(Kind.LOOPBACK, "::1", 128),
            Triple(Kind.IPV4_MAPPED, "::ffff:0:0", 96), Triple(Kind.NAT64, "64:ff9b::", 96),
            Triple(Kind.DOCUMENTATION, "2001:db8::", 32), Triple(Kind.TEREDO, "2001::", 32),
            Triple(Kind.SIX_TO_FOUR, "2002::", 16), Triple(Kind.UNIQUE_LOCAL, "fc00::", 7),
            Triple(Kind.LINK_LOCAL, "fe80::", 10), Triple(Kind.MULTICAST, "ff00::", 8),
            Triple(Kind.GLOBAL, "2000::", 3),
        ).map { (k, p, bits) -> Triple(k, parse(p)!!, bits) }
    }

    fun kind(v: BigInteger): Kind =
        RANGES.firstOrNull { (_, net, bits) -> v.and(mask(bits)) == net }?.first ?: Kind.RESERVED

    data class Subnet(val ip: BigInteger, val bits: Int) {
        val network: BigInteger get() = ip.and(mask(bits))
        val last: BigInteger get() = network.or(mask(bits).xor(ALL_ONES))
        val size: BigInteger get() = BigInteger.ONE.shiftLeft(128 - bits)
        /** Reverse DNS zone of the network (nibble boundary rounded up). */
        val reverseZone: String get() {
            val nibbles = expanded(network).replace(":", "").take((bits + 3) / 4)
            return nibbles.reversed().toList().joinToString(".") + (if (nibbles.isEmpty()) "" else ".") + "ip6.arpa"
        }
    }

    /** Accepts "address/prefix" or just "address" (then /64 is assumed). */
    fun parseSubnet(s: String): Subnet? {
        val t = s.trim()
        val ip = parse(t.substringBefore('/')) ?: return null
        if ('/' !in t) return Subnet(ip, 64)
        val bits = t.substringAfter('/').trim().toIntOrNull()?.takeIf { it in 0..128 } ?: return null
        return Subnet(ip, bits)
    }

    fun isMax(v: BigInteger) = v == ALL_ONES

    /** "2^n = value" for sizes too large to read comfortably. */
    fun power(exp: Int): String = BigInteger.ONE.shiftLeft(exp).let { if (exp > 16) "2^$exp = $it" else it.toString() }
}

/* ======================= IPv6 subnet calculator ======================= */

@Composable
fun Ipv6SubnetTool() {
    var input by remember { mutableStateOf("2001:db8:85a3::8a2e:370:7334/64") }
    val sub = remember(input) { Ipv6.parseSubnet(input) }
    val es = isSpanish()
    ToolColumn {
        InputField(input, { input = it }, t("An IPv6 address with or without prefix", "Dirección IPv6 con o sin prefijo"), mono = true,
            placeholder = "2001:db8::1/64",
            error = if (sub == null) t("We cannot parse this address, check the format", "No se puede interpretar la dirección, revisa el formato") else null)
        Hint(t("Without a prefix length, /64 is assumed.", "Sin longitud de prefijo se asume /64."))
        sub?.let { s ->
            val kind = Ipv6.kind(s.ip)
            KeyValueTable(buildList {
                add(t("Address", "Dirección") to Ipv6.compressed(s.ip))
                add(t("Expanded address", "Dirección expandida") to Ipv6.expanded(s.ip))
                add(t("Network", "Red") to "${Ipv6.compressed(s.network)}/${s.bits}")
                add(t("Prefix length", "Longitud del prefijo") to "/${s.bits}")
                add(t("Network mask", "Máscara de red") to Ipv6.compressed(Ipv6.mask(s.bits)))
                add(t("First address", "Primera dirección") to Ipv6.compressed(s.network))
                add(t("Last address", "Última dirección") to Ipv6.compressed(s.last))
                add(t("Total addresses", "Total de direcciones") to Ipv6.power(128 - s.bits))
                if (s.bits <= 64) add(t("/64 networks", "Redes /64") to Ipv6.power(64 - s.bits))
                add(t("Address type", "Tipo de dirección") to if (es) kind.es else kind.en)
                add(t("Reverse DNS zone", "Zona DNS inversa") to s.reverseZone)
            })
            ButtonRow {
                OutlinedButton(onClick = { input = "${Ipv6.compressed(s.network - s.size)}/${s.bits}" }, enabled = s.network.signum() > 0) {
                    Text(t("Previous block", "Bloque anterior"))
                }
                OutlinedButton(onClick = { input = "${Ipv6.compressed(s.last + BigInteger.ONE)}/${s.bits}" }, enabled = !Ipv6.isMax(s.last)) {
                    Text(t("Next block", "Bloque siguiente"))
                }
            }
            if (s.bits < 128) SubnetSplit(s)
        }
    }
}

/** Splits the network into smaller subnets and lists the first ones. */
@Composable
private fun SubnetSplit(s: Ipv6.Subnet) {
    val maxPrefix = minOf(128, s.bits + 16)
    var newPrefix by remember(s.bits) { mutableIntStateOf(minOf(maxOf(s.bits + 1, 64), maxPrefix)) }
    val shown = 16
    val subnets = remember(s.network, s.bits, newPrefix) {
        val step = BigInteger.ONE.shiftLeft(128 - newPrefix)
        val count = 1 shl (newPrefix - s.bits)
        List(minOf(count, shown)) { i -> "${Ipv6.compressed(s.network + step * BigInteger.valueOf(i.toLong()))}/$newPrefix" }
    }
    Panel(t("Split into subnets", "Dividir en subredes")) {
        // A slider needs at least two values (a /127 can only be split into /128)
        if (maxPrefix > s.bits + 1) SliderRow(t("New prefix length", "Nueva longitud de prefijo"), newPrefix, (s.bits + 1)..maxPrefix) { newPrefix = it }
        else Text(t("New prefix length", "Nueva longitud de prefijo") + ": $newPrefix", style = MaterialTheme.typography.labelLarge)
        Hint(t("Subnets", "Subredes") + ": " + Ipv6.power(newPrefix - s.bits) +
            if (newPrefix - s.bits > 4) " · " + t("showing the first $shown", "se muestran las $shown primeras") else "")
        val list = subnets.joinToString("\n")
        Row(verticalAlignment = Alignment.Top) {
            MonoBlock(list, Modifier.weight(1f))
            CopyButton(list)
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
