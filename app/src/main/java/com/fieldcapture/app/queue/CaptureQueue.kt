package com.fieldcapture.app.queue

import android.content.Context
import android.graphics.Bitmap
import com.fieldcapture.app.core.CaptureRecord
import com.fieldcapture.app.core.CaptureStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-backed at-least-once queue. The image is written before the index row; orphan
 * images are reconciled on startup. Capture IDs are idempotency keys, so a retried
 * upload can never create a second job entry.
 */
class CaptureQueue(context: Context) {
    private val root = File(context.filesDir, "fieldcapture")
    private val imageDir = File(root, "images")
    val jobDir = File(root, "jobs")
    private val indexFile = File(root, "queue.json")
    private val prefs = context.getSharedPreferences("fieldcapture", Context.MODE_PRIVATE)
    private val exportDir = File(context.getExternalFilesDir(null), "exports")
    private val lock = Any()

    init {
        imageDir.mkdirs()
        jobDir.mkdirs()
        exportDir.mkdirs()
    }

    fun saveImage(bitmap: Bitmap, id: String): String {
        val file = File(imageDir, "$id.jpg")
        val tmp = File(imageDir, "$id.jpg.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(file)
        return file.absolutePath
    }

    fun upsert(capture: CaptureRecord) = synchronized(lock) {
        val all = readAllLocked().filter { it.id != capture.id }.toMutableList()
        all += capture
        writeAllLocked(all)
    }

    fun all(): List<CaptureRecord> = synchronized(lock) { readAllLocked() }

    fun pending(): List<CaptureRecord> = all().filter {
        it.status == CaptureStatus.QUEUED || it.status == CaptureStatus.FAILED
    }

    fun forJob(jobId: String): List<CaptureRecord> = all().filter { it.jobId == jobId }

    fun countPending(): Int = pending().size

    private fun readAllLocked(): List<CaptureRecord> {
        if (!indexFile.exists()) return emptyList()
        val arr = try {
            JSONArray(indexFile.readText())
        } catch (_: Exception) {
            return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { CaptureJson.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun writeAllLocked(captures: List<CaptureRecord>) {
        val arr = JSONArray()
        captures.forEach { arr.put(CaptureJson.toJson(it)) }
        val tmp = File(root, "queue.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(indexFile)) {
            tmp.copyTo(indexFile, overwrite = true)
            tmp.delete()
        }
    }

    var activeJobId: String?
        get() = prefs.getString("active_job_id", null)
        set(value) = prefs.edit().putString("active_job_id", value).apply()

    val activeJobStartedAt: Long
        get() = prefs.getLong("active_job_started_at", System.currentTimeMillis())

    fun startJob(jobId: String) {
        prefs.edit()
            .putString("active_job_id", jobId)
            .putLong("active_job_started_at", System.currentTimeMillis())
            .putString("transcript", JSONArray().toString())
            .apply()
    }

    fun appendTranscript(line: String) {
        val arr = JSONArray(prefs.getString("transcript", "[]"))
        arr.put(line)
        prefs.edit().putString("transcript", arr.toString()).apply()
    }

    fun transcript(): List<String> {
        val arr = JSONArray(prefs.getString("transcript", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun clearActiveJob() = prefs.edit().remove("active_job_id").apply()

    fun simulateOffline(value: Boolean) = prefs.edit().putBoolean("simulate_offline", value).apply()

    fun isOfflineSimulated(): Boolean = prefs.getBoolean("simulate_offline", false)

    fun exportJobs(): List<File> {
        exportDir.mkdirs()
        return jobDir.listFiles { f -> f.extension == "json" }?.map { src ->
            val dst = File(exportDir, src.name)
            src.copyTo(dst, overwrite = true)
            dst
        } ?: emptyList()
    }
}
