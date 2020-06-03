package org.openexposuretrace.oextrace.ui.logs

import androidx.lifecycle.ViewModel
import org.openexposuretrace.oextrace.di.DatabaseProvider
import org.openexposuretrace.oextrace.utils.DoAsync

class LogsViewModel : ViewModel() {

    private val database by DatabaseProvider()

    val logsLiveData = database.appDao().getLogsLiveData()

    fun removeOldContacts() {
        DoAsync { database.appDao().clearLogs() }
    }
}