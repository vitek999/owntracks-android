package org.owntracks.android.model.messages

import com.fasterxml.jackson.annotation.*
import org.owntracks.android.location.geofencing.Geofence
import org.owntracks.android.support.Preferences

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "_type")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class MessageTransition : MessageBase() {

    @JsonIgnore
    fun getTransition(): Int = when (event) {
        EVENT_ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
        EVENT_LEAVE -> Geofence.GEOFENCE_TRANSITION_EXIT
        else -> 0
    }

    fun setTransition(value: Int) {
        this.event = when (value) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> EVENT_ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> EVENT_LEAVE
            else -> null
        }
    }

    @JsonProperty("event")
    var event: String? = null

    @JsonProperty("desc")
    var description: String? = null

    @JsonProperty("tid")
    override var trackerId: String? = null

    @JsonProperty("t")
    var trigger: String? = null


    @JsonProperty("tst")
    var timestamp: Long = 0

    @JsonProperty("wtst")
    var waypointTimestamp: Long = 0

    @JsonProperty("acc")
    var accuracy = 0f

    @JsonProperty("lon")
    var longitude = 0.0

    @JsonProperty("lat")
    var latitude = 0.0

    override fun addMqttPreferences(preferences: Preferences) {
        topic = preferences.pubTopicEvents
        qos = preferences.pubQosEvents
        retained = preferences.pubRetainEvents
    }

    override val baseTopicSuffix: String
        get() = BASETOPIC_SUFFIX

    companion object {
        const val TYPE = "transition"
        const val TRIGGER_CIRCULAR = "c"
        const val TRIGGER_LOCATION = "l"
        private const val BASETOPIC_SUFFIX = "/event"
        private const val EVENT_ENTER = "enter"
        private const val EVENT_LEAVE = "leave"
    }
}