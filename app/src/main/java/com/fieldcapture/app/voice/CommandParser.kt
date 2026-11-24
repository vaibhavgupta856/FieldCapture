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
            val delimited = rest.split(Regex("\\s*,\\s*|\\s+in\\s+|\\s+on\\s+"))
                .map { it.trim(' ', ',', '.') }
                .filter { it.isNotEmpty() }
            // No comma / "in" / "on": fall back to whitespace-separated words.
            val parts = if (delimited.size >= 2) delimited
            else rest.split(Regex("\\s+")).filter { it.isNotEmpty() }

            val roomIndex = parts.indexOfFirst { p -> rooms.any { p == it || p.contains(it) } }
            val surfaceIndex = parts.indexOfFirst { p ->
                p != parts.getOrNull(roomIndex) && surfaces.any { p == it || p.contains(it) }
            }
            val directions = setOf("north", "south", "east", "west", "upper", "lower", "behind", "under")
            val finalRoom = parts.getOrNull(roomIndex)
            val finalSurface = when {
                surfaceIndex !in parts.indices -> null
                roomIndex >= 0 && surfaceIndex > roomIndex -> {
                    val start = if (surfaceIndex > 0 && parts[surfaceIndex - 1] in directions) surfaceIndex - 1 else surfaceIndex
                    parts.drop(start).joinToString(" ")
                }
                roomIndex >= 0 && surfaceIndex < roomIndex -> {
                    parts.take(roomIndex).joinToString(" ").ifBlank { parts[surfaceIndex] }
                }
                else -> parts[surfaceIndex]
            }
            return Command.Damage(finalRoom, finalSurface, raw)
        }
        return Command.Unknown(raw, "unrecognised command")
    }
}
