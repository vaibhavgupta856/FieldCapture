package com.fieldcapture.app.queue

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import com.fieldcapture.app.BuildConfig
import com.fieldcapture.app.core.CaptureStatus
import com.fieldcapture.app.core.JobRecord
import com.fieldcapture.app.core.JobJson
import com.fieldcapture.app.core.LineItemCatalog
import com.fieldcapture.app.extraction.GeminiClient
import com.fieldcapture.app.extraction.HybridExtractor

/** Extracts queued captures and writes the job record. Retries are idempotent by capture id. */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val queue = CaptureQueue(applicationContext)
        if (queue.isOfflineSimulated()) return ListenableWorker.Result.retry()

        val jobId = queue.activeJobId ?: return ListenableWorker.Result.success()
        val gemini = if (BuildConfig.GEMINI_API_KEY.isBlank()) null
        else GeminiClient(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL)
        val extractor = HybridExtractor(applicationContext, gemini)
        try {
            queue.pending().forEach { capture ->
                capture.attempts += 1
                val bitmap = BitmapFactory.decodeFile(capture.imagePath)
                if (bitmap == null) {
                    capture.status = CaptureStatus.FAILED
                    capture.lastError = "image file missing"
                    queue.upsert(capture)
                    return@forEach
                }
                runCatching { extractor.extract(capture, bitmap) }
                    .onSuccess {
                        capture.status = CaptureStatus.UPLOADED
                        capture.lastError = null
                    }
                    .onFailure { error ->
                        capture.status = CaptureStatus.FAILED
                        capture.lastError = error.message ?: error::class.java.simpleName
                    }
                queue.upsert(capture)
                bitmap.recycle()
            }

            val captures = queue.forJob(jobId)
            val job = JobRecord(
                jobId = jobId,
                startedAtMs = queue.activeJobStartedAt,
                endedAtMs = System.currentTimeMillis(),
                transcript = queue.transcript(),
                captures = captures,
                lineItems = LineItemCatalog.build(captures),
                diagnostics = mapOf(
                    "gemini_model" to if (gemini == null) "NONE" else BuildConfig.GEMINI_MODEL,
                    "pending_after_sync" to queue.countPending().toString(),
                ),
            )
            JobJson.write(queue.jobDir, job)
            return if (queue.countPending() == 0) ListenableWorker.Result.success()
            else ListenableWorker.Result.retry()
        } finally {
            extractor.close()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "fieldcapture-sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
