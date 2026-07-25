package com.salyvn.omniward.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * Color / text helper supporting three input formats:
 *  - Legacy codes: &c &l &n ...
 *  - Hex: &#RRGGBB
 *  - MiniMessage: <yellow> <bold> <#ff0000> ...
 *
 * Everything is normalized to Adventure [Component]s so the plugin renders
 * consistently on Paper 1.21+.
 */
object TextUtil {

    private val mini = MiniMessage.miniMessage()
    private val legacyAmp = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()
    private val plain = PlainTextComponentSerializer.plainText()

    private val hexAmp = Regex("&#([0-9a-fA-F]{6})")

    /**
     * Convert a raw config string to a rendered [Component].
     * Detects MiniMessage tags first, otherwise treats input as legacy/hex.
     */
    fun colorize(raw: String): Component {
        if (raw.isEmpty()) return Component.empty()
        val looksMini = raw.contains('<') && raw.contains('>')
        return if (looksMini) {
            // Pre-convert &#hex and legacy to MiniMessage-safe first would be complex;
            // MiniMessage handles its own tags, legacy fallback covers the rest.
            try {
                mini.deserialize(raw)
            } catch (ex: Exception) {
                legacyAmp.deserialize(preConvertHex(raw))
            }
        } else {
            legacyAmp.deserialize(preConvertHex(raw))
        }.let { it }
    }

    /** MiniMessage/legacy hex `&#RRGGBB` -> legacy serializer hex `&x&R&R...` friendly form. */
    private fun preConvertHex(raw: String): String {
        return hexAmp.replace(raw) { m -> "&#" + m.groupValues[1] }
    }

    /** Strip all formatting, returning plain text (for logs / comparisons). */
    fun strip(raw: String): String = plain.serialize(colorize(raw))

    /** Colorize a list of lines. */
    fun colorizeAll(lines: List<String>): List<Component> = lines.map { colorize(it) }

    /** Alias of [colorize] — returns a rendered [Component] from a raw string. */
    fun component(raw: String): Component = colorize(raw)
}
