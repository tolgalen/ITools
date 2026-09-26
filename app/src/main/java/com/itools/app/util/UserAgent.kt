package com.itools.app.util

/** Regex-based user-agent parser (a subset of ua-parser-js). */
object UserAgentParser {
    data class Info(val browser: String, val browserVer: String, val engine: String, val engineVer: String,
                    val os: String, val osVer: String, val device: String, val deviceVendor: String, val deviceModel: String, val cpu: String)

    private fun re(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private fun rules(vararg r: Pair<String, String>) = r.map { (name, p) -> name to re(p) }

    private fun find(ua: String, rules: List<Pair<String, Regex>>): Pair<String, String> {
        for ((name, re) in rules) re.find(ua)?.let { return name to (it.groupValues.getOrNull(1) ?: "") }
        return "" to ""
    }

    // Rules compiled only once
    private val BROWSERS = rules(
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
    private val ENGINES = rules(
        "EdgeHTML" to "Edge/([\\d.]+)",
        "Blink" to "Chrome/([\\d.]+)",
        "Presto" to "Presto/([\\d.]+)",
        "Gecko" to "rv:([\\d.]+)\\) Gecko",
        "WebKit" to "AppleWebKit/([\\d.]+)",
        "Trident" to "Trident/([\\d.]+)",
    )
    private val OSES = rules(
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
    private val TABLET = re("iPad|Tablet|Tab\\b|SM-T|Nexus (7|9|10)")
    private val MOBILE = re("Mobile|iPhone|Android.+Mobile|Windows Phone")
    private val SMART_TV = re("SmartTV|SMART-TV|AppleTV|GoogleTV|BRAVIA")
    private val BOT = re("bot|crawler|spider")
    private val ANDROID_MODEL = re("Android [\\d.]+; (?:[a-z]{2}[-_][a-z]{2}; )?([^;)]+?)(?: Build/|\\))")
    private val AMD64 = re("x86_64|x64|Win64|WOW64|amd64")
    private val ARM64 = re("aarch64|arm64")
    private val ARM = re("armv7|arm")
    private val IA32 = re("i[3-6]86|x86")

    fun parse(ua: String): Info {
        val (b, bv) = find(ua, BROWSERS)
        val (e, ev) = find(ua, ENGINES)
        val (o, ov) = find(ua, OSES)
        val osVer = if (o == "Linux") "" else ov.replace('_', '.')
        val device = when {
            TABLET.containsMatchIn(ua) -> "tablet"
            MOBILE.containsMatchIn(ua) -> "mobile"
            SMART_TV.containsMatchIn(ua) -> "smarttv"
            BOT.containsMatchIn(ua) -> "bot"
            else -> ""
        }
        val (vendor, model) = when {
            ua.contains("iPhone") -> "Apple" to "iPhone"
            ua.contains("iPad") -> "Apple" to "iPad"
            ua.contains("Macintosh") -> "Apple" to "Macintosh"
            else -> ANDROID_MODEL.find(ua)?.groupValues?.get(1)?.trim()?.let { m ->
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
            AMD64.containsMatchIn(ua) -> "amd64"
            ARM64.containsMatchIn(ua) -> "arm64"
            ARM.containsMatchIn(ua) -> "arm"
            IA32.containsMatchIn(ua) -> "ia32"
            else -> ""
        }
        return Info(b, bv, e, ev, o, osVer, device, vendor, model, cpu)
    }
}
