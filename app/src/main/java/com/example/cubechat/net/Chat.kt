package com.example.cubechat.net

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent

data class Span(val text: String, val rgb: Int?)
data class ChatLine(val spans: List<Span>)

/** Mengubah Component Minecraft (JSON text) menjadi potongan teks berwarna. */
object ChatText {
    private val translations = mapOf(
        "chat.type.text" to "<%s> %s",
        "chat.type.emote" to "* %s %s",
        "chat.type.announcement" to "[%s] %s",
        "multiplayer.player.joined" to "%s joined the game",
        "multiplayer.player.left" to "%s left the game",
        "commands.message.display.incoming" to "%s whispers to you: %s",
        "commands.message.display.outgoing" to "You whisper to %s: %s",
    )

    fun fromComponent(c: Component): ChatLine {
        val out = mutableListOf<Span>()
        walk(c, null, out)
        return ChatLine(out)
    }

    fun plain(c: Component): String = fromComponent(c).spans.joinToString("") { it.text }

    fun prefixed(prefix: String, c: Component): ChatLine =
        ChatLine(listOf(Span(prefix, null)) + fromComponent(c).spans)

    fun text(s: String, rgb: Int? = null) = ChatLine(listOf(Span(s, rgb)))

    private fun walk(c: Component, inherited: Int?, out: MutableList<Span>) {
        val color = c.color()?.value() ?: inherited
        if (c is TextComponent) {
            if (c.content().isNotEmpty()) out.add(Span(c.content(), color))
        } else if (c is TranslatableComponent) {
            val args = c.arguments().map { plain(it.asComponent()) }
            val pattern = translations[c.key()] ?: c.fallback()
            val text = if (pattern != null) format(pattern, args)
            else c.key() + if (args.isEmpty()) "" else " (" + args.joinToString(", ") + ")"
            out.add(Span(text, color))
        }
        for (child in c.children()) walk(child, color, out)
    }

    private val placeholder = Regex("""%(\d+\$)?s""")

    private fun format(pattern: String, args: List<String>): String {
        var next = 0
        return placeholder.replace(pattern) { m ->
            val explicit = m.groupValues[1].removeSuffix("$").toIntOrNull()
            val idx = if (explicit != null) explicit - 1 else next++
            args.getOrElse(idx) { "" }
        }
    }
}
