package com.itools.app.data

import com.itools.app.util.Json
import java.time.Instant

/**
 * Settings backup as a human-readable (unencrypted) JSON file:
 * theme, dynamic color, language and favorite tools.
 */
object SettingsBackup {
    private const val APP = "ITools"
    private const val FORMAT = 1
    /** Languages supported by the app ("" = system language). */
    val LANGUAGES = setOf("", "en", "es")

    class Restored(val snapshot: SettingsSnapshot, val language: String?)

    fun toJson(s: SettingsSnapshot, language: String, appVersion: String): String = Json.stringify(linkedMapOf(
        "app" to APP,
        "format" to FORMAT,
        "appVersion" to appVersion,
        "createdAt" to Instant.now().toString(),
        "theme" to s.theme.name,
        "dynamicColor" to s.dynamicColor,
        "language" to language,
        "favorites" to s.favorites.sorted(),
    ))

    /** Validates the file; drops favorites of tools that no longer exist and unknown values. */
    fun fromJson(text: String, knownTools: Set<String>): Restored {
        val root = Json.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Not a backup")
        require(root["app"] == APP) { "Not an $APP backup" }
        val format = (root["format"] as? Number)?.toInt() ?: throw IllegalArgumentException("Missing format")
        require(format in 1..FORMAT) { "Unsupported backup format $format" }
        val theme = (root["theme"] as? String)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        val favorites = (root["favorites"] as? List<*>).orEmpty().filterIsInstance<String>().filter { it in knownTools }.toSet()
        return Restored(
            SettingsSnapshot(theme, root["dynamicColor"] as? Boolean ?: false, favorites),
            (root["language"] as? String)?.takeIf { it in LANGUAGES },
        )
    }
}
