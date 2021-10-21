package org.owntracks.android.location.geofencing

import android.content.Intent
import android.location.Location
import org.owntracks.android.BuildConfig.FLAVOR
import org.owntracks.android.gms.location.geofencing.intentToGeofencingEvent
import timber.log.Timber

data class GeofencingEvent(val errorCode: Int?, val geofenceTransition: Int?, val triggeringGeofences: List<Geofence>?, val triggeringLocation: Location?) {
    fun hasError(): Boolean = errorCode != null && errorCode >= 0

    companion object {
        @JvmStatic
        fun fromIntent(intent: Intent): GeofencingEvent? {
            return when (FLAVOR) {
                "gms" -> intentToGeofencingEvent(intent)
                else -> {
                    Timber.w("Decoding a geofencing event from an intent on non-GMS currently not supported")
                    null
                }
            }
        }
    }
}
