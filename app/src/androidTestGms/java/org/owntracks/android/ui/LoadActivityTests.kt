package org.owntracks.android.ui

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertContains
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
import com.adevinta.android.barista.assertion.BaristaVisibilityAssertions.assertNotExist
import com.adevinta.android.barista.interaction.BaristaSleepInteractions.sleep
import com.adevinta.android.barista.interaction.PermissionGranter.allowPermissionsIfNeeded
import com.adevinta.android.barista.rule.BaristaRule
import com.adevinta.android.barista.rule.flaky.AllowFlaky
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.owntracks.android.R
import org.owntracks.android.ScreenshotTakingOnTestEndRule
import org.owntracks.android.ui.preferences.load.LoadActivity
import java.io.File
import java.io.FileWriter

@LargeTest
@RunWith(AndroidJUnit4::class)
class LoadActivityTests {
    @get:Rule
    var baristaRule = BaristaRule.create(LoadActivity::class.java)

    private val screenshotRule = ScreenshotTakingOnTestEndRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(baristaRule.activityTestRule)
        .around(screenshotRule)

    private var mockWebServer = MockWebServer()

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }


    private val expectedConfig = """
{
  "_type" : "configuration",
  "waypoints" : [ {
    "_type" : "waypoint",
    "desc" : "work",
    "lat" : 51.5,
    "lon" : -0.02,
    "rad" : 150,
    "tst" : 1505910709000
  }, {
    "_type" : "waypoint",
    "desc" : "home",
    "lat" : 53.6,
    "lon" : -1.5,
    "rad" : 100,
    "tst" : 1558351273
  } ],
  "auth" : true,
  "autostartOnBoot" : true,
  "cleanSession" : false,
  "clientId" : "emulator",
  "cmd" : true,
  "connectionTimeoutSeconds" : 34,
  "debugLog" : true,
  "deviceId" : "testdevice",
  "fusedRegionDetection" : true,
  "geocodeEnabled" : true,
  "host" : "testhost.example.com",
  "ignoreInaccurateLocations" : 150,
  "ignoreStaleLocations" : 0,
  "keepalive" : 900,
  "locatorDisplacement" : 5,
  "locatorInterval" : 60,
  "locatorPriority" : 2,
  "mode" : 0,
  "monitoring" : 1,
  "moveModeLocatorInterval" : 10,
  "mqttProtocolLevel" : 3,
  "notificationHigherPriority" : false,
  "notificationLocation" : true,
  "opencageApiKey" : "",
  "password" : "password",
  "ping" : 30,
  "port" : 1883,
  "pubExtendedData" : true,
  "pubQos" : 1,
  "pubRetain" : true,
  "pubTopicBase" : "owntracks/%u/%d",
  "remoteConfiguration" : true,
  "sub" : true,
  "subQos" : 2,
  "subTopic" : "owntracks/+/+",
  "tls" : false,
  "usePassword" : true,
  "username" : "username",
  "ws" : false
}""".trimIndent()

    private val servedConfig =
        "{\"_type\":\"configuration\",\"waypoints\":[{\"_type\":\"waypoint\",\"desc\":\"work\",\"lat\":51.5,\"lon\":-0.02,\"rad\":150,\"tst\":1505910709000},{\"_type\":\"waypoint\",\"desc\":\"home\",\"lat\":53.6,\"lon\":-1.5,\"rad\":100,\"tst\":1558351273}],\"auth\":true,\"autostartOnBoot\":true,\"connectionTimeoutSeconds\":34,\"cleanSession\":false,\"clientId\":\"emulator\",\"cmd\":true,\"debugLog\":true,\"deviceId\":\"testdevice\",\"fusedRegionDetection\":true,\"geocodeEnabled\":true,\"host\":\"testhost.example.com\",\"ignoreInaccurateLocations\":150,\"ignoreStaleLocations\":0,\"keepalive\":900,\"locatorDisplacement\":5,\"locatorInterval\":60,\"locatorPriority\":2,\"mode\":0,\"monitoring\":1,\"moveModeLocatorInterval\":10,\"mqttProtocolLevel\":3,\"notificationHigherPriority\":false,\"notificationLocation\":true,\"opencageApiKey\":\"\",\"password\":\"password\",\"ping\":30,\"port\":1883,\"pubExtendedData\":true,\"pubQos\":1,\"pubRetain\":true,\"pubTopicBase\":\"owntracks/%u/%d\",\"remoteConfiguration\":true,\"sub\":true,\"subQos\":2,\"subTopic\":\"owntracks/+/+\",\"tls\":false,\"usePassword\":true,\"username\":\"username\",\"ws\":false}"

    @Test
    @AllowFlaky
    fun loadActivityCanLoadConfigFromOwntracksInlineConfigURL() {
        baristaRule.launchActivity(
            Intent(
                Intent.ACTION_VIEW, Uri.parse(
                    "owntracks:///config?inline=eyJfdHlwZSI6ImNvbmZpZ3VyYXRpb24iLCJ3YXlwb2ludHMiOlt7Il90eXBlIjoid2F5cG9pbnQiLCJkZXNjIjoid29yayIsImxhdCI6NTEuNSwibG9uIjotMC4wMiwicmFkIjoxNTAsInRzdCI6MTUwNTkxMDcwOTAwMH0seyJfdHlwZSI6IndheXBvaW50IiwiZGVzYyI6ImhvbWUiLCJsYXQiOjUzLjYsImxvbiI6LTEuNSwicmFkIjoxMDAsInRzdCI6MTU1ODM1MTI3M31dLCJhdXRoIjp0cnVlLCJhdXRvc3RhcnRPbkJvb3QiOnRydWUsImNvbm5lY3Rpb25UaW1lb3V0U2Vjb25kcyI6MzQsImNsZWFuU2Vzc2lvbiI6ZmFsc2UsImNsaWVudElkIjoiZW11bGF0b3IiLCJjbWQiOnRydWUsImRlYnVnTG9nIjp0cnVlLCJkZXZpY2VJZCI6InRlc3RkZXZpY2UiLCJmdXNlZFJlZ2lvbkRldGVjdGlvbiI6dHJ1ZSwiZ2VvY29kZUVuYWJsZWQiOnRydWUsImhvc3QiOiJ0ZXN0aG9zdC5leGFtcGxlLmNvbSIsImlnbm9yZUluYWNjdXJhdGVMb2NhdGlvbnMiOjE1MCwiaWdub3JlU3RhbGVMb2NhdGlvbnMiOjAsImtlZXBhbGl2ZSI6OTAwLCJsb2NhdG9yRGlzcGxhY2VtZW50Ijo1LCJsb2NhdG9ySW50ZXJ2YWwiOjYwLCJsb2NhdG9yUHJpb3JpdHkiOjIsIm1vZGUiOjAsIm1vbml0b3JpbmciOjEsIm1vdmVNb2RlTG9jYXRvckludGVydmFsIjoxMCwibXF0dFByb3RvY29sTGV2ZWwiOjMsIm5vdGlmaWNhdGlvbkhpZ2hlclByaW9yaXR5IjpmYWxzZSwibm90aWZpY2F0aW9uTG9jYXRpb24iOnRydWUsIm9wZW5jYWdlQXBpS2V5IjoiIiwicGFzc3dvcmQiOiJwYXNzd29yZCIsInBpbmciOjMwLCJwb3J0IjoxODgzLCJwdWJFeHRlbmRlZERhdGEiOnRydWUsInB1YlFvcyI6MSwicHViUmV0YWluIjp0cnVlLCJwdWJUb3BpY0Jhc2UiOiJvd250cmFja3MvJXUvJWQiLCJyZW1vdGVDb25maWd1cmF0aW9uIjp0cnVlLCJzdWIiOnRydWUsInN1YlFvcyI6Miwic3ViVG9waWMiOiJvd250cmFja3MvKy8rIiwidGxzIjpmYWxzZSwidXNlUGFzc3dvcmQiOnRydWUsInVzZXJuYW1lIjoidXNlcm5hbWUiLCJ3cyI6ZmFsc2V9Cg=="
                )
            )
        )
        assertContains(R.id.effectiveConfiguration, expectedConfig)
        assertDisplayed(R.id.save)
        assertDisplayed(R.id.close)
    }

    @Test
    @AllowFlaky
    fun loadActivityShowsErrorWhenLoadingFromInlineConfigURLContaninigInvalidJSON() {
        baristaRule.launchActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("owntracks:///config?inline=e30k")
            )
        )
        assertContains(R.id.effectiveConfiguration, R.string.errorPreferencesImportFailed)
        assertNotExist(R.id.save)
        assertDisplayed(R.id.close)

    }

    @Test
    @AllowFlaky
    fun loadActivityShowsErrorWhenLoadingFromInlineConfigURLContaninigInvalidBase64() {
        baristaRule.launchActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("owntracks:///config?inline=aaaaaaaaaaaaaaaaaaaaaaaaa")
            )
        )
        assertContains(R.id.effectiveConfiguration, R.string.errorPreferencesImportFailed)
        assertNotExist(R.id.save)
        assertDisplayed(R.id.close)
    }

    @Test
    @AllowFlaky
    fun loadActivityCanLoadConfigFromOwntracksRemoteURL() {
        mockWebServer.start(8080)
        mockWebServer.dispatcher = MockWebserverConfigDispatcher(servedConfig)

        baristaRule.launchActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("owntracks:///config?url=http%3A%2F%2Flocalhost%3A8080%2Fmyconfig.otrc")
            )
        )
        sleep(1000)
        assertContains(R.id.effectiveConfiguration, expectedConfig)
        assertDisplayed(R.id.save)
        assertDisplayed(R.id.close)
    }

    @Test
    @AllowFlaky
    fun loadActivityShowsErrorTryingToLoadNotFoundRemoteUrl() {
        mockWebServer.start(8080)
        mockWebServer.dispatcher = MockWebserverConfigDispatcher(servedConfig)

        baristaRule.launchActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("owntracks:///config?url=http%3A%2F%2Flocalhost%3A8080%2Fnotfound")
            )
        )
        sleep(1000)
        assertContains(R.id.effectiveConfiguration, "Unexpected status code")
        assertNotExist(R.id.save)
        assertDisplayed(R.id.close)
    }

    @Test
    fun loadActivityCanLoadConfigFromFileURL() {
        val dir =
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val localConfig = File(dir, "espresso-testconfig.otrc")
        localConfig.writeText(servedConfig)
        baristaRule.launchActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("file://${localConfig.absoluteFile}")
            )
        )
        assertContains(R.id.effectiveConfiguration, expectedConfig)
        assertDisplayed(R.id.save)
        assertDisplayed(R.id.close)
    }

    @Test
    @AllowFlaky
    fun loadActivityCanLoadConfigFromContentURL() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configFilename = "espresso-testconfig.otrc"
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.delete(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(configFilename)
            )
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, configFilename)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val contentUri = context.contentResolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues
            )
            contentUri?.let {
                context.contentResolver.openFileDescriptor(it, "w").use { parcelFileDescriptor ->
                    ParcelFileDescriptor.AutoCloseOutputStream(parcelFileDescriptor)
                        .write(servedConfig.toByteArray())
                }
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(it, contentValues, null, null)
            }
            baristaRule.launchActivity(Intent(Intent.ACTION_VIEW, contentUri))
        } else {
            allowPermissionsIfNeeded(WRITE_EXTERNAL_STORAGE)
            @Suppress("DEPRECATION")
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val configFile = downloadsDir.resolve(configFilename)
            FileWriter(configFile).use {
                it.write(servedConfig)
            }
            baristaRule.launchActivity(Intent(Intent.ACTION_VIEW, Uri.fromFile(configFile)))
        }

        assertContains(R.id.effectiveConfiguration, expectedConfig)
        assertDisplayed(R.id.save)
        assertDisplayed(R.id.close)
    }

    @Test
    @AllowFlaky
    fun loadActivityErrorsCorrectlyFromInvalidContentURL() {
        baristaRule.launchActivity(Intent(Intent.ACTION_VIEW, null))
        assertContains(
            R.id.effectiveConfiguration,
            "Import failed: No URI given for importing configuration"
        )
        assertNotExist(R.id.save)
        assertDisplayed(R.id.close)
    }

    class MockWebserverConfigDispatcher(private val config: String) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val errorResponse = MockResponse().setResponseCode(404)
            return if (request.path == "/myconfig.otrc") {
                MockResponse().setResponseCode(200).setHeader("Content-type", "application/json")
                    .setBody(config)
            } else {
                errorResponse
            }
        }
    }
}