package org.openexposuretrace.oextrace.ext.text

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*


const val DATE_WITH_TIME_FULL_FORMAT = "dd MMM yyyy, HH:mm:ss"
const val DATE_FORMAT = "dd MMM yyyy"

@SuppressLint("SimpleDateFormat")
fun Date.dateFullFormat(): String {
    return SimpleDateFormat(DATE_WITH_TIME_FULL_FORMAT).format(this.time)
}

@SuppressLint("SimpleDateFormat")
fun Date.dateFormat(): String {
    return SimpleDateFormat(DATE_FORMAT).format(this.time)
}
