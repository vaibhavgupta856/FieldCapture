package com.fieldcapture.app.dat

import android.content.Context
import android.net.Uri
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing

/** Debug surface for the simulated glasses. Nothing here exists in a production build path. */
class MockDeviceController(context: Context) {
    private val kit: MockDeviceKitInterface = MockDeviceKit.getInstance(context)
    private var glasses: MockGlasses? = null

    fun enable(): String {
        kit.enable()
        return "Mock Device Kit enabled (registration + permissions simulated)"
    }

    fun pair(): String = kit.pairGlasses(GlassesModel.RAYBAN_META).fold(
        onSuccess = { device ->
            glasses = device
            "Paired ${GlassesModel.RAYBAN_META}"
        },
        onFailure = { error, _ -> "Pair failed: ${error.description}" },
    )

    fun makeReady(): String {
        val g = glasses ?: return "Pair a mock device first"
        g.powerOn()
        g.unfold()
        g.don()
        return "Mock glasses powered on, unfolded, donned"
    }

    fun usePhoneCamera(): String {
        val g = glasses ?: return "Pair a mock device first"
        g.services.camera.setCameraFeed(CameraFacing.BACK)
        return "Mock feed = phone back camera"
    }

    fun useVideo(uri: Uri): String {
        val g = glasses ?: return "Pair a mock device first"
        g.services.camera.setCameraFeed(uri)
        return "Mock feed = video file (must be h.265 on Android)"
    }

    fun simulateDrop(): String {
        val g = glasses ?: return "Pair a mock device first"
        g.doff()
        g.fold()
        g.powerOff()
        return "Simulated Bluetooth drop: doff + fold + power off"
    }

    fun restoreAfterDrop(): String {
        val g = glasses ?: return "Pair a mock device first"
        g.powerOn()
        g.unfold()
        g.don()
        return "Mock glasses back online"
    }

    fun denyCameraPermission() {
        kit.permissions.setRequestResult(Permission.CAMERA, PermissionStatus.Denied)
        kit.permissions.set(Permission.CAMERA, PermissionStatus.Denied)
    }

    fun grantCameraPermission() {
        kit.permissions.set(Permission.CAMERA, PermissionStatus.Granted)
        kit.permissions.setRequestResult(Permission.CAMERA, PermissionStatus.Granted)
    }

    fun requireFirmwareUpdate(enabled: Boolean) {
        glasses?.services?.setFirmwareUpdateRequirementEnabled(enabled)
    }

    fun tap() = glasses?.services?.captouch?.tap()

    fun tapAndHold() = glasses?.services?.captouch?.tapAndHold()
}
