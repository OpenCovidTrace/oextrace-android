package org.openexposuretrace.oextrace

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.openexposuretrace.oextrace.OnboardingActivity.Extra.STAGE_EXTRA
import org.openexposuretrace.oextrace.data.*
import org.openexposuretrace.oextrace.data.Enums.*
import org.openexposuretrace.oextrace.di.BluetoothManagerProvider
import org.openexposuretrace.oextrace.di.api.ApiClientProvider
import org.openexposuretrace.oextrace.di.api.ContactsApiClientProvider
import org.openexposuretrace.oextrace.ext.access.withPermissions
import org.openexposuretrace.oextrace.ext.ifAllNotNull
import org.openexposuretrace.oextrace.ext.text.dateFullFormat
import org.openexposuretrace.oextrace.ext.ui.showError
import org.openexposuretrace.oextrace.ext.ui.showInfo
import org.openexposuretrace.oextrace.location.LocationAccessManager
import org.openexposuretrace.oextrace.location.LocationUpdateManager
import org.openexposuretrace.oextrace.service.BleUpdatesService
import org.openexposuretrace.oextrace.service.TrackingService
import org.openexposuretrace.oextrace.storage.*
import org.openexposuretrace.oextrace.ui.contacts.ContactsFragment
import org.openexposuretrace.oextrace.ui.logs.LogsFragment
import org.openexposuretrace.oextrace.ui.qrcode.QrCodeFragment
import org.openexposuretrace.oextrace.ui.settings.SettingsFragment
import org.openexposuretrace.oextrace.utils.CryptoUtil
import org.openexposuretrace.oextrace.utils.CryptoUtil.base64DecodeByteArray
import org.openexposuretrace.oextrace.utils.CryptoUtil.base64EncodedString
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val REQUEST_NONE = 0
        const val REQUEST_LOCATION = 1
        const val REQUEST_CHECK_TRACKING_SETTINGS = 2
        private const val REQUEST_BLUETOOTH = 3
    }

    // Tracks the bound state of the service.
    private val deviceManager by BluetoothManagerProvider()
    private var bluetoothAlert: AlertDialog.Builder? = null
    private val contactsApiClient by ContactsApiClientProvider()
    private val apiClient by ApiClientProvider()

    private var googleMap: GoogleMap? = null

    private var mkContactPoints = mutableListOf<Marker>()

    var userPolylines = mutableListOf<Polyline>()
    var sickPolylines = mutableListOf<Polyline>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync(this)

        EventBus.getDefault().register(this)
        logsButton.setOnClickListener { showLogs() }
        recordContactButton.setOnClickListener { showQrCode() }
        contactsButton.setOnClickListener { showContacts() }
        settingsButton.setOnClickListener { showSettings() }

        zoomInButton.setOnClickListener {
            googleMap?.let { map ->
                map.animateCamera(
                    CameraUpdateFactory.zoomTo((map.cameraPosition.zoom.roundToInt() + 1).toFloat())
                )
            }
        }

        zoomOutButton.setOnClickListener {
            googleMap?.let { map ->
                map.animateCamera(
                    CameraUpdateFactory.zoomTo((map.cameraPosition.zoom.roundToInt() - 1).toFloat())
                )
            }
        }

        myLocationButton.setOnClickListener {
            LocationUpdateManager.getLastLocation()?.let { location ->
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLng(LatLng(location.latitude, location.longitude))
                )
            }
        }

        if (!OnboardingManager.isComplete()) {
            val intent = Intent(this, OnboardingActivity::class.java)

            intent.putExtra(STAGE_EXTRA, OnboardingStage.WELCOME)

            startActivity(intent)

            return
        }

        handleDeepLink()
    }

    override fun onDestroy() {
        super.onDestroy()

        mapView.onDestroy()

        stopBleService()
        stopTrackingService()

        EventBus.getDefault().unregister(this)
    }

    private fun handleDeepLink() {
        val data: Uri? = intent.data
        if (data != null && data.isHierarchical) {
            val uri = Uri.parse(intent.dataString)
            val rpi = uri.getQueryParameter("r")
            val key = uri.getQueryParameter("k")
            val token = uri.getQueryParameter("d")
            val platform = uri.getQueryParameter("p")
            val tst = uri.getQueryParameter("t")?.toLongOrNull()
            ifAllNotNull(rpi, key, token, platform, tst, ::makeContact)
        }
    }

    override fun onStart() {
        super.onStart()

        mapView.onStart()

        if (OnboardingManager.isComplete()) {
            requestEnableTracking()

            Log.i("DATA", "Cleaning old data...")

            BtContactsManager.removeOldContacts()
            QrContactsManager.removeOldContacts()
            TracksManager.removeOldTracks()
            TrackingManager.removeOldPoints()
            LocationBordersManager.removeOldLocationBorders()
            EncryptionKeysManager.removeOldKeys()

            if (UserSettingsManager.sick()) {
                KeysManager.uploadNewKeys()
            }

            updateUserTracks()

            LocationUpdateManager.registerCallback { location ->
                loadTracks(location)
                loadDiagnosticKeys(location)
            }
        }
    }

    override fun onStop() {
        super.onStop()

        mapView.onStop()
    }

    override fun onResume() {
        super.onResume()

        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()

        mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()

        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        mapView.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startOrUpdateTrackingService()
                    startSearchDevices()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_BLUETOOTH)
                startBleService()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isIndoorLevelPickerEnabled = false
        map.uiSettings.isCompassEnabled = true

        LocationUpdateManager.registerCallback { location ->
            runOnUiThread {
                map.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(14f)
                            .build()
                    )
                )
                map.isMyLocationEnabled = true
            }
        }

        updateExtTracks()
        updateContacts()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUpdateUserTracksEvent(event: UpdateUserTracksEvent) {
        updateUserTracks()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUpdateUserTracksEvent(event: UpdateLocationAccuracyEvent) {
        accuracyText.text = getString(R.string.accuracy_text, event.accuracy)
        accuracyText.visibility = View.VISIBLE
    }

    private fun updateUserTracks() {
        println("Updating user tracks...")

        val polylines = makePolylines(TrackingManager.getTrackingData())

        println("Got ${polylines.size} user polylines.")

        userPolylines.forEach { it.remove() }
        userPolylines.clear()
        polylines.forEach {
            googleMap?.addPolyline(
                PolylineOptions()
                    .clickable(true)
                    .addAll(it)
            )?.let { polyline ->
                polyline.color = ContextCompat.getColor(this, R.color.colorPrimary)
                userPolylines.add(polyline)
            }
        }
    }

    private fun updateExtTracks() {
        println("Updating external tracks...")

        val sickPolylines: MutableList<List<LatLng>> = mutableListOf()

        TracksManager.getTracks().forEach { track ->
            val trackPolylines = makePolylines(track.points)
            sickPolylines.addAll(trackPolylines)
        }

        println("Got ${sickPolylines.size} sick polylines.")

        val now = System.currentTimeMillis()

        this.sickPolylines.forEach { it.remove() }
        this.sickPolylines.clear()
        sickPolylines.forEach {
            googleMap?.addPolyline(
                PolylineOptions()
                    .clickable(true)
                    .addAll(it)
            )?.let { polyline ->
                polyline.color = ContextCompat.getColor(this, R.color.red)
                this.sickPolylines.add(polyline)
            }
        }

        val renderTime = System.currentTimeMillis() - now

        print("Rendered ${sickPolylines.size} sick polylines in $renderTime ms.")

        // So that user tracks are always above
        updateUserTracks()
    }

    private fun makePolylines(points: List<TrackingPoint>): List<List<LatLng>> {
        val polylines: MutableList<List<LatLng>> = mutableListOf()
        var lastPolyline = mutableListOf<LatLng>()
        var lastTimestamp = 0L

        fun addPolyline() {
            if (lastPolyline.size == 1) {
                // Each polyline should have at least 2 points
                lastPolyline.add(lastPolyline.first())
            }

            polylines.add(lastPolyline)
        }

        points.forEach { point ->
            val timestamp = point.tst
            val coordinate = point.coordinate()

            when {
                lastTimestamp == 0L -> {
                    lastPolyline = arrayListOf(coordinate)
                }
                timestamp - lastTimestamp > TrackingManager.trackingIntervalMs * 2 -> {
                    addPolyline()

                    lastPolyline = arrayListOf(coordinate)
                }
                else -> {
                    lastPolyline.add(coordinate)
                }
            }

            lastTimestamp = timestamp
        }

        addPolyline()

        return polylines
    }

    // TODO replace with LiveData
    fun updateContacts() {
        mkContactPoints.forEach { it.remove() }
        mkContactPoints.clear()

        BtContactsManager.contacts.values.forEach { contact ->
            contact.encounters.forEach { encounter ->
                ifAllNotNull(encounter.metaData, encounter.metaData?.coord) { metaData, coord ->
                    googleMap?.addMarker(
                        MarkerOptions().position(coord.coordinate())
                            .title(
                                getString(
                                    R.string.contact_at_date,
                                    metaData.date.dateFullFormat()
                                )
                            )
                    )?.let {
                        mkContactPoints.add(it)
                    }
                }
            }
        }

        QrContactsManager.contacts.forEach { contact ->
            ifAllNotNull(contact.metaData, contact.metaData?.coord) { metaData, coord ->
                googleMap?.addMarker(
                    MarkerOptions().position(coord.coordinate())
                        .title(getString(R.string.contact_at_date, metaData.date.dateFullFormat()))
                )?.let {
                    mkContactPoints.add(it)
                }
            }
        }
    }

    private fun loadTracks(location: Location) {
        val index = LocationIndex(location)
        val lastUpdateTimestamp = LocationIndexManager.getTracksIndex()[index] ?: 0
        val border = LocationBordersManager.LocationBorder.fetchLocationBorderByIndex(index)

        apiClient.fetchTracks(
            lastUpdateTimestamp,
            border.minLat,
            border.maxLat,
            border.minLng,
            border.maxLng
        ).enqueue(object : Callback<TracksData> {
            override fun onResponse(call: Call<TracksData>, response: Response<TracksData>) {
                response.body()?.tracks?.let { tracks ->
                    LocationIndexManager.updateTracksIndex(index)

                    if (tracks.isEmpty()) {
                        return
                    }

                    val latestSecretDailyKeys = CryptoUtil.getLatestSecretDailyKeys()

                    val tracksFiltered = tracks.filter { !latestSecretDailyKeys.contains(it.key) }

                    Log.i(
                        "TRACKS",
                        "Got ${tracksFiltered.size} new tracks since $lastUpdateTimestamp for ${border}."
                    )

                    if (tracksFiltered.isEmpty()) {
                        return
                    }

                    TracksManager.addTracks(tracksFiltered)

                    runOnUiThread {
                        updateExtTracks()
                    }
                }
            }

            override fun onFailure(call: Call<TracksData>, t: Throwable) {
                Log.e("TRACKS", "ERROR: ${t.message}", t)
            }
        })
    }

    private fun loadDiagnosticKeys(location: Location) {
        val index = LocationIndex(location)
        val lastUpdateTimestamp = LocationIndexManager.getKeysIndex()[index] ?: 0
        val border = LocationBordersManager.LocationBorder.fetchLocationBorderByIndex(index)

        apiClient.fetchKeys(
            lastUpdateTimestamp,
            border.minLat,
            border.maxLat,
            border.minLng,
            border.maxLng
        ).enqueue(object : Callback<KeysData> {
            override fun onResponse(call: Call<KeysData>, response: Response<KeysData>) {
                response.body()?.let { data ->
                    LocationIndexManager.updateKeysIndex(index)

                    if (data.keys.isNullOrEmpty()) {
                        return
                    }

                    val qrContacts = QrContactsManager.matchContacts(data)
                    val btContacts = BtContactsManager.matchContacts(data)

                    val hasQrExposure = qrContacts.first
                    val hasBtExposure = btContacts.first

                    if (hasQrExposure || hasBtExposure) {
                        showExposedNotification()
                    }

                    qrContacts.second?.let {
                        goToContact(it)
                        updateContacts()
                    } ?: btContacts.second?.let {
                        goToContact(it)
                        updateContacts()
                    }
                }
            }

            override fun onFailure(call: Call<KeysData>, t: Throwable) {
                Log.e("TRACKS", "ERROR: ${t.message}", t)
            }
        })
    }

    private fun showExposedNotification() {
        showInfo(R.string.exposed_contact_message)
    }

    fun goToContact(coord: ContactCoord) {
        goToLocation(LatLng(coord.lat, coord.lng))
    }

    private fun goToLocation(latLng: LatLng) {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
    }

    private fun showLogs() {
        val dialog = LogsFragment()
        dialog.show(supportFragmentManager, dialog.tag)
    }

    private fun showQrCode() {
        val dialog = QrCodeFragment()
        dialog.show(supportFragmentManager, dialog.tag)
    }

    private fun showContacts() {
        val dialog = ContactsFragment()
        dialog.show(supportFragmentManager, dialog.tag)
    }

    private fun showSettings() {
        val dialog = SettingsFragment()
        dialog.show(supportFragmentManager, dialog.tag)
    }

    private fun enableTracking() {
        if (LocationAccessManager.authorized(this)) {
            startOrUpdateTrackingService()
            startBleService()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION
            )
        }
    }

    private fun requestEnableTracking() {
        checkLocationSettings(
            TrackingService.TRACKING_LOCATION_REQUEST_BUILDER,
            Runnable { this.enableTracking() },
            Runnable {
                Toast.makeText(this, R.string.location_disabled, LENGTH_LONG).show()
            }
        )
    }

    /**
     * This method is about location usage device-wide, not permission of the app!
     */
    private fun checkLocationSettings(
        requestBuilder: LocationSettingsRequest.Builder,
        onSuccess: Runnable,
        onFailure: Runnable?
    ) {
        val client = LocationServices.getSettingsClient(this)
        val task =
            client.checkLocationSettings(requestBuilder.build())
        task.addOnSuccessListener(this) { onSuccess.run() }
        task.addOnFailureListener(this) { e ->
            if (e is ResolvableApiException) {
                // StaticLocation settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(this@MainActivity, REQUEST_CHECK_TRACKING_SETTINGS)
                } catch (sendEx: SendIntentException) {
                    // Ignore the error.
                }
            } else {
                onFailure?.run()
            }
        }
    }

    private fun stopBleService() {
        stopService(Intent(this, BleUpdatesService::class.java))
    }

    private fun startOrUpdateTrackingService() {
        startService(Intent(this, TrackingService::class.java))
    }

    private fun stopTrackingService() {
        stopService(Intent(this, TrackingService::class.java))
    }

    private fun startSearchDevices() =
        withPermissions(arrayOf(permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION) {
            val locationManager =
                getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsEnabled =
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ?: false
            if (gpsEnabled) {
                when (checkBluetooth()) {
                    ENABLED -> startService(Intent(this, BleUpdatesService::class.java))
                    DISABLED -> showBluetoothDisabledError()
                    NOT_FOUND -> showBluetoothNotFoundError()
                }
            }
        }

    private fun checkBluetooth(): Enums = deviceManager.checkBluetooth()

    private fun startBleService() {
        when (checkBluetooth()) {
            ENABLED -> startSearchDevices()
            DISABLED -> showBluetoothDisabledError()
            NOT_FOUND -> showBluetoothNotFoundError()
        }
    }

    private fun showBluetoothDisabledError() {
        if (bluetoothAlert == null)
            bluetoothAlert = AlertDialog.Builder(this).apply {
                setTitle(R.string.bluetooth_turn_off)
                setMessage(R.string.bluetooth_turn_off_description)
                setPositiveButton(R.string.enable) { _, _ ->
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_BLUETOOTH)
                    bluetoothAlert = null
                }
                setOnCancelListener { bluetoothAlert = null }
                show()
            }
    }

    private fun showBluetoothNotFoundError() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.bluetooth_do_not_support)
            setMessage(R.string.bluetooth_do_not_support_description)
            setCancelable(false)
            setNegativeButton(R.string.done) { _, _ -> }
            show()
        }
    }

    private fun makeContact(rpi: String, key: String, token: String, platform: String, tst: Long) {
        if (abs(System.currentTimeMillis() - tst) > (60 * 1000)) {
            // QR contact should be valid for 1 minute only
            showError(R.string.code_has_expired)

            return
        }

        val (rollingId, meta) = CryptoUtil.getCurrentRollingIdAndMeta()

        val keyData = key.base64DecodeByteArray()
        var secretData = CryptoUtil.encodeAES(rollingId, keyData)
        secretData += CryptoUtil.encodeAES(meta, keyData)

        val contactRequest = ContactRequest(token, platform, secretData.base64EncodedString(), tst)

        contactsApiClient.sendContactRequest(contactRequest)
            .enqueue(object : Callback<Void> {

                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    val contact = QrContact.create(rpi)

                    QrContactsManager.addContact(contact)
                    showInfo(R.string.recorded_contact)
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    t.message?.let { showError(it) }
                }

            })
    }

}
