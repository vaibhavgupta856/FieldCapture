package com.fieldcapture.app.core

/**
 * Draft line-item catalogue. Codes are ours, deliberately scoped to what a water-damage
 * walk-through can actually observe. `quantity` is derived from an observation or left
 * null; nothing here is a final estimate.
 */
object LineItemCatalog {
    data class Definition(val code: String, val description: String, val unit: String)

    val all = listOf(
        Definition("DRYWALL_RR_SF", "Remove and replace gypsum wallboard", "SQ_FT"),
        Definition("INSULATION_RR_SF", "Remove and replace batt insulation", "SQ_FT"),
        Definition("SUBFLOOR_RR_SF", "Remove and replace subfloor sheathing", "SQ_FT"),
        Definition("FLOORING_RR_SF", "Remove and replace finish flooring", "SQ_FT"),
        Definition("STUD_SISTER_LF", "Sister or replace framing members", "LIN_FT"),
        Definition("WATER_HEATER_DIAG_EA", "Diagnose water heater and leaks", "EACH"),
        Definition("WATER_HEATER_REPL_EA", "Replace water heater", "EACH"),
        Definition("PLUMB_SUPPLY_REPAIR_LF", "Repair supply line", "LIN_FT"),
        Definition("CABINET_BASE_RR_EA", "Remove and replace base cabinet", "EACH"),
        Definition("PAINT_WALL_SF", "Prime and paint affected surface", "SQ_FT"),
        Definition("MOLD_REMEDIATE_SF", "Antimicrobial treatment of affected surface", "SQ_FT"),
        Definition("DEHUMIDIFY_DAY", "Dehumidifier rental", "DAY"),
        Definition("CLEANUP_HR", "Cleanup, debris removal and disposal labor", "HOUR"),
        Definition("ACCESS_PANEL_EA", "Install access panel", "EACH"),
    )

    private fun byCode(code: String) = all.first { it.code == code }

    private fun item(
        code: String,
        quantity: Double?,
        basis: String,
        confidence: Float,
        captureIds: List<String>,
    ): LineItem {
        val d = byCode(code)
        return LineItem(d.code, d.description, d.unit, quantity, basis, confidence, captureIds)
    }

    fun build(captures: List<CaptureRecord>): List<LineItem> {
        val result = LinkedHashMap<String, LineItem>()
        fun add(item: LineItem) {
            val existing = result[item.code]
            if (existing == null) {
                result[item.code] = item
            } else {
                val q = if (existing.quantity != null && item.quantity != null) {
                    existing.quantity + item.quantity
                } else existing.quantity ?: item.quantity
                result[item.code] = existing.copy(
                    quantity = q,
                    confidence = minOf(existing.confidence, item.confidence),
                    captureIds = (existing.captureIds + item.captureIds).distinct(),
                )
            }
        }

        captures.forEach { c ->
            val id = listOf(c.id)
            c.damage?.let { d ->
                val surface = (d.surface.value ?: "").lowercase()
                val kind = (d.damageType.value ?: "").lowercase()
                val area = d.sizeSqFt?.value
                val basis = "damage capture ${c.id}: ${d.damageType.value} on ${d.surface.value}"
                val q = area?.toDouble()
                val conf = minOf(d.surface.confidence, d.damageType.confidence)

                if (surface.contains("wall") || surface.contains("ceiling")) {
                    add(item("DRYWALL_RR_SF", q, basis, conf, id))
                    add(item("PAINT_WALL_SF", q, basis, conf * 0.95f, id))
                    if (surface.contains("ceiling")) {
                        add(item("INSULATION_RR_SF", q, basis, conf * 0.8f, id))
                    }
                    if (kind.contains("mold") || kind.contains("mildew")) {
                        add(item("MOLD_REMEDIATE_SF", q, basis, conf * 0.9f, id))
                    }
                }
                if (surface.contains("floor") || surface.contains("subfloor")) {
                    add(item("SUBFLOOR_RR_SF", q, basis, conf, id))
                    add(item("FLOORING_RR_SF", q, basis, conf * 0.9f, id))
                }
                if (kind.contains("leak") || kind.contains("water")) {
                    add(item("DEHUMIDIFY_DAY", 1.0, basis, conf * 0.75f, id))
                }
                add(item("CLEANUP_HR", 1.0, basis, conf * 0.7f, id))
            }

            c.nameplate?.let { n ->
                val makeModel = "${n.make.value.orEmpty()} ${n.model.value.orEmpty()}".lowercase()
                if (makeModel.contains("water heater") || makeModel.contains("rheem") ||
                    makeModel.contains("bradford") || makeModel.contains("a.o. smith") ||
                    makeModel.contains("ao smith") || makeModel.contains("whirlpool")
                ) {
                    add(item("WATER_HEATER_DIAG_EA", 1.0, "nameplate ${c.id}: $makeModel", 0.75f, id))
                    add(item("WATER_HEATER_REPL_EA", 1.0, "nameplate ${c.id}: $makeModel", 0.55f, id))
                }
            }

            c.note?.let { n ->
                val text = n.text.value.orEmpty().lowercase()
                if (text.contains("subfloor") && (text.contains("soft") || text.contains("wet") || text.contains("rot"))) {
                    add(item("SUBFLOOR_RR_SF", null, "note ${c.id}: ${n.text.value}", 0.6f, id))
                }
                if (text.contains("cabinet")) {
                    add(item("CABINET_BASE_RR_EA", null, "note ${c.id}: ${n.text.value}", 0.5f, id))
                }
                if (text.contains("leak") || text.contains("supply line")) {
                    add(item("PLUMB_SUPPLY_REPAIR_LF", null, "note ${c.id}: ${n.text.value}", 0.5f, id))
                }
                if (text.contains("stud") || text.contains("framing")) {
                    add(item("STUD_SISTER_LF", null, "note ${c.id}: ${n.text.value}", 0.5f, id))
                }
            }
        }
        return result.values.toList()
    }
}
