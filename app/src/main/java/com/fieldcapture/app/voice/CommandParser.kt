package com.fieldcapture.app.voice

import java.util.Locale

sealed class Command {
    data class Nameplate(val raw: String) : Command()
    data class Damage(val room: String?, val surface: String?, val raw: String) : Command()
    data class Note(val text: String, val raw: String) : Command()
    data class Unknown(val raw: String, val reason: String) : Command()
}

/** Small deterministic grammar. New commands are added here, not in a prompt. */
object CommandParser {
    private val rooms = listOf(
        "kitchen", "bathroom", "bath", "bedroom", "living room", "hallway", "attic",
        "basement", "garage", "laundry", "utility room", "closet", "pantry",
    )
    private val surfaces = listOf(
        "north wall", "south wall", "east wall", "west wall", "ceiling", "floor",
        "subfloor", "wall", "cabinet", "behind cabinet", "under sink", "around pipe",
        "drywall", " insulation",
    ).map { it.trim() }

    fun parse(rawInput: String): Command {
        val raw = rawInput.trim()
        val lower = raw.lowercase(Locale.ROOT)

        if (lower.contains("capture nameplate") || lower == "nameplate") {
            return Command.Nameplate(raw)
        }
        if (lower.startsWith("note:") || lower.startsWith("note ")) {
            val text = raw.substringAfter(":").trim().ifBlank { raw.removePrefix("note").trim() }
            return if (text.isEmpty()) Command.Unknown(raw, "empty note") else Command.Note(text, raw)
        }
        if (lower.contains("capture damage") || lower.startsWith("damage ")) {
            val rest = lower.substringAfter("damage", "").trim(' ', ',', '.')
            val parts = rest.split(Regex("\\s*,\\s*|\\s+in\\s+|\\s+on\\s+"))
                .map { it.trim(' ', ',', '.') }
                .filter { it.isNotEmpty() }

            val room = parts.firstOrNull { p -> rooms.any { p == it || p.contains(it) } }
            val surface = parts.firstOrNull { p ->
                surfaces.any { p == it || p.contains(it) } && p != room
            }
            // Fallback: two trailing noun phrases with no comma -> first is room, last is surface.
            val finalRoom = room ?: parts.getOrNull(0)?.takeIf { parts.size >= 2 }
            val finalSurface = surface ?: parts.getOrNull(parts.size - 1)?.takeIf { parts.size >= 2 }
            return Command.Damage(finalRoom, finalSurface, raw)
        }
        return Command.Unknown(raw, "unrecognised command")
    }
}
