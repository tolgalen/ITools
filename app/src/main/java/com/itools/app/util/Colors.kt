package com.itools.app.util

import kotlin.math.*

/** RGBA color in [0,1]. Conversion between hex, rgb, hsl, hwb, cmyk, lch and CSS names. */
data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
    val r8 get() = (r * 255).roundToInt().coerceIn(0, 255)
    val g8 get() = (g * 255).roundToInt().coerceIn(0, 255)
    val b8 get() = (b * 255).roundToInt().coerceIn(0, 255)
    val argb: Int get() = ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (r8 shl 16) or (g8 shl 8) or b8
}

object ColorConv {
    private val NAMES_RAW = "aliceblue f0f8ff antiquewhite faebd7 aqua 00ffff aquamarine 7fffd4 azure f0ffff beige f5f5dc bisque ffe4c4 " +
        "black 000000 blanchedalmond ffebcd blue 0000ff blueviolet 8a2be2 brown a52a2a burlywood deb887 cadetblue 5f9ea0 " +
        "chartreuse 7fff00 chocolate d2691e coral ff7f50 cornflowerblue 6495ed cornsilk fff8dc crimson dc143c cyan 00ffff " +
        "darkblue 00008b darkcyan 008b8b darkgoldenrod b8860b darkgray a9a9a9 darkgreen 006400 darkgrey a9a9a9 darkkhaki bdb76b " +
        "darkmagenta 8b008b darkolivegreen 556b2f darkorange ff8c00 darkorchid 9932cc darkred 8b0000 darksalmon e9967a " +
        "darkseagreen 8fbc8f darkslateblue 483d8b darkslategray 2f4f4f darkslategrey 2f4f4f darkturquoise 00ced1 darkviolet 9400d3 " +
        "deeppink ff1493 deepskyblue 00bfff dimgray 696969 dimgrey 696969 dodgerblue 1e90ff firebrick b22222 floralwhite fffaf0 " +
        "forestgreen 228b22 fuchsia ff00ff gainsboro dcdcdc ghostwhite f8f8ff gold ffd700 goldenrod daa520 gray 808080 green 008000 " +
        "greenyellow adff2f grey 808080 honeydew f0fff0 hotpink ff69b4 indianred cd5c5c indigo 4b0082 ivory fffff0 khaki f0e68c " +
        "lavender e6e6fa lavenderblush fff0f5 lawngreen 7cfc00 lemonchiffon fffacd lightblue add8e6 lightcoral f08080 lightcyan e0ffff " +
        "lightgoldenrodyellow fafad2 lightgray d3d3d3 lightgreen 90ee90 lightgrey d3d3d3 lightpink ffb6c1 lightsalmon ffa07a " +
        "lightseagreen 20b2aa lightskyblue 87cefa lightslategray 778899 lightslategrey 778899 lightsteelblue b0c4de lightyellow ffffe0 " +
        "lime 00ff00 limegreen 32cd32 linen faf0e6 magenta ff00ff maroon 800000 mediumaquamarine 66cdaa mediumblue 0000cd " +
        "mediumorchid ba55d3 mediumpurple 9370db mediumseagreen 3cb371 mediumslateblue 7b68ee mediumspringgreen 00fa9a " +
        "mediumturquoise 48d1cc mediumvioletred c71585 midnightblue 191970 mintcream f5fffa mistyrose ffe4e1 moccasin ffe4b5 " +
        "navajowhite ffdead navy 000080 oldlace fdf5e6 olive 808000 olivedrab 6b8e23 orange ffa500 orangered ff4500 orchid da70d6 " +
        "palegoldenrod eee8aa palegreen 98fb98 paleturquoise afeeee palevioletred db7093 papayawhip ffefd5 peachpuff ffdab9 " +
        "peru cd853f pink ffc0cb plum dda0dd powderblue b0e0e6 purple 800080 rebeccapurple 663399 red ff0000 rosybrown bc8f8f " +
        "royalblue 4169e1 saddlebrown 8b4513 salmon fa8072 sandybrown f4a460 seagreen 2e8b57 seashell fff5ee sienna a0522d " +
        "silver c0c0c0 skyblue 87ceeb slateblue 6a5acd slategray 708090 slategrey 708090 snow fffafa springgreen 00ff7f " +
        "steelblue 4682b4 tan d2b48c teal 008080 thistle d8bfd8 tomato ff6347 turquoise 40e0d0 violet ee82ee wheat f5deb3 " +
        "white ffffff whitesmoke f5f5f5 yellow ffff00 yellowgreen 9acd32"
    val names: Map<String, String> = NAMES_RAW.split(" ").chunked(2).associate { it[0] to it[1] }
    /** hex → first CSS name (same order as [names]). */
    private val namesByHex: Map<String, String> = LinkedHashMap<String, String>().apply { names.forEach { (n, h) -> putIfAbsent(h, n) } }
    private val HEX_RE = Regex("[0-9a-f]{3,8}")
    private val ARGS_SPLIT = Regex("[\\s,/]+")

    private fun num(s: String, scale: Double): Double {
        val t = s.trim()
        return if (t.endsWith("%")) t.dropLast(1).toDouble() / 100.0 else t.toDouble() / scale
    }
    private fun hue(s: String): Double {
        val t = s.trim().lowercase()
        return when {
            t.endsWith("deg") -> t.dropLast(3).toDouble()
            t.endsWith("turn") -> t.dropLast(4).toDouble() * 360
            t.endsWith("rad") -> Math.toDegrees(t.dropLast(3).toDouble())
            else -> t.toDouble()
        }.mod(360.0)
    }
    private fun args(s: String): List<String> =
        s.substringAfter('(').substringBeforeLast(')').split(ARGS_SPLIT).filter { it.isNotBlank() }

    fun parse(input: String): Rgba? = runCatching {
        val s = input.trim().lowercase()
        names[s]?.let { return@runCatching parse("#$it") }
        when {
            s.startsWith("#") || HEX_RE.matches(s) -> {
                var h = s.removePrefix("#")
                if (h.length == 3 || h.length == 4) h = h.map { "$it$it" }.joinToString("")
                require(h.length == 6 || h.length == 8)
                val v = h.chunked(2).map { it.toInt(16) / 255.0 }
                Rgba(v[0], v[1], v[2], v.getOrElse(3) { 1.0 })
            }
            s.startsWith("rgb") -> args(s).let { Rgba(num(it[0], 255.0), num(it[1], 255.0), num(it[2], 255.0), it.getOrNull(3)?.let { a -> num(a, 1.0) } ?: 1.0) }
            s.startsWith("hsl") -> args(s).let { hslToRgb(hue(it[0]), num(it[1], 100.0), num(it[2], 100.0), it.getOrNull(3)?.let { a -> num(a, 1.0) } ?: 1.0) }
            s.startsWith("hwb") -> args(s).let { hwbToRgb(hue(it[0]), num(it[1], 100.0), num(it[2], 100.0), it.getOrNull(3)?.let { a -> num(a, 1.0) } ?: 1.0) }
            s.startsWith("cmyk") || s.startsWith("device-cmyk") -> args(s).let {
                val (c, m, y, k) = it.take(4).map { x -> num(x, 100.0) }
                Rgba((1 - c) * (1 - k), (1 - m) * (1 - k), (1 - y) * (1 - k), it.getOrNull(4)?.let { a -> num(a, 1.0) } ?: 1.0)
            }
            s.startsWith("lch") -> args(s).let { lchToRgb(num(it[0], 1.0).let { l -> if (it[0].endsWith("%")) l * 100 else l }, it[1].toDouble(), hue(it[2]), it.getOrNull(3)?.let { a -> num(a, 1.0) } ?: 1.0) }
            else -> null
        }
    }.getOrNull()?.takeIf { listOf(it.r, it.g, it.b, it.a).all { v -> v in -0.001..1.001 } }

    fun hslToRgb(h: Double, s: Double, l: Double, a: Double = 1.0): Rgba {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0.0); h < 120 -> Triple(x, c, 0.0); h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c); h < 300 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        return Rgba(r + m, g + m, b + m, a)
    }
    fun hwbToRgb(h: Double, w: Double, bl: Double, a: Double = 1.0): Rgba {
        if (w + bl >= 1) { val g = w / (w + bl); return Rgba(g, g, g, a) }
        val base = hslToRgb(h, 1.0, 0.5)
        fun f(v: Double) = v * (1 - w - bl) + w
        return Rgba(f(base.r), f(base.g), f(base.b), a)
    }
    fun toHsl(c: Rgba): Triple<Double, Double, Double> {
        val max = maxOf(c.r, c.g, c.b); val min = minOf(c.r, c.g, c.b); val d = max - min
        val l = (max + min) / 2
        val s = if (d == 0.0) 0.0 else d / (1 - abs(2 * l - 1))
        val h = when {
            d == 0.0 -> 0.0
            max == c.r -> 60 * (((c.g - c.b) / d).mod(6.0))
            max == c.g -> 60 * ((c.b - c.r) / d + 2)
            else -> 60 * ((c.r - c.g) / d + 4)
        }
        return Triple(h, s, l)
    }

    // --- CIE LCH (D50, as in CSS Color 4) ---
    private fun lin(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun gam(v: Double) = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    private const val XN = 0.96422; private const val YN = 1.0; private const val ZN = 0.82521
    fun toLch(c: Rgba): Triple<Double, Double, Double> {
        val r = lin(c.r); val g = lin(c.g); val b = lin(c.b)
        // sRGB → XYZ D65 → D50 (Bradford)
        val x65 = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y65 = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z65 = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        val x = 1.0478112 * x65 + 0.0228866 * y65 - 0.0501270 * z65
        val y = 0.0295424 * x65 + 0.9904844 * y65 - 0.0170491 * z65
        val z = -0.0092345 * x65 + 0.0150436 * y65 + 0.7521316 * z65
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        val fx = f(x / XN); val fy = f(y / YN); val fz = f(z / ZN)
        val l = 116 * fy - 16; val aa = 500 * (fx - fy); val bb = 200 * (fy - fz)
        val ch = sqrt(aa * aa + bb * bb)
        val h = Math.toDegrees(atan2(bb, aa)).mod(360.0)
        return Triple(l, ch, h)
    }
    fun lchToRgb(l: Double, ch: Double, h: Double, a: Double = 1.0): Rgba {
        val aa = ch * cos(Math.toRadians(h)); val bb = ch * sin(Math.toRadians(h))
        val fy = (l + 16) / 116; val fx = fy + aa / 500; val fz = fy - bb / 200
        fun fi(t: Double) = if (t.pow(3) > 216.0 / 24389) t.pow(3) else (116 * t - 16) / (24389.0 / 27)
        val x = fi(fx) * XN; val y = (if (l > 8) fy.pow(3) else l / (24389.0 / 27)) * YN; val z = fi(fz) * ZN
        val x65 = 0.9555766 * x - 0.0230393 * y + 0.0631636 * z
        val y65 = -0.0282895 * x + 1.0099416 * y + 0.0210077 * z
        val z65 = 0.0122982 * x - 0.0204830 * y + 1.3299098 * z
        val r = 3.2404542 * x65 - 1.5371385 * y65 - 0.4985314 * z65
        val g = -0.9692660 * x65 + 1.8760108 * y65 + 0.0415560 * z65
        val b = 0.0556434 * x65 - 0.2040259 * y65 + 1.0572252 * z65
        return Rgba(gam(r).coerceIn(0.0, 1.0), gam(g).coerceIn(0.0, 1.0), gam(b).coerceIn(0.0, 1.0), a)
    }

    private fun f(v: Double, d: Int = 0) = if (d == 0) v.roundToInt().toString() else "%.${d}f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    private fun alpha(c: Rgba) = if (c.a < 1) " / ${f(c.a, 2)}" else ""

    fun formats(c: Rgba): List<Pair<String, String>> {
        val (h, s, l) = toHsl(c)
        val rgbHex = "%02x%02x%02x".format(c.r8, c.g8, c.b8)
        val hex = "#$rgbHex" + if (c.a < 1) "%02x".format((c.a * 255).roundToInt()) else ""
        val w = minOf(c.r, c.g, c.b); val bl = 1 - maxOf(c.r, c.g, c.b)
        val k = 1 - maxOf(c.r, c.g, c.b)
        val (cc, mm, yy) = if (k >= 1) Triple(0.0, 0.0, 0.0) else Triple((1 - c.r - k) / (1 - k), (1 - c.g - k) / (1 - k), (1 - c.b - k) / (1 - k))
        val (ll, lc, lh) = toLch(c)
        val name = (if (c.a >= 1) namesByHex[rgbHex] else null) ?: "—"
        return listOf(
            "name" to name,
            "hex" to hex,
            "rgb" to "rgb(${c.r8} ${c.g8} ${c.b8}${alpha(c)})",
            "hsl" to "hsl(${f(h)} ${f(s * 100)}% ${f(l * 100)}%${alpha(c)})",
            "hwb" to "hwb(${f(h)} ${f(w * 100)}% ${f(bl * 100)}%${alpha(c)})",
            "lch" to "lch(${f(ll, 2)}% ${f(lc, 2)} ${f(lh, 2)}${alpha(c)})",
            "cmyk" to "device-cmyk(${f(cc * 100)}% ${f(mm * 100)}% ${f(yy * 100)}% ${f(k * 100)}%${alpha(c)})",
        )
    }
}
