package com.fieldcapture.app

import com.fieldcapture.app.core.CaptureRecord
import com.fieldcapture.app.core.CaptureStatus
import com.fieldcapture.app.core.CaptureType
import com.fieldcapture.app.core.QualityMetrics
import com.fieldcapture.app.core.LineItemCatalog
import com.fieldcapture.app.voice.Command
import com.fieldcapture.app.voice.CommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    @Test
    fun `parses nameplate`() {
        assertTrue(CommandParser.parse("capture nameplate") is Command.Nameplate)
    }

    @Test
    fun `parses damage with comma separated room and surface`() {
        val parsed = CommandParser.parse("capture damage, kitchen, north wall") as Command.Damage
        assertEquals("kitchen", parsed.room)
        assertEquals("north wall", parsed.surface)
    }

    @Test
    fun `parses damage without commas using vocabulary`() {
        val parsed = CommandParser.parse("capture damage ceiling kitchen") as Command.Damage
        assertEquals("kitchen", parsed.room)
        assertEquals("ceiling", parsed.surface)
    }

    @Test
    fun `parses note text verbatim`() {
        val parsed = CommandParser.parse("note: subfloor soft by the cabinet") as Command.Note
        assertEquals("subfloor soft by the cabinet", parsed.text)
    }

    @Test
    fun `rejects unknown command`() {
        assertTrue(CommandParser.parse("make me a sandwich") is Command.Unknown)
    }
}

class LineItemCatalogTest {
    private fun capture(
        type: CaptureType,
        id: String = "c1",
        room: String? = null,
        surface: String? = null,
        kind: String? = null,
        note: String? = null,
    ): CaptureRecord {
        val q = QualityMetrics(200.0, 90.0, 0.0, 0.0, 0.2, 0.1, true, emptyList())
        return CaptureRecord(id, "job1", type, "", 0L, "", q).apply {
            if (type == CaptureType.DAMAGE) {
                damage = com.fieldcapture.app.core.DamageFields(
                    room = com.fieldcapture.app.core.Field(room, 0.9f),
                    surface = com.fieldcapture.app.core.Field(surface, 0.9f),
                    damageType = com.fieldcapture.app.core.Field(kind, 0.9f),
                    approximateSize = com.fieldcapture.app.core.Field("2x3 ft", 0.5f),
                    sizeSqFt = com.fieldcapture.app.core.Field(6.0, 0.5f),
                )
            }
            if (type == CaptureType.NOTE) {
                note = com.fieldcapture.app.core.NoteFields(com.fieldcapture.app.core.Field(note, 1f, "VOICE"))
            }
            status = CaptureStatus.UPLOADED
        }
    }

    @Test
    fun `wall damage maps to drywall paint and cleanup`() {
        val items = LineItemCatalog.build(listOf(capture(CaptureType.DAMAGE, surface = "north wall", kind = "water stain")))
        val codes = items.map { it.code }
        assertTrue("DRYWALL_RR_SF" in codes)
        assertTrue("PAINT_WALL_SF" in codes)
        assertTrue("CLEANUP_HR" in codes)
        assertEquals(6.0, items.first { it.code == "DRYWALL_RR_SF" }.quantity)
    }

    @Test
    fun `subfloor note maps to subfloor item with uncertain quantity`() {
        val items = LineItemCatalog.build(
            listOf(capture(CaptureType.NOTE, note = "subfloor soft by the cabinet")),
        )
        val subfloor = items.first { it.code == "SUBFLOOR_RR_SF" }
        assertEquals(null, subfloor.quantity)
        assertTrue(subfloor.confidence < 0.7f)
    }
}
