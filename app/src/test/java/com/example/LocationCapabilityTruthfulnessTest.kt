package com.example

import android.Manifest
import android.app.Application
import android.location.LocationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.service.CentralCapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocationCapabilityTruthfulnessTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun locationEnabled(enabled: Boolean) {
        shadowOf(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .setLocationEnabled(enabled)
    }
    private fun status() = CentralCapabilityRegistry(context).detectAllCapabilities()[CapabilityType.LOCATION]
    @Test fun missingPermissionRequiresPermission() {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        locationEnabled(true)
        assertEquals(CapabilityStatus.PERMISSION_REQUIRED, status())
    }
    @Test fun permissionWithLocationOffIsDisabled() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        locationEnabled(false)
        assertEquals(CapabilityStatus.DISABLED, status())
    }
    @Test fun coarsePermissionAndLocationOnIsAvailable() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        locationEnabled(true)
        assertEquals(CapabilityStatus.AVAILABLE, status())
    }
    @Test fun locationSwitchChangeRevokesAvailability() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        locationEnabled(true)
        val registry = CentralCapabilityRegistry(context)
        assertEquals(CapabilityStatus.AVAILABLE, registry.detectAllCapabilities()[CapabilityType.LOCATION])
        locationEnabled(false)
        assertEquals(CapabilityStatus.DISABLED, registry.detectAllCapabilities()[CapabilityType.LOCATION])
    }
    @Test @Config(sdk = [26]) fun prePieUsesProviderEnabledState() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        locationEnabled(false)
        assertEquals(CapabilityStatus.DISABLED, status())
        locationEnabled(true)
        assertEquals(CapabilityStatus.AVAILABLE, status())
    }
}
