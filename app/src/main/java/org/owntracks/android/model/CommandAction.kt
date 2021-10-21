package org.owntracks.android.model

import com.fasterxml.jackson.annotation.JsonValue

enum class CommandAction(val value: String) {
    /**
     * The owntracks model for command actions
     */
    REPORT_LOCATION("reportLocation"),
    SET_WAYPOINTS("setWaypoints"),
    SET_CONFIGURATION("setConfiguration"),
    RESTART("restart"),
    RECONNECT("reconnect"),
    WAYPOINTS("waypoints");

    @JsonValue
    fun getVal(): String {
        return value
    }
}