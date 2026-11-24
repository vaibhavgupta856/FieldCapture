package com.fieldcapture.app.extraction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.fieldcapture.app.core.CaptureRecord
import com.fieldcapture.app.core.DamageFields
import com.fieldcapture.app.core.Field
import com.fieldcapture.app.core.NameplateFields
import com.fieldcapture.app.core.NoteFields
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

private data class OcrToken(val text: String, val confidence: Float)

/**
 * On-device OCR for nameplates (works with no signal), VLM for damage and free-text notes.
 * Both paths emit per-field confidence; VLM confidence is later recalibrated against
 * the eval set (raw model confidence is not trustworthy as-is).
 */
class HybridExtractor(
    context: Context,
    private val gemini: GeminiClient?,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extract(capture: CaptureRecord, bitmap: Bitmap): CaptureRecord = withContext(Dispatchers.IO) {
        when (capture.type) {
            com.fieldcapture.app.core.CaptureType.NAMEPLATE -> {
                val tokens = runCatching {
                    Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                }.getOrNull()
                val list = mutableListOf<OcrToken>()
                tokens?.textBlocks?.forEach { block ->
                    block.lines.forEach { line ->
                        line.elements.forEach { el ->
                            list += OcrToken(el.text, el.confidence)
                        }
                    }
                }
                capture.nameplate = parseNameplate(list)
            }
            com.fieldcapture.app.core.CaptureType.DAMAGE -> {
                val prompt = damagePrompt(capture.commandText)
                val response = gemini?.generateJson(prompt, bitmap)
                capture.damage = if (response == null) {
                    val hint = capture.commandText.split(Regex("\\s+")).getOrNull(2)
                    DamageFields(
                        room = Field(hint, 0.5f, "VOICE"),
                        surface = Field(null, 0f, "UNKNOWN"),
                        damageType = Field(null, 0f, "UNKNOWN"),
                        approximateSize = Field(null, 0f, "UNKNOWN"),
                    )
                } else parseDamage(JSONObject(response.text), response)
            }
            com.fieldcapture.app.core.CaptureType.NOTE -> {
                val prompt = notePrompt(capture.commandText)
                val response = gemini?.generateJson(prompt, bitmap)
                capture.note = if (response == null) {
                    NoteFields(Field(capture.commandText.removePrefix("note:").trim(), 1f, "VOICE"))
                } else parseNote(JSONObject(response.text), capture.commandText)
            }
        }
        capture
    }

    /** Text bounding boxes, used by the framing check before a nameplate is accepted. */
    suspend fun textBoxes(bitmap: Bitmap): List<Rect> = withContext(Dispatchers.IO) {
        runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))) }
            .getOrNull()
            ?.textBlocks
            ?.mapNotNull { it.boundingBox }
            ?: emptyList()
    }

    fun close() = recognizer.close()

    private fun parseNameplate(tokens: List<OcrToken>): NameplateFields {
        val joined = tokens.joinToString(" ") { it.text }
        fun find(pattern: String): MatchResult? = Regex(pattern, RegexOption.IGNORE_CASE).find(joined)
        fun confidenceFor(value: String?, match: MatchResult?): Float {
            if (value.isNullOrBlank()) return 0f
            val matched = match?.value?.lowercase(Locale.ROOT).orEmpty()
            val contributing = tokens.filter {
                matched.contains(it.text.lowercase(Locale.ROOT)) &&
                    it.text.length >= 2 &&
                    !it.text.equals("model", true) &&
                    !it.text.equals("serial", true)
            }
            val raw = if (contributing.isEmpty()) tokens.map { it.confidence }.average().toFloat() * 0.8f
            else contributing.map { it.confidence }.average().toFloat()
            // A short match is more likely to be a coincidental substring.
            val lengthBonus = (value.length.coerceAtMost(12) / 12f) * 0.1f
            return (raw * 0.9f + lengthBonus).coerceIn(0f, 1f)
        }

        val brands = listOf(
            "rheem", "bradford white", "a.o. smith", "ao smith", "whirlpool",
            "ge", "bosch", "carrier", "trane", "lennox", "american standard", "navien",
        )
        val makeMatch = brands.firstOrNull { joined.lowercase(Locale.ROOT).contains(it) }
        val makeFallback = Regex("""\b[A-Z][A-Za-z]{2,}(?:\s+[A-Z][A-Za-z]{2,})?\b""").find(joined)
        val make = makeMatch ?: makeFallback?.value?.takeIf {
            !it.equals("model", true) && !it.equals("serial", true) && !it.equals("date", true)
        }

        val modelMatch = find("""\bmodel\s*(?:no\.?|number|#)?\s*[:#]?\s*([A-Z0-9][A-Z0-9\-/]{2,})""")
        val serialMatch = find("""\bser(?:ial)?\s*(?:no\.?|number|#)?\s*[:#]?\s*([A-Z0-9][A-Z0-9\-]{4,})""")
        val dateMatch = find("""\b(?:mfg|manufactured?|date)\s*[:#]?\s*([0-9]{2}[/-][0-9]{2}[/-][0-9]{2,4}|[A-Z][a-z]{2}\s+[0-9]{4}|(?:19|20)[0-9]{2})""")

        fun group(match: MatchResult?) = match?.groupValues?.getOrNull(1)
        return NameplateFields(
            make = Field(make, confidenceFor(make, null), "OCR", joined.take(160)),
            model = Field(group(modelMatch), confidenceFor(group(modelMatch), modelMatch), "OCR"),
            serialNumber = Field(group(serialMatch), confidenceFor(group(serialMatch), serialMatch), "OCR"),
            manufactureDate = Field(group(dateMatch), confidenceFor(group(dateMatch), dateMatch), "OCR"),
        )
    }

    private fun damagePrompt(command: String): String = """
        You are extracting structured field notes from a photo taken by a damage inspector.
        The technician said: "$command".
        Return ONLY a JSON object with exactly these keys. Each value is an object with
        "value" and "confidence" (0..1). Use null and confidence 0 when the photo does not
        support the field. Do not invent data.
        {
          "room": {"value": string|null, "confidence": number},
          "surface": {"value": string|null, "confidence": number},
          "damage_type": {"value": string|null, "confidence": number},
          "approximate_size": {"value": string|null, "confidence": number},
          "size_sq_ft": {"value": number|null, "confidence": number}
        }
        Prefer the spoken room/surface when the image is ambiguous, but lower confidence.
        Estimate size in square feet from visible reference objects; set low confidence
        (<0.5) because there is no depth sensor.
    """.trimIndent()

    private fun notePrompt(command: String): String = """
        Structure this free-text field note: "$command".
        Return ONLY a JSON object with keys "text", "location", "subject", "severity".
        Each value is {"value": string|null, "confidence": number in 0..1}.
        "severity" must be one of low, medium, high, or null. Do not invent facts.
    """.trimIndent()

    private fun stringField(obj: JSONObject, key: String, source: String = "VLM"): Field<String> {
        val f = obj.optJSONObject(key) ?: return Field(null, 0f, source)
        val raw = f.opt("value")
        val value = if (raw == null || raw == JSONObject.NULL) null else raw.toString().takeIf { it.isNotBlank() }
        val confidence = f.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
        return Field(value, if (value == null) 0f else confidence, source)
    }

    private fun parseDamage(obj: JSONObject, response: GeminiResponse): DamageFields {
        val size = stringField(obj, "size_sq_ft")
        val sqFt = size.value?.toDoubleOrNull()
        return DamageFields(
            room = stringField(obj, "room"),
            surface = stringField(obj, "surface"),
            damageType = stringField(obj, "damage_type"),
            approximateSize = stringField(obj, "approximate_size"),
            sizeSqFt = if (sqFt == null) null else Field(sqFt, size.confidence, "VLM"),
        )
    }

    private fun parseNote(obj: JSONObject, command: String): NoteFields {
        val spoken = command.substringAfter(":", command).trim()
        val text = stringField(obj, "text")
        return NoteFields(
            text = if (text.value == null) Field(spoken, 1f, "VOICE") else text,
            location = stringField(obj, "location"),
            subject = stringField(obj, "subject"),
            severity = stringField(obj, "severity"),
        )
    }
}
