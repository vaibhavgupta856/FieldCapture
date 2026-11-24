package com.fieldcapture.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fieldcapture.app.capture.CaptureCoordinator
import com.fieldcapture.app.dat.MockDeviceController
import com.fieldcapture.app.dat.SessionSupervisor
import com.fieldcapture.app.extraction.GeminiClient
import com.fieldcapture.app.extraction.HybridExtractor
import com.fieldcapture.app.queue.CaptureQueue
import com.fieldcapture.app.voice.CommandParser
import com.fieldcapture.app.voice.VoiceController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var offlineButton: Button
    private val logLines = StringBuilder()

    private lateinit var mock: MockDeviceController
    private lateinit var supervisor: SessionSupervisor
    private lateinit var extractor: HybridExtractor
    private lateinit var queue: CaptureQueue
    private lateinit var coordinator: CaptureCoordinator
    private lateinit var voice: VoiceController

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            results.forEach { (name, granted) -> log("Permission $name: $granted") }
            if (results[Manifest.permission.CAMERA] == true) log("Grant phone CAMERA before selecting the mock feed")
            if (results[Manifest.permission.RECORD_AUDIO] == true) voice.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        offlineButton = findViewById(R.id.btnOffline)

        mock = MockDeviceController(this)
        supervisor = SessionSupervisor(this) { log(it) }
        val gemini = if (BuildConfig.GEMINI_API_KEY.isBlank()) null
        else GeminiClient(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL)
        extractor = HybridExtractor(this, gemini)
        queue = CaptureQueue(this)
        voice = VoiceController(this) { text, final ->
            if (final) {
                log("HEARD: $text")
                coordinator.handle(CommandParser.parse(text))
            } else log("hearing: $text")
        }
        coordinator = CaptureCoordinator(this, supervisor, extractor, queue, { log(it) }) { speak(it) }

        lifecycleScope.launch {
            supervisor.state.collect { state ->
                statusText.text = "Session: $state | Queue: ${queue.countPending()} | Offline: ${queue.isOfflineSimulated()}"
            }
        }

        bindButtons()
        log("FieldCapture ready. DAT mode: MOCK_DEVICE_KIT. Gemini: ${if (gemini == null) "OFF" else BuildConfig.GEMINI_MODEL}")
    }

    private fun bindButtons() {
        findViewById<Button>(R.id.btnEnableMock).setOnClickListener { log(mock.enable()) }
        findViewById<Button>(R.id.btnPair).setOnClickListener { log(mock.pair()) }
        findViewById<Button>(R.id.btnReady).setOnClickListener {
            log(mock.restoreAfterDrop())
        }
        findViewById<Button>(R.id.btnFeed).setOnClickListener {
            if (!hasPermission(Manifest.permission.CAMERA)) {
                permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                log("Grant CAMERA, then press 4 again")
            } else log(mock.usePhoneCamera())
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            coordinator.startWalk()
            supervisor.start()
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) voice.start()
            else permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT))
        }
        findViewById<Button>(R.id.btnPause).setOnClickListener { supervisor.pause() }
        findViewById<Button>(R.id.btnResume).setOnClickListener { supervisor.resume() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            voice.stop()
            supervisor.stop()
            coordinator.finishWalk()
        }
        findViewById<Button>(R.id.btnDrop).setOnClickListener { log(mock.simulateDrop()) }
        offlineButton.setOnClickListener {
            val next = !queue.isOfflineSimulated()
            queue.simulateOffline(next)
            offlineButton.text = "Offline: ${if (next) "ON" else "OFF"}"
            log("Simulated offline = $next (captures still queue locally)")
        }
        findViewById<Button>(R.id.btnSync).setOnClickListener { coordinator.syncNow() }
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            val files = coordinator.exportJobs()
            log(if (files.isEmpty()) "No job records yet" else "Exported ${files.size} job(s) to ${files.first().parent}")
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun speak(message: String) = voice.speak(message)

    private fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logLines.appendLine("[$stamp] $message")
        if (logLines.length > 12000) logLines.delete(0, logLines.length - 12000)
        runOnUiThread { logText.text = logLines.toString() }
    }

    override fun onDestroy() {
        voice.stop()
        extractor.close()
        super.onDestroy()
    }
}
