package org.owntracks.android.gms.location.geofencing

import android.content.Intent
import com.google.android.gms.location.GeofencingEvent

fun GeofencingEvent.toOTGeofencingEvent(): org.owntracks.android.location.geofencing.GeofencingEvent {
    return org.owntracks.android.location.geofencing.GeofencingEvent(this.errorCode, this.geofenceTransition, emptyList(), this.triggeringLocation)
}

fun intentToGeofencingEvent(intent: Intent): org.owntracks.android.location.geofencing.GeofencingEvent {
    return GeofencingEvent.fromIntent(intent).toOTGeofencingEvent()
}