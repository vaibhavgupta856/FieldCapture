package com.fieldcapture.app.capture

import android.content.Context
import com.fieldcapture.app.core.CaptureRecord
import com.fieldcapture.app.core.CaptureType
import com.fieldcapture.app.dat.SessionSupervisor
import com.fieldcapture.app.extraction.HybridExtractor
import com.fieldcapture.app.quality.QualityGate
import com.fieldcapture.app.queue.CaptureQueue
import com.fieldcapture.app.queue.SyncWorker
import com.fieldcapture.app.voice.Command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class CaptureCoordinator(
    private val context: Context,
    private val supervisor: SessionSupervisor,
    private val extractor: HybridExtractor,
    private val queue: CaptureQueue,
    private val onEvent: (String) -> Unit,
    private val speak: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureMutex = Mutex()
    private var activeJobId: String? = null

    fun startWalk(): String {
        val id = "job_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        queue.startJob(id)
        activeJobId = id
        onEvent("Walk started: $id")
        return id
    }

    fun handle(command: Command) {
        queue.appendTranscript(command.rawText())
        scope.launch {
            when (command) {
                is Command.Nameplate -> capture(CaptureType.NAMEPLATE, command.raw)
                is Command.Damage -> capture(CaptureType.DAMAGE, command.raw)
                is Command.Note -> capture(CaptureType.NOTE, command.raw)
                is Command.Unknown -> {
                    onEvent("Unknown command: ${command.raw} (${command.reason})")
                    speak("Didn't understand. Say capture nameplate, capture damage, or note.")
                }
            }
        }
    }

    private suspend fun capture(type: CaptureType, raw: String) = captureMutex.withLock {
        if (!supervisor.isStreaming()) {
            onEvent("Capture skipped: stream not live (${supervisor.describe()})")
            speak("Stream not live. Try again.")
            return@withLock
        }
        val bitmap = supervisor.capturePhoto()
        if (bitmap == null) {
            speak("Capture failed. Try again.")
            return@withLock
        }
        val boxes = if (type == CaptureType.NAMEPLATE) extractor.textBoxes(bitmap) else emptyList()
        val metrics = QualityGate.evaluate(bitmap, type, boxes)
        if (!metrics.passed) {
            val reason = when {
                metrics.reasons.any { it.startsWith("blurry") } -> "Too blurry. Hold steady and retake."
                metrics.reasons.any { it.startsWith("dark") } -> "Too dark. Add light and retake."
                metrics.reasons.any { it.startsWith("glare") } -> "Glare. Change angle and retake."
                else -> "Poor framing. Get closer and retake."
            }
            onEvent("RETAKE ($type): ${metrics.reasons.joinToString("; ")}")
            speak(reason)
            bitmap.recycle()
            return@withLock
        }

        val id = UUID.randomUUID().toString()
        val jobId = activeJobId ?: queue.activeJobId ?: startWalk()
        val path = queue.saveImage(bitmap, id)
        bitmap.recycle()
        val record = CaptureRecord(
            id = id,
            jobId = jobId,
            type = type,
            commandText = raw,
            createdAtMs = System.currentTimeMillis(),
            imagePath = path,
            quality = metrics,
        )
        queue.upsert(record)
        onEvent("ACCEPTED $type: lap=${metrics.laplacianVariance.toInt()} luma=${metrics.meanLuma.toInt()} coverage=${(metrics.textCoverage * 100).toInt()}%")
        speak("Captured.")
        SyncWorker.enqueue(context)
    }

    fun finishWalk() {
        SyncWorker.enqueue(context)
        onEvent("Walk ended - sync requested (queued=${queue.countPending()})")
    }

    fun syncNow() {
        SyncWorker.enqueue(context)
        onEvent("Sync requested (queued=${queue.countPending()})")
    }

    fun exportJobs() = queue.exportJobs()

    fun pendingCount(): Int = queue.countPending()

    private fun Command.rawText(): String = when (this) {
        is Command.Nameplate -> raw
        is Command.Damage -> raw
        is Command.Note -> raw
        is Command.Unknown -> raw
    }
}
