package com.fieldcapture.app.queue

import com.fieldcapture.app.core.CaptureRecord
import com.fieldcapture.app.core.CaptureStatus
import com.fieldcapture.app.core.CaptureType
import com.fieldcapture.app.core.DamageFields
import com.fieldcapture.app.core.Field
import com.fieldcapture.app.core.NameplateFields
import com.fieldcapture.app.core.NoteFields
import com.fieldcapture.app.core.QualityMetrics
import org.json.JSONObject

internal object CaptureJson {
    private fun field(f: Field<*>): JSONObject = JSONObject().apply {
        put("value", f.value)
        put("confidence", f.confidence.toDouble())
        put("source", f.source)
        if (f.evidence != null) put("evidence", f.evidence)
    }

    private fun fieldOrNull(f: Field<*>?): Any =
        if (f == null) JSONObject.NULL else field(f)

    private fun <T> readField(obj: JSONObject?, key: String): Field<T>? {
        val f = obj?.optJSONObject(key) ?: return null
        val raw = f.opt("value")
        @Suppress("UNCHECKED_CAST")
        val value = (if (raw == null || raw == JSONObject.NULL) null else raw) as T?
        return Field(value, f.optDouble("confidence", 0.0).toFloat(), f.optString("source", "UNKNOWN"), f.optString("evidence", null))
    }

    fun toJson(c: CaptureRecord): JSONObject {
        val q = c.quality
        return JSONObject().apply {
            put("id", c.id)
            put("job_id", c.jobId)
            put("type", c.type.name)
            put("command_text", c.commandText)
            put("created_at_ms", c.createdAtMs)
            put("image_path", c.imagePath)
            put("status", c.status.name)
            put("attempts", c.attempts)
            if (c.lastError != null) put("last_error", c.lastError)
            put("quality", JSONObject().apply {
                put("laplacian_variance", q.laplacianVariance)
                put("mean_luma", q.meanLuma)
                put("dark_fraction", q.darkFraction)
                put("clipped_fraction", q.clippedFraction)
                put("detail_fraction", q.detailFraction)
                put("text_coverage", q.textCoverage)
                put("passed", q.passed)
                put("reasons", org.json.JSONArray(q.reasons))
            })
            c.nameplate?.let {
                put("nameplate", JSONObject().apply {
                    put("make", field(it.make))
                    put("model", field(it.model))
                    put("serial_number", field(it.serialNumber))
                    put("manufacture_date", field(it.manufactureDate))
                })
            }
            c.damage?.let {
                put("damage", JSONObject().apply {
                    put("room", field(it.room))
                    put("surface", field(it.surface))
                    put("damage_type", field(it.damageType))
                    put("approximate_size", field(it.approximateSize))
                    put("size_sq_ft", fieldOrNull(it.sizeSqFt))
                })
            }
            c.note?.let {
                put("note", JSONObject().apply {
                    put("text", field(it.text))
                    put("location", fieldOrNull(it.location))
                    put("subject", fieldOrNull(it.subject))
                    put("severity", fieldOrNull(it.severity))
                })
            }
        }
    }

    fun fromJson(o: JSONObject): CaptureRecord {
        val q = o.getJSONObject("quality")
        val reasons = q.optJSONArray("reasons")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        val c = CaptureRecord(
            id = o.getString("id"),
            jobId = o.getString("job_id"),
            type = CaptureType.valueOf(o.getString("type")),
            commandText = o.getString("command_text"),
            createdAtMs = o.getLong("created_at_ms"),
            imagePath = o.getString("image_path"),
            quality = QualityMetrics(
                laplacianVariance = q.getDouble("laplacian_variance"),
                meanLuma = q.getDouble("mean_luma"),
                darkFraction = q.getDouble("dark_fraction"),
                clippedFraction = q.getDouble("clipped_fraction"),
                detailFraction = q.getDouble("detail_fraction"),
                textCoverage = q.getDouble("text_coverage"),
                passed = q.getBoolean("passed"),
                reasons = reasons,
            ),
            status = CaptureStatus.valueOf(o.optString("status", "QUEUED")),
            attempts = o.optInt("attempts"),
            lastError = o.optString("last_error", null),
        )
        o.optJSONObject("nameplate")?.let {
            c.nameplate = NameplateFields(
                make = readField<String>(it, "make")!!,
                model = readField<String>(it, "model")!!,
                serialNumber = readField<String>(it, "serial_number")!!,
                manufactureDate = readField<String>(it, "manufacture_date")!!,
            )
        }
        o.optJSONObject("damage")?.let {
            c.damage = DamageFields(
                room = readField<String>(it, "room")!!,
                surface = readField<String>(it, "surface")!!,
                damageType = readField<String>(it, "damage_type")!!,
                approximateSize = readField<String>(it, "approximate_size")!!,
                sizeSqFt = readField<Double>(it, "size_sq_ft"),
            )
        }
        o.optJSONObject("note")?.let {
            c.note = NoteFields(
                text = readField<String>(it, "text")!!,
                location = readField<String>(it, "location"),
                subject = readField<String>(it, "subject"),
                severity = readField<String>(it, "severity"),
            )
        }
        return c
    }
}
