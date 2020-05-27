package org.openexposuretrace.oextrace.service

import android.Manifest
import android.app.*
import android.app.NotificationManager.IMPORTANCE_LOW
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.openexposuretrace.oextrace.MainActivity
import org.openexposuretrace.oextrace.R
import org.openexposuretrace.oextrace.data.Enums
import org.openexposuretrace.oextrace.data.SCAN_TAG
import org.openexposuretrace.oextrace.di.BluetoothManagerProvider
import org.openexposuretrace.oextrace.ext.access.isNotGranted
import java.util.*


class BleUpdatesService : Service() {

    companion object {
        private const val BACKGROUND_CHANNEL_ID = "SILENT_CHANNEL_BLE"
        private const val NOTIFICATION_ID = 1

        private const val TAG = "BLE"
    }

    private var notificationManager: NotificationManager? = null

    private val peripherals = mutableMapOf<BluetoothDevice, PeripheralData>()

    private val deviceManager by BluetoothManagerProvider()

    private var bluetoothState: Int = -1

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                bluetoothState =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                notificationManager?.notify(NOTIFICATION_ID, getNotification())

                when (bluetoothState) {
                    BluetoothAdapter.STATE_OFF -> stopBleService()
                    BluetoothAdapter.STATE_ON -> startBleService()
                }
            }
        }
    }

    override fun onCreate() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
            notificationManager = this
            // Android O requires a Notification Channel.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name: CharSequence = getString(R.string.app_name)
                val channel = NotificationChannel(BACKGROUND_CHANNEL_ID, name, IMPORTANCE_LOW)
                createNotificationChannel(channel)
            }
        }
        bluetoothState =
            if (deviceManager.checkBluetooth() == Enums.ENABLED) BluetoothAdapter.STATE_ON
            else BluetoothAdapter.STATE_OFF
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Start Command")

        startForeground()

        startBleService()

        return START_STICKY
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel =
                NotificationChannel(
                    BACKGROUND_CHANNEL_ID,
                    getString(R.string.background_channel_name),
                    IMPORTANCE_LOW
                )
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(channel)
        }

        startForeground(NOTIFICATION_ID, getNotification())
    }

    private fun hasPermissions(): Boolean {
        val notGrantedPermissions =
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION).filter { it.isNotGranted() }

        return notGrantedPermissions.isEmpty()
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothReceiver)
    }

    private fun startScanning() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        if (hasPermissions() && gpsEnabled) {
            deviceManager.startSearchDevices(::onBleDeviceFound)
        }
    }

    private fun startAdvertising() {
        try {
            deviceManager.startAdvertising()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onBleDeviceFound(result: ScanResult) {
        peripherals[result.device]?.let { peripheralData ->
            if (System.currentTimeMillis() - peripheralData.date.time < 5000) {
                Log.d(
                    SCAN_TAG,
                    "Not connecting to ${result.device.address} yet"
                )

                return
            }
        }

        peripherals[result.device] = PeripheralData(result.rssi, Date())
        if (deviceManager.connectDevice(result)) {
            Log.d(
                SCAN_TAG,
                "Connecting to ${result.device.address}, RSSI ${result.rssi}"
            )
        }
    }

    private fun onBleDeviceConnect(device: BluetoothDevice, result: Boolean) {
        if (!result) {
            peripherals.remove(device)
        }
    }

    fun startBleService() {
        startAdvertising()
        startScanning()
    }

    fun stopBleService() {
        deviceManager.stopSearchDevices()
        deviceManager.stopServer()
        deviceManager.stopAdvertising()
    }

    private fun getNotification(): Notification? {
        val intent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, BACKGROUND_CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setContentText(getBluetoothState())
                .setSmallIcon(R.drawable.ic_bluetooth_black_24dp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE)
        }

        return builder.build()
    }

    private fun getBluetoothState(): String {
        return getString(
            when (bluetoothState) {
                BluetoothAdapter.STATE_OFF -> R.string.bluetooth_off
                BluetoothAdapter.STATE_TURNING_OFF -> R.string.turning_bluetooth_off
                BluetoothAdapter.STATE_ON -> R.string.bluetooth_on
                BluetoothAdapter.STATE_TURNING_ON -> R.string.turning_bluetooth_on
                else -> R.string.bluetooth_unknown_state
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}


data class PeripheralData(val rssi: Int, val date: Date)