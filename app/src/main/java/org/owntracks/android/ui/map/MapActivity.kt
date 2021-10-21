package org.owntracks.android.ui.map

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.commit
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.owntracks.android.BuildConfig.FLAVOR
import org.owntracks.android.R
import org.owntracks.android.data.repos.LocationRepo
import org.owntracks.android.databinding.UiMapBinding
import org.owntracks.android.geocoding.GeocoderProvider
import org.owntracks.android.gms.location.toGMSLatLng
import org.owntracks.android.location.LatLng
import org.owntracks.android.location.LocationProviderClient
import org.owntracks.android.location.LocationServices
import org.owntracks.android.location.LocationSource
import org.owntracks.android.model.FusedContact
import org.owntracks.android.perfLog
import org.owntracks.android.services.BackgroundService
import org.owntracks.android.services.BackgroundService.BACKGROUND_LOCATION_RESTRICTION_NOTIFICATION_TAG
import org.owntracks.android.services.LocationProcessor
import org.owntracks.android.services.MessageProcessorEndpointHttp
import org.owntracks.android.support.ContactImageBindingAdapter
import org.owntracks.android.support.Events.PermissionGranted
import org.owntracks.android.support.Preferences.Companion.EXPERIMENTAL_FEATURE_BEARING_ARROW_FOLLOWS_DEVICE_ORIENTATION
import org.owntracks.android.support.Preferences.Companion.EXPERIMENTAL_FEATURE_USE_OSM_MAP
import org.owntracks.android.support.RequirementsChecker
import org.owntracks.android.support.RunThingsOnOtherThreads
import org.owntracks.android.ui.base.BaseActivity
import org.owntracks.android.ui.base.navigator.Navigator
import org.owntracks.android.ui.map.osm.OSMMapFragment
import org.owntracks.android.ui.welcome.WelcomeActivity
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MapActivity : BaseActivity<UiMapBinding?, MapMvvm.ViewModel<MapMvvm.View?>?>(), MapMvvm.View,
    View.OnClickListener, View.OnLongClickListener, PopupMenu.OnMenuItemClickListener {
    lateinit var locationLifecycleObserver: LocationLifecycleObserver
    private var bottomSheetBehavior: BottomSheetBehavior<LinearLayoutCompat>? = null
    private var menu: Menu? = null
    private var locationProviderClient: LocationProviderClient? = null
    private lateinit var mapFragment: MapFragment
    private var sensorManager: SensorManager? = null
    private var orientationSensor: Sensor? = null

    internal lateinit var mapLocationSource: LocationSource

    @Inject
    lateinit var locationRepo: LocationRepo

    @Inject
    lateinit var runThingsOnOtherThreads: RunThingsOnOtherThreads

    @Inject
    lateinit var contactImageBindingAdapter: ContactImageBindingAdapter

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var geocoderProvider: GeocoderProvider

    @Inject
    lateinit var countingIdlingResource: CountingIdlingResource

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var requirementsChecker: RequirementsChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        perfLog {
            super.onCreate(savedInstanceState)
            if (!preferences.isSetupCompleted) {
                navigator.startActivity(WelcomeActivity::class.java)
                finish()
            }
            bindAndAttachContentView(R.layout.ui_map, savedInstanceState)

            binding?.also {
                setSupportToolbar(it.appbar.toolbar, false, true)
                setDrawer(it.appbar.toolbar)
                bottomSheetBehavior = BottomSheetBehavior.from(it.bottomSheetLayout)
                it.contactPeek.contactRow.setOnClickListener(this)
                it.contactPeek.contactRow.setOnLongClickListener(this)
                it.moreButton.setOnClickListener { v: View -> showPopupMenu(v) }
                setBottomSheetHidden()

                // Need to set the appbar layout behaviour to be non-drag, so that we can drag the map
                val behavior = AppBarLayout.Behavior()
                behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
                    override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                        return false
                    }
                })
            }

            locationLifecycleObserver = LocationLifecycleObserver(activityResultRegistry)
            lifecycle.addObserver(locationLifecycleObserver)

            locationProviderClient = LocationServices.getLocationProviderClient(this, preferences)
            mapLocationSource =
                MapLocationSource(locationProviderClient!!, viewModel!!.mapLocationUpdateCallback)

            if (savedInstanceState == null || supportFragmentManager.findFragmentByTag("map") == null) {
                mapFragment = getMapFragment()
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.mapFragment, mapFragment, "map")
                }
            } else {
                mapFragment = supportFragmentManager.findFragmentByTag("map") as MapFragment
            }

            // Watch various things that the viewModel owns
            viewModel?.also { vm ->
                vm.contact.observe(this, { contact: FusedContact? ->
                    contact?.let {
                        binding?.contactPeek?.run {
                            Timber.v("contact changed: $it.id")
                            name.text = it.fusedName
                            image.setImageResource(0)
                            GlobalScope.launch(Dispatchers.Main) {
                                contactImageBindingAdapter.run {
                                    image.setImageBitmap(
                                        getBitmapFromCache(it)
                                    )
                                }
                            }
                            vm.refreshGeocodeForActiveContact()
                        }
                    }
                })
                vm.bottomSheetHidden.observe(this, { o: Boolean? ->
                    if (o == null || o) {
                        setBottomSheetHidden()
                    } else {
                        setBottomSheetCollapsed()
                    }
                })
                vm.mapCenter.observe(this, { o: LatLng ->
                    mapFragment.updateCamera(o)
                })
                vm.currentLocation.observe(this, { location ->
                    if (location == null) {
                        disableLocationMenus()
                    } else {
                        enableLocationMenus()
                        binding?.vm?.run {
                            updateActiveContactDistanceAndBearing(location)
                        }
                    }
                })
            }

            Timber.d("starting BackgroundService")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, BackgroundService::class.java))
            } else {
                startService(Intent(this, BackgroundService::class.java))
            }

            // We've been started in the foreground, so cancel the background restriction notification
            NotificationManagerCompat.from(this)
                .cancel(BACKGROUND_LOCATION_RESTRICTION_NOTIFICATION_TAG, 0)
        }
    }

    private fun getMapFragment() =
        if (preferences.isExperimentalFeatureEnabled(EXPERIMENTAL_FEATURE_USE_OSM_MAP)) {
            OSMMapFragment()
        } else {
            when (FLAVOR) {
                "gms" -> GoogleMapFragment(mapLocationSource, locationRepo)
                else -> OSMMapFragment(mapLocationSource, locationRepo)
            }
        }

    internal fun checkAndRequestLocationPermissions(): Boolean {
        if (!requirementsChecker.isPermissionCheckPassed()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    val currentActivity: Activity = this
                    AlertDialog.Builder(this)
                        .setCancelable(true)
                        .setMessage(R.string.permissions_description)
                        .setPositiveButton(
                            "OK"
                        ) { _: DialogInterface?, _: Int ->
                            ActivityCompat.requestPermissions(
                                currentActivity,
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                PERMISSIONS_REQUEST_CODE
                            )
                        }
                        .show()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSIONS_REQUEST_CODE
                    )
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_CODE
                )
            }
            return false
        } else {
            return true
        }
    }

    override fun onResume() {
        if (FLAVOR == "gms") {
            if (mapFragment is GoogleMapFragment && preferences.isExperimentalFeatureEnabled(
                    EXPERIMENTAL_FEATURE_USE_OSM_MAP
                )
            ) {
                mapFragment = OSMMapFragment(mapLocationSource, locationRepo)
                supportFragmentManager.commit(true) {
                    this.replace(R.id.mapFragment, mapFragment)
                }
            } else if (mapFragment is OSMMapFragment && !preferences.isExperimentalFeatureEnabled(
                    EXPERIMENTAL_FEATURE_USE_OSM_MAP
                )
            ) {
                mapFragment = GoogleMapFragment(mapLocationSource, locationRepo)
                supportFragmentManager.commit(true) {
                    this.replace(R.id.mapFragment, mapFragment)
                }
            }
        }
        if (preferences.isExperimentalFeatureEnabled(
                EXPERIMENTAL_FEATURE_BEARING_ARROW_FOLLOWS_DEVICE_ORIENTATION
            )
        ) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager?.let {
                orientationSensor = it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                orientationSensor?.run { Timber.d("Got a rotation vector sensor") }
            }
        } else {
            sensorManager?.unregisterListener(viewModel?.orientationSensorEventListener)
            sensorManager = null
            orientationSensor = null
        }
        super.onResume()
        handleIntentExtras(intent)
        updateMonitoringModeMenu()
        viewModel?.refreshMarkers()
    }

    private fun handleIntentExtras(intent: Intent) {
        Timber.v("handleIntentExtras")
        val b = navigator.getExtrasBundle(intent)
        if (b != null) {
            Timber.v("intent has extras from drawerProvider")
            val contactId = b.getString(BUNDLE_KEY_CONTACT_ID)
            if (contactId != null) {
                viewModel!!.restore(contactId)
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.activity_map, menu)
        this.menu = menu
        updateMonitoringModeMenu()
        return true
    }

    override fun updateMonitoringModeMenu() {
        if (menu == null) {
            return
        }
        val item = menu!!.findItem(R.id.menu_monitoring)
        when (preferences.monitoring) {
            LocationProcessor.MONITORING_QUIET -> {
                item.setIcon(R.drawable.ic_baseline_stop_36)
                item.setTitle(R.string.monitoring_quiet)
            }
            LocationProcessor.MONITORING_MANUAL -> {
                item.setIcon(R.drawable.ic_baseline_pause_36)
                item.setTitle(R.string.monitoring_manual)
            }
            LocationProcessor.MONITORING_SIGNIFICANT -> {
                item.setIcon(R.drawable.ic_baseline_play_arrow_36)
                item.setTitle(R.string.monitoring_significant)
            }
            LocationProcessor.MONITORING_MOVE -> {
                item.setIcon(R.drawable.ic_step_forward_2)
                item.setTitle(R.string.monitoring_move)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_report -> {
                viewModel!!.sendLocation()
                return true
            }
            R.id.menu_mylocation -> {
                viewModel!!.onMenuCenterDeviceClicked()
                return true
            }
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.menu_monitoring -> {
                stepMonitoringModeMenu()
            }
        }
        return false
    }

    private fun stepMonitoringModeMenu() {
        preferences.setMonitoringNext()
        when (preferences.monitoring) {
            LocationProcessor.MONITORING_QUIET -> {
                Toast.makeText(this, R.string.monitoring_quiet, Toast.LENGTH_SHORT).show()
            }
            LocationProcessor.MONITORING_MANUAL -> {
                Toast.makeText(this, R.string.monitoring_manual, Toast.LENGTH_SHORT).show()
            }
            LocationProcessor.MONITORING_SIGNIFICANT -> {
                Toast.makeText(this, R.string.monitoring_significant, Toast.LENGTH_SHORT)
                    .show()
            }
            else -> {
                Toast.makeText(this, R.string.monitoring_move, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableLocationMenus() {
        menu?.run {
            findItem(R.id.menu_mylocation).setEnabled(false).icon.alpha = 128
            findItem(R.id.menu_report).setEnabled(false).icon.alpha = 128
        }
    }

    private fun enableLocationMenus() {
        menu?.run {
            findItem(R.id.menu_mylocation).setEnabled(true).icon.alpha = 255
            findItem(R.id.menu_report).setEnabled(true).icon.alpha = 255
        }
    }

    override fun clearMarkers() {
        mapFragment.clearMarkers()
    }

    override fun removeMarker(contact: FusedContact) {
        mapFragment.removeMarker(contact.id)
    }

    override fun updateMarker(contact: FusedContact) {
        if (contact.latLng == null) {
            Timber.w("unable to update marker for $contact. no location")
            return
        }
        Timber.v("updating marker for contact: %s", contact.id)
        mapFragment.updateMarker(contact.id, contact.latLng!!)
        GlobalScope.launch(Dispatchers.Main) {
            contactImageBindingAdapter.run {
                mapFragment.setMarkerImage(contact.id, getBitmapFromCache(contact))
            }
        }
    }

    fun onMarkerClicked(id: String) {
        viewModel?.onMarkerClick(id)
    }

    fun onMapClick() {
        viewModel?.onMapClick()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.menu_navigate) {
            val c = viewModel!!.contact
            c.value?.latLng?.run {
                try {
                    val l = this.toGMSLatLng()
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("google.navigation:q=${l.latitude},${l.longitude}")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Snackbar.make(
                        binding!!.coordinatorLayout,
                        getString(R.string.noNavigationApp),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } ?: run {
                Snackbar.make(
                    binding!!.coordinatorLayout,
                    getString(R.string.contactLocationUnknown),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return true
        } else if (itemId == R.id.menu_clear) {
            viewModel!!.onClearContactClicked()
            return false
        }
        return false
    }

    override fun onLongClick(view: View): Boolean {
        viewModel!!.onBottomSheetLongClick()
        return true
    }

    override fun setBottomSheetExpanded() {
        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
        orientationSensor?.let {
            sensorManager?.registerListener(viewModel?.orientationSensorEventListener, it, 500_000)
        }
    }

    // BOTTOM SHEET CALLBACKS
    override fun onClick(view: View) {
        viewModel!!.onBottomSheetClick()
    }

    override fun setBottomSheetCollapsed() {
        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
        sensorManager?.unregisterListener(viewModel?.orientationSensorEventListener)
    }

    override fun setBottomSheetHidden() {
        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
        menu?.run { close() }
        sensorManager?.unregisterListener(viewModel?.orientationSensorEventListener)
    }

    private fun showPopupMenu(v: View) {
        val popupMenu = PopupMenu(this, v, Gravity.START)
        popupMenu.menuInflater.inflate(R.menu.menu_popup_contacts, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener(this)
        if (preferences.mode == MessageProcessorEndpointHttp.MODE_ID) {
            popupMenu.menu.removeItem(R.id.menu_clear)
        }
        if (viewModel?.contactHasLocation() == null) {
            popupMenu.menu.removeItem(R.id.menu_navigate)
        }
        popupMenu.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mapFragment.locationPermissionGranted()
            eventBus.postSticky(PermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    fun onMapReady() {
        viewModel?.onMapReady()
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior == null) {
            super.onBackPressed()

        } else {
            when (bottomSheetBehavior?.state) {
                BottomSheetBehavior.STATE_HIDDEN -> super.onBackPressed()
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    setBottomSheetHidden()
                }
                BottomSheetBehavior.STATE_DRAGGING -> {
                    //Noop
                }
                BottomSheetBehavior.STATE_EXPANDED -> {
                    setBottomSheetCollapsed()
                }
                BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    setBottomSheetCollapsed()
                }
                BottomSheetBehavior.STATE_SETTLING -> {
                    //Noop
                }
            }
        }
    }

    @get:VisibleForTesting
    val locationIdlingResource: IdlingResource?
        get() = binding?.vm?.locationIdlingResource

    @get:VisibleForTesting
    val outgoingQueueIdlingResource: IdlingResource
        get() = countingIdlingResource

    companion object {
        const val BUNDLE_KEY_CONTACT_ID = "BUNDLE_KEY_CONTACT_ID"
        const val STARTING_LATITUDE = 48.856826
        const val STARTING_LONGITUDE = 2.292713
        private const val PERMISSIONS_REQUEST_CODE = 1
    }
}