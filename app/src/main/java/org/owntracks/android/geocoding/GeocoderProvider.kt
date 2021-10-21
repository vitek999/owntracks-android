package org.owntracks.android.geocoding

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.withContext
import org.owntracks.android.R
import org.owntracks.android.model.messages.MessageLocation
import org.owntracks.android.perfLog
import org.owntracks.android.services.BackgroundService
import org.owntracks.android.support.Preferences
import org.owntracks.android.support.preferences.OnModeChangedPreferenceChangedListener
import org.owntracks.android.ui.map.MapActivity
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset.UTC
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeocoderProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences
) {

    private val ioDispatcher = Dispatchers.IO
    private var lastRateLimitedNotificationTime: Instant? = null
    private var notificationManager: NotificationManagerCompat
    private var geocoder: Geocoder = GeocoderNone()

    private var job: Job? = null

    private fun setGeocoderProvider(context: Context, preferences: Preferences) {
        Timber.i("Setting geocoding provider to ${preferences.reverseGeocodeProvider}")
        job = GlobalScope.launch {
            withContext(ioDispatcher) {
                perfLog {
                    geocoder = when (preferences.reverseGeocodeProvider) {
                        Preferences.REVERSE_GEOCODE_PROVIDER_OPENCAGE -> OpenCageGeocoder(
                            preferences.openCageGeocoderApiKey
                        )
                        Preferences.REVERSE_GEOCODE_PROVIDER_DEVICE -> DeviceGeocoder(context)
                        else -> GeocoderNone()
                    }
                }
            }
        }
    }

    private suspend fun geocoderResolve(messageLocation: MessageLocation): GeocodeResult {
        return withContext(ioDispatcher) {
            job?.run { join() }
            return@withContext geocoder.reverse(messageLocation.latitude, messageLocation.longitude)
        }
    }

    suspend fun resolve(messageLocation: MessageLocation) {
        if (messageLocation.hasGeocode) {
            return
        }
        Timber.d("Resolving geocode for $messageLocation")
        val result = geocoderResolve(messageLocation)
        messageLocation.geocode = geocodeResultToText(result)
        maybeCreateErrorNotification(result)
    }

    private fun maybeCreateErrorNotification(result: GeocodeResult) {
        if (result is GeocodeResult.Formatted || result is GeocodeResult.Empty || !preferences.notificationGeocoderErrors) {
            notificationManager.cancel(GEOCODE_ERROR_NOTIFICATION_TAG, 0)
            return
        }
        val errorNotificationText = when (result) {
            is GeocodeResult.Error -> context.getString(R.string.geocoderError, result.message)
            is GeocodeResult.Disabled -> context.getString(R.string.geocoderDisabled)
            is GeocodeResult.IPAddressRejected -> context.getString(R.string.geocoderIPAddressRejected)
            is GeocodeResult.RateLimited -> context.getString(
                R.string.geocoderRateLimited,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(UTC).format(result.until)
            )
            else -> ""
        }
        val until = when (result) {
            is GeocodeResult.Error -> result.until
            is GeocodeResult.Disabled -> result.until
            is GeocodeResult.IPAddressRejected -> result.until
            is GeocodeResult.RateLimited -> result.until
            else -> Instant.MIN
        }

        if (until == lastRateLimitedNotificationTime) {
            return
        } else {
            lastRateLimitedNotificationTime = until
        }

        val activityLaunchIntent = Intent(this.context, MapActivity::class.java)
        activityLaunchIntent.action = "android.intent.action.MAIN"
        activityLaunchIntent.addCategory("android.intent.category.LAUNCHER")
        activityLaunchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val notification = NotificationCompat.Builder(context, ERROR_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.geocoderProblemNotificationTitle))
            .setContentText(errorNotificationText)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_owntracks_80)
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorNotificationText))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    activityLaunchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setPriority(PRIORITY_LOW)
            .setSilent(true)
            .build()

        notificationManager.notify(GEOCODE_ERROR_NOTIFICATION_TAG, 0, notification)
    }

    private fun geocodeResultToText(result: GeocodeResult) =
        when (result) {
            is GeocodeResult.Formatted -> result.text
            else -> null
        }

    fun resolve(messageLocation: MessageLocation, backgroundService: BackgroundService) {
        if (messageLocation.hasGeocode) {
            backgroundService.onGeocodingProviderResult(messageLocation)
            return
        }
        MainScope().launch {
            val result = geocoderResolve(messageLocation)
            messageLocation.geocode = geocodeResultToText(result)
            backgroundService.onGeocodingProviderResult(messageLocation)
            maybeCreateErrorNotification(result)
        }
    }

    init {
        setGeocoderProvider(context, preferences)
        preferences.registerOnPreferenceChangedListener(object :
            OnModeChangedPreferenceChangedListener {
            override fun onAttachAfterModeChanged() {

            }

            override fun onSharedPreferenceChanged(
                sharedPreferences: SharedPreferences?,
                key: String?
            ) {
                if (key == preferences.getPreferenceKey(R.string.preferenceKeyReverseGeocodeProvider) || key == preferences.getPreferenceKey(
                        R.string.preferenceKeyOpencageGeocoderApiKey
                    )
                ) {
                    setGeocoderProvider(context, preferences)
                }
            }
        })
        notificationManager = NotificationManagerCompat.from(context)
    }

    companion object {
        const val ERROR_NOTIFICATION_CHANNEL_ID = "Errors"
        const val GEOCODE_ERROR_NOTIFICATION_TAG = "GeocoderError"
    }
}

