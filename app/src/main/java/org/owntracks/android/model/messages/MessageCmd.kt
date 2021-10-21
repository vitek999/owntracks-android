package org.owntracks.android.model.messages

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.owntracks.android.model.CommandAction
import org.owntracks.android.support.Preferences

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "_type")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class MessageCmd : MessageBase() {
    var action: CommandAction? = null
    var waypoints: MessageWaypoints? = null
    var configuration: MessageConfiguration? = null

    override fun isValidMessage(): Boolean {
        return super.isValidMessage() && action != null
    }

    override fun addMqttPreferences(preferences: Preferences) {
        topic = preferences.pubTopicCommands
    }

    override val baseTopicSuffix: String
        get() = BASETOPIC_SUFFIX

    companion object {
        const val TYPE = "cmd"
        private const val BASETOPIC_SUFFIX = "/cmd"
    }
}