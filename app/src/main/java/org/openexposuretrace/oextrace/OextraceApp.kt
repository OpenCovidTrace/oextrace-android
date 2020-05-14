package org.openexposuretrace.oextrace

import android.app.Application
import com.google.firebase.FirebaseApp
import org.openexposuretrace.oextrace.bluetooth.DeviceManager
import org.openexposuretrace.oextrace.di.BluetoothManagerProvider
import org.openexposuretrace.oextrace.di.ContextProvider

class OextraceApp : Application() {

    init {
        ContextProvider.inject { applicationContext }
        BluetoothManagerProvider.inject { DeviceManager(applicationContext) }
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(applicationContext)
    }
}
