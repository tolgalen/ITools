package com.itools.app.util

/** Analizador de user-agent basado en expresiones regulares (subconjunto de ua-parser-js). */
object UserAgentParser {
    data class Info(val browser: String, val browserVer: String, val engine: String, val engineVer: String,
                    val os: String, val osVer: String, val device: String, val deviceVendor: String, val deviceModel: String, val cpu: String)

    private fun find(ua: String, vararg rules: Pair<String, String>): Pair<String, String> {
        for ((name, re) in rules) Regex(re, RegexOption.IGNORE_CASE).find(ua)?.let { return name to (it.groupValues.getOrNull(1) ?: "") }
        return "" to ""
    }

    fun parse(ua: String): Info {
        val (b, bv) = find(ua,
            "Samsung Internet" to "SamsungBrowser/([\\d.]+)",
            "Opera" to "(?:OPR|Opera)/([\\d.]+)",
            "Edge" to "Edg(?:e|A|iOS)?/([\\d.]+)",
            "Yandex" to "YaBrowser/([\\d.]+)",
            "Vivaldi" to "Vivaldi/([\\d.]+)",
            "Brave" to "Brave/([\\d.]+)",
            "UCBrowser" to "UCBrowser/([\\d.]+)",
            "Firefox" to "(?:Firefox|FxiOS)/([\\d.]+)",
            "Chrome WebView" to "; wv\\).+Chrome/([\\d.]+)",
            "Chrome" to "(?:Chrome|CriOS)/([\\d.]+)",
            "IE" to "(?:MSIE |Trident/.+rv:)([\\d.]+)",
            "Mobile Safari" to "Version/([\\d.]+).+Mobile.+Safari",
            "Safari" to "Version/([\\d.]+).+Safari",
            "curl" to "curl/([\\d.]+)",
            "Wget" to "Wget/([\\d.]+)",
            "Googlebot" to "Googlebot/([\\d.]+)",
        )
        val (e, ev) = find(ua,
            "EdgeHTML" to "Edge/([\\d.]+)",
            "Blink" to "Chrome/([\\d.]+)",
            "Presto" to "Presto/([\\d.]+)",
            "Gecko" to "rv:([\\d.]+)\\) Gecko",
            "WebKit" to "AppleWebKit/([\\d.]+)",
            "Trident" to "Trident/([\\d.]+)",
        )
        val (o, ov) = find(ua,
            "Windows Phone" to "Windows Phone(?: OS)? ([\\d.]+)",
            "Windows" to "Windows NT ([\\d.]+)",
            "iOS" to "(?:iPhone|iPad|iPod).+OS ([\\d_]+)",
            "Mac OS" to "Mac OS X ([\\d_.]+)",
            "Android" to "Android ([\\d.]+)",
            "HarmonyOS" to "HarmonyOS ([\\d.]+)",
            "Chrome OS" to "CrOS \\S+ ([\\d.]+)",
            "Ubuntu" to "Ubuntu(?:/([\\d.]+))?",
            "Fedora" to "Fedora(?:/([\\d.]+))?",
            "Linux" to "(Linux)",
        )
        val osVer = if (o == "Linux") "" else ov.replace('_', '.')
        val device = when {
            Regex("iPad|Tablet|Tab\\b|SM-T|Nexus (7|9|10)", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "tablet"
            Regex("Mobile|iPhone|Android.+Mobile|Windows Phone", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "mobile"
            Regex("SmartTV|SMART-TV|AppleTV|GoogleTV|BRAVIA", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "smarttv"
            Regex("bot|crawler|spider", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "bot"
            else -> ""
        }
        val (vendor, model) = when {
            ua.contains("iPhone") -> "Apple" to "iPhone"
            ua.contains("iPad") -> "Apple" to "iPad"
            ua.contains("Macintosh") -> "Apple" to "Macintosh"
            else -> Regex("Android [\\d.]+; (?:[a-z]{2}[-_][a-z]{2}; )?([^;)]+?)(?: Build/|\\))", RegexOption.IGNORE_CASE).find(ua)?.groupValues?.get(1)?.trim()?.let { m ->
                val v = when {
                    m.startsWith("SM-") || m.startsWith("GT-") -> "Samsung"
                    m.startsWith("Pixel") || m.startsWith("Nexus") -> "Google"
                    m.contains("Redmi", true) || m.startsWith("Mi ") || m.startsWith("M2") -> "Xiaomi"
                    m.startsWith("HUAWEI", true) -> "Huawei"
                    m.startsWith("CPH") -> "OPPO"
                    m.startsWith("moto", true) -> "Motorola"
                    else -> ""
                }
                v to m
            } ?: ("" to "")
        }
        val cpu = when {
            Regex("x86_64|x64|Win64|WOW64|amd64", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "amd64"
            Regex("aarch64|arm64", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "arm64"
            Regex("armv7|arm", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "arm"
            Regex("i[3-6]86|x86", RegexOption.IGNORE_CASE).containsMatchIn(ua) -> "ia32"
            else -> ""
        }
        return Info(b, bv, e, ev, o, osVer, device, vendor, model, cpu)
    }
}
