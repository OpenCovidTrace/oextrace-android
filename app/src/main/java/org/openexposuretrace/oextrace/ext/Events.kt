package org.openexposuretrace.oextrace.ext

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import org.openexposuretrace.oextrace.BuildConfig


fun Context.logEvent(eventName: String, attributes: Map<String, Any>? = null) {
    if (!BuildConfig.DEBUG) {

        val firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        if (attributes != null) {
            val bundle = Bundle()
            for (attribute in attributes.entries) {
                bundle.putString(attribute.key, attribute.toString())
            }
            firebaseAnalytics.logEvent(eventName, bundle)
        } else {
            firebaseAnalytics.logEvent(eventName, null)
        }
    }
}
