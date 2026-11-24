package com.fieldcapture.app.dat

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.fieldcapture.app.core.CaptureType
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the full DAT lifecycle. Key edge cases handled:
 *  - capabilities are attached only after STARTED, and only once (resume keeps them)
 *  - start()/stop() are fire-and-forget; errors arrive on session.errors
 *  - STOPPED is terminal: recreate a new session on reconnect
 *  - stream CRITICAL errors tear down; PAUSED is left alone and resumes by itself
 */
class SessionSupervisor(
    private val activity: Activity,
    private val onEvent: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var sessionJob: Job? = null
    private var errorJob: Job? = null
    private var streamJob: Job? = null
    private var streamErrorJob: Job? = null
    private var userStopped = false
    private var retries = 0

    val state: StateFlow<String> = kotlinx.coroutines.flow.MutableStateFlow("IDLE")
    private val mutableState = state as kotlinx.coroutines.flow.MutableStateFlow<String>

    fun start() {
        userStopped = false
        scope.launch {
            if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
                mutableState.value = "REGISTERING"
                onEvent("Not registered with Meta AI - starting registration")
                Wearables.startRegistration(activity)
                return@launch
            }
            var granted = false
            Wearables.checkPermissionStatus(Permission.CAMERA).fold(
                onSuccess = { granted = it == PermissionStatus.Granted },
                onFailure = { error, _ -> onEvent("Permission check failed: ${error.description}") },
            )
            if (!granted) {
                mutableState.value = "PERMISSION_DENIED"
                onEvent("DAT CAMERA permission denied - grant it in Meta AI")
                return@launch
            }
            createSession()
        }
    }

    private fun createSession() {
        Wearables.createSession(AutoDeviceSelector()).fold(
            onSuccess = { created ->
                if (session != null) return@fold
                session = created
                mutableState.value = "CONNECTING"
                sessionJob = scope.launch {
                    created.state.collect { handleState(created, it) }
                }
                errorJob = scope.launch {
                    created.errors.collect { onEvent("Session error: ${it.description}") }
                }
                // Subscribe first: start() is fire-and-forget and reports through errors.
                created.start()
            },
            onFailure = { error, _ ->
                mutableState.value = "FAILED"
                onEvent("createSession failed: ${error.description}")
                scheduleReconnect()
            },
        )
    }

    private fun handleState(active: DeviceSession, value: DeviceSessionState) {
        mutableState.value = value.name
        when (value) {
            DeviceSessionState.STARTED -> {
                retries = 0
                if (camera == null) attachCamera(active)
            }
            DeviceSessionState.PAUSED -> onEvent("Session paused - waiting for automatic resume")
            DeviceSessionState.STOPPING -> onEvent("Session stopping")
            DeviceSessionState.STOPPED -> {
                cleanup()
                if (!userStopped) {
                    onEvent("Device dropped mid-walk - queued captures are safe")
                    scheduleReconnect()
                } else mutableState.value = "STOPPED"
            }
            else -> Unit
        }
    }

    private fun attachCamera(active: DeviceSession) {
        active.addCamera(
            StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 15),
        ).fold(
            onSuccess = { added ->
                camera = added
                streamJob = scope.launch {
                    added.stream.state.collect { streamState ->
                        mutableState.value = "STREAM_${streamState.name}"
                        if (streamState == StreamState.PAUSED) onEvent("Glasses tapped: stream paused")
                        if (streamState == StreamState.CLOSED && !userStopped) scheduleReconnect()
                    }
                }
                streamErrorJob = scope.launch {
                    added.stream.errorStream.collect { onEvent("Stream error: ${it.description}") }
                }
                added.stream.start().fold(
                    onSuccess = { onEvent("Streaming from ${added.stream.state.value}") },
                    onFailure = { error, _ -> onEvent("stream.start failed: ${error.description}") },
                )
            },
            onFailure = { error, _ -> onEvent("addCamera failed: ${error.description}") },
        )
    }

    fun pause() {
        session?.pause()
        onEvent("pause() requested")
    }

    fun resume() {
        session?.resume()
        onEvent("resume() requested")
    }

    fun stop() {
        userStopped = true
        camera?.stop()
        session?.stop()
        cleanup()
        mutableState.value = "STOPPED"
        onEvent("Walk finished - session stopped")
    }

    private fun cleanup() {
        sessionJob?.cancel()
        errorJob?.cancel()
        streamJob?.cancel()
        streamErrorJob?.cancel()
        sessionJob = null
        errorJob = null
        streamJob = null
        streamErrorJob = null
        camera = null
        session = null
    }

    private fun scheduleReconnect() {
        if (userStopped) return
        retries++
        if (retries > 3) {
            onEvent("Reconnect attempts exhausted - queue retained, retry via Start walk")
            return
        }
        scope.launch {
            val wait = 1000L * retries
            onEvent("Reconnecting in ${wait / 1000}s (attempt $retries)")
            delay(wait)
            if (!userStopped) start()
        }
    }

    suspend fun capturePhoto(): Bitmap? = withContext(Dispatchers.IO) {
        val stream = camera?.stream
        if (stream == null) {
            onEvent("No active stream - cannot capture")
            return@withContext null
        }
        stream.capturePhoto().fold(
            onSuccess = { data ->
                when (data) {
                    is PhotoData.Bitmap -> data.bitmap
                    is PhotoData.HEIC -> runCatching {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(data.data))
                    }.getOrNull()
                }
            },
            onFailure = { error, _ ->
                onEvent("capturePhoto failed: ${error.description}")
                null
            },
        )
    }

    fun isStreaming(): Boolean = camera?.stream?.state?.value == StreamState.STREAMING

    fun describe(): String = "session=${state.value}"
}
