package com.fieldcapture.app.core

/**
 * Every extracted value carries its own confidence. Confidence is always in [0, 1].
 * `source` is one of: OCR, VLM, RULE, VOICE, USER.
 */
data class Field<T>(
    val value: T?,
    val confidence: Float,
    val source: String = "UNKNOWN",
    val evidence: String? = null,
)

enum class CaptureType { NAMEPLATE, DAMAGE, NOTE }

enum class CaptureStatus { QUEUED, EXTRACTED, UPLOADED, FAILED }

data class QualityMetrics(
    val laplacianVariance: Double,
    val meanLuma: Double,
    val darkFraction: Double,
    val clippedFraction: Double,
    val detailFraction: Double,
    val textCoverage: Double,
    val passed: Boolean,
    val reasons: List<String>,
)

data class NameplateFields(
    val make: Field<String>,
    val model: Field<String>,
    val serialNumber: Field<String>,
    val manufactureDate: Field<String>,
)

data class DamageFields(
    val room: Field<String>,
    val surface: Field<String>,
    val damageType: Field<String>,
    val approximateSize: Field<String>,
    val sizeSqFt: Field<Double>? = null,
)

data class NoteFields(
    val text: Field<String>,
    val location: Field<String>? = null,
    val subject: Field<String>? = null,
    val severity: Field<String>? = null,
)

data class CaptureRecord(
    val id: String,
    val jobId: String,
    val type: CaptureType,
    val commandText: String,
    val createdAtMs: Long,
    val imagePath: String,
    val quality: QualityMetrics,
    var nameplate: NameplateFields? = null,
    var damage: DamageFields? = null,
    var note: NoteFields? = null,
    var status: CaptureStatus = CaptureStatus.QUEUED,
    var attempts: Int = 0,
    var lastError: String? = null,
)

data class LineItem(
    val code: String,
    val description: String,
    val unit: String,
    val quantity: Double?,
    val basis: String,
    val confidence: Float,
    val captureIds: List<String>,
)

data class JobRecord(
    val jobId: String,
    val schemaVersion: String = "1.0",
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val deviceMode: String = "MOCK_DEVICE_KIT",
    val transcript: List<String> = emptyList(),
    val captures: List<CaptureRecord> = emptyList(),
    val lineItems: List<LineItem> = emptyList(),
    val diagnostics: Map<String, String> = emptyMap(),
)
