package com.fieldcapture.app.extraction

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GeminiResponse(
    val text: String,
    val promptTokens: Int?,
    val outputTokens: Int?,
)

/** Thin REST wrapper. No third-party HTTP dependency. */
class GeminiClient(
    private val apiKey: String,
    private val model: String,
) {
    suspend fun generateJson(prompt: String, bitmap: Bitmap): GeminiResponse = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream().apply {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, this)
        }.toByteArray()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val request = JSONObject().apply {
            put(
                "contents",
                org.json.JSONArray().put(
                    JSONObject().put(
                        "parts",
                        org.json.JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "image/jpeg")
                                        .put("data", encoded),
                                ),
                            ),
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.0)
                    .put("responseMimeType", "application/json"),
            )
        }

        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=" +
                URLEncoder.encode(apiKey, "UTF-8")
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw IOException("Gemini HTTP $code: ${body.take(400)}")
            val json = JSONObject(body)
            val text = json.getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).optString("text", "")
            val usage = json.optJSONObject("usageMetadata")
            GeminiResponse(
                text = text,
                promptTokens = usage?.optInt("promptTokenCount"),
                outputTokens = usage?.optInt("candidatesTokenCount"),
            )
        } finally {
            connection.disconnect()
        }
    }
}
