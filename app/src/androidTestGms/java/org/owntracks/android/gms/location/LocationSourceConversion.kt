package org.owntracks.android.gms.location

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.owntracks.android.location.LocationSource

@RunWith(AndroidJUnit4::class)
@SmallTest
class LocationSourceConversion {

    @Test
    fun canConvertLocationSourceToGMS() {
        var activateCalled = false
        var gmsActivateCalled = false
        var deactivateCalled = false
        val locationSource = object : LocationSource {
            override fun activate(onLocationChangedListener: LocationSource.OnLocationChangedListener) {
                activateCalled = true
                onLocationChangedListener.onLocationChanged(Location("test"))
            }

            override fun reactivate() {
            }

            override fun deactivate() {
                deactivateCalled = true
            }

            override fun getLastKnownLocation(): Location? = null
        }

        val gmsLocationSource = locationSource.toGMSLocationSource()
        gmsLocationSource.activate { gmsActivateCalled = true }
        gmsLocationSource.deactivate()
        assertTrue("Activate was called", activateCalled)
        assertTrue("GMS activation was called", gmsActivateCalled)
        assertTrue("Deactivate called", deactivateCalled)
    }
}