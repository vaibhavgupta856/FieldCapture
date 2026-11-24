package com.fieldcapture.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object JobJson {
    fun field(value: Any?, confidence: Float, source: String, evidence: String? = null): JSONObject =
        JSONObject().apply {
            put("value", value)
            put("confidence", confidence.toDouble())
            put("source", source)
            if (evidence != null) put("evidence", evidence)
        }

    fun toJson(job: JobRecord): JSONObject {
        val captures = JSONArray()
        job.captures.forEach { c ->
            val q = c.quality
            val o = JSONObject().apply {
                put("id", c.id)
                put("type", c.type.name)
                put("command_text", c.commandText)
                put("created_at_ms", c.createdAtMs)
                put("image_path", c.imagePath)
                put("status", c.status.name)
                put("attempts", c.attempts)
                if (c.lastError != null) put("last_error", c.lastError)
                put(
                    "quality",
                    JSONObject().apply {
                        put("laplacian_variance", q.laplacianVariance)
                        put("mean_luma", q.meanLuma)
                        put("dark_fraction", q.darkFraction)
                        put("clipped_fraction", q.clippedFraction)
                        put("detail_fraction", q.detailFraction)
                        put("text_coverage", q.textCoverage)
                        put("passed", q.passed)
                        put("reasons", JSONArray(q.reasons))
                    },
                )
                c.nameplate?.let { n ->
                    put(
                        "nameplate",
                        JSONObject().apply {
                            put("make", field(n.make.value, n.make.confidence, n.make.source, n.make.evidence))
                            put("model", field(n.model.value, n.model.confidence, n.model.source, n.model.evidence))
                            put("serial_number", field(n.serialNumber.value, n.serialNumber.confidence, n.serialNumber.source, n.serialNumber.evidence))
                            put("manufacture_date", field(n.manufactureDate.value, n.manufactureDate.confidence, n.manufactureDate.source, n.manufactureDate.evidence))
                        },
                    )
                }
                c.damage?.let { d ->
                    val size = if (d.sizeSqFt == null) JSONObject.NULL else field(d.sizeSqFt.value, d.sizeSqFt.confidence, d.sizeSqFt.source, d.sizeSqFt.evidence)
                    put(
                        "damage",
                        JSONObject().apply {
                            put("room", field(d.room.value, d.room.confidence, d.room.source, d.room.evidence))
                            put("surface", field(d.surface.value, d.surface.confidence, d.surface.source, d.surface.evidence))
                            put("damage_type", field(d.damageType.value, d.damageType.confidence, d.damageType.source, d.damageType.evidence))
                            put("approximate_size", field(d.approximateSize.value, d.approximateSize.confidence, d.approximateSize.source, d.approximateSize.evidence))
                            put("size_sq_ft_estimate", size)
                        },
                    )
                }
                c.note?.let { n ->
                    put(
                        "note",
                        JSONObject().apply {
                            put("text", field(n.text.value, n.text.confidence, n.text.source, n.text.evidence))
                            put("location", if (n.location == null) JSONObject.NULL else field(n.location.value, n.location.confidence, n.location.source))
                            put("subject", if (n.subject == null) JSONObject.NULL else field(n.subject.value, n.subject.confidence, n.subject.source))
                            put("severity", if (n.severity == null) JSONObject.NULL else field(n.severity.value, n.severity.confidence, n.severity.source))
                        },
                    )
                }
            }
            captures.put(o)
        }

        val items = JSONArray()
        job.lineItems.forEach {
            items.put(
                JSONObject().apply {
                    put("code", it.code)
                    put("description", it.description)
                    put("unit", it.unit)
                    put("quantity", if (it.quantity == null) JSONObject.NULL else it.quantity)
                    put("basis", it.basis)
                    put("confidence", it.confidence.toDouble())
                    put("capture_ids", JSONArray(it.captureIds))
                    put("status", "DRAFT")
                },
            )
        }

        val transcript = JSONArray()
        job.transcript.forEach { transcript.put(it) }
        val diagnostics = JSONObject()
        job.diagnostics.forEach { (k, v) -> diagnostics.put(k, v) }

        return JSONObject().apply {
            put("schema_version", job.schemaVersion)
            put("job_id", job.jobId)
            put("started_at_ms", job.startedAtMs)
            put("ended_at_ms", if (job.endedAtMs == null) JSONObject.NULL else job.endedAtMs)
            put("device_mode", job.deviceMode)
            put("transcript", transcript)
            put("captures", captures)
            put("draft_line_items", items)
            put("diagnostics", diagnostics)
        }
    }

    fun write(directory: File, job: JobRecord): File {
        directory.mkdirs()
        val file = File(directory, "${job.jobId}.json")
        val tmp = File(directory, "${job.jobId}.json.tmp")
        tmp.writeText(toJson(job).toString(2))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        return file
    }
}
