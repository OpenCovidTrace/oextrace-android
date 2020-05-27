package org.openexposuretrace.oextrace.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationSettingsRequest
import org.openexposuretrace.oextrace.MainActivity
import org.openexposuretrace.oextrace.R
import org.openexposuretrace.oextrace.location.LocationAccessManager
import org.openexposuretrace.oextrace.location.LocationUpdateManager
import org.openexposuretrace.oextrace.storage.UserSettingsManager

class TrackingService : Service() {

    companion object {
        private const val BACKGROUND_CHANNEL_ID = "SILENT_CHANNEL_LOCATION"
        private const val NOTIFICATION_ID = 2

        val TRACKING_LOCATION_REQUEST = LocationRequest()

        val TRACKING_LOCATION_REQUEST_BUILDER: LocationSettingsRequest.Builder =
            LocationSettingsRequest.Builder().addLocationRequest(TRACKING_LOCATION_REQUEST)

        private const val TAG = "TRACKING"

        init {
            TRACKING_LOCATION_REQUEST.maxWaitTime = 5000
            TRACKING_LOCATION_REQUEST.interval = 3000
            TRACKING_LOCATION_REQUEST.fastestInterval = 1000
            TRACKING_LOCATION_REQUEST.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }
    }

    private val trackingLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                LocationUpdateManager.updateLocation(location)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Start Command")

        var foreground = false

        if (UserSettingsManager.recordTrack) {
            if (LocationAccessManager.authorized(this)) {
                LocationAccessManager.addConsumer(
                    this,
                    TRACKING_LOCATION_REQUEST,
                    trackingLocationCallback
                )

                foreground = true
                Log.i(TAG, "Tracking enabled")
            } else {
                Log.w(TAG, "Failed to request tracking location updates")
            }
        } else {
            stopTrackingUpdates()

            Log.i(TAG, "Tracking is off")
        }

        if (foreground) {
            startForeground()
        } else {
            stopForeground(true)
        }

        super.onStartCommand(intent, flags, startId)

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
                    NotificationManager.IMPORTANCE_LOW
                )
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(channel)
        }

        val openMainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            MainActivity.REQUEST_NONE,
            openMainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder =
            NotificationCompat.Builder(this, BACKGROUND_CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setContentText(getString(R.string.tracking_active))
                .setSmallIcon(R.drawable.ic_near_me_black_24dp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE)
        }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        super.onCreate()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        stopTrackingUpdates()
        super.onDestroy()
    }

    private fun stopTrackingUpdates() {
        LocationAccessManager.removeConsumer(trackingLocationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}
