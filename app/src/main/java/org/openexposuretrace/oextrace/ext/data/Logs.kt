package org.openexposuretrace.oextrace.ext.data

import org.openexposuretrace.oextrace.data.LogTableValue
import org.openexposuretrace.oextrace.di.DatabaseProvider
import org.openexposuretrace.oextrace.utils.DoAsync

private val database by DatabaseProvider()

fun insertLogs(tag: String, text: String) {
    LogTableValue(tag, text).add()
}

fun LogTableValue.add() {
    DoAsync {
        try {
            database.appDao().insertLog(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}