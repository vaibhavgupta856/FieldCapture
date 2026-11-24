package com.fieldcapture.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MockLifecycleTest {
    private lateinit var kit: MockDeviceKitInterface

    @Before
    fun setUp() {
        kit = MockDeviceKit.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
        kit.enable()
    }

    @After
    fun tearDown() {
        kit.disable()
    }

    @Test
    fun enabling_mock_registers_the_app() {
        assertEquals(true, kit.isEnabled)
        runBlocking { delay(200) }
        assertEquals(
            com.meta.wearable.dat.core.types.RegistrationState.REGISTERED,
            Wearables.registrationState.value,
        )
    }

    @Test
    fun paired_device_can_be_powered_doffed_and_repaired() {
        val glasses = kit.pairGlasses(GlassesModel.RAYBAN_META)
        var device: com.meta.wearable.dat.mockdevice.api.MockGlasses? = null
        glasses.fold(onSuccess = { device = it }, onFailure = { _, _ -> })
        assertEquals(true, device != null)
        device!!.powerOn()
        device!!.unfold()
        device!!.don()
        device!!.doff()
        device!!.fold()
        device!!.powerOff()
        assertFalse(kit.pairedDevices.isEmpty())
    }

    @Test
    fun denied_permission_is_visible_to_checkPermissionStatus() = runBlocking {
        kit.permissions.set(Permission.CAMERA, PermissionStatus.Denied)
        var observed: PermissionStatus? = null
        Wearables.checkPermissionStatus(Permission.CAMERA).fold(
            onSuccess = { observed = it },
            onFailure = { _, _ -> },
        )
        assertEquals(PermissionStatus.Denied, observed)
    }
}
