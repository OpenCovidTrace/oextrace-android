package org.openexposuretrace.oextrace.ui.contacts

import androidx.lifecycle.ViewModel
import org.openexposuretrace.oextrace.data.ContactMetaData
import org.openexposuretrace.oextrace.storage.BtContact
import org.openexposuretrace.oextrace.storage.BtContactsManager
import org.openexposuretrace.oextrace.storage.QrContact
import org.openexposuretrace.oextrace.storage.QrContactsManager

class ContactsViewModel : ViewModel() {

    val btContactsLiveData = BtContactsManager.contactsLiveData
    val qrContactsLiveData = QrContactsManager.contactsLiveData

}


data class Contact(val btContact: BtContact?, val qrContact: QrContact?) {
    fun day(): Int {
        btContact?.let {
            return it.day
        }

        return qrContact!!.day
    }

    fun metaData(): ContactMetaData? {
        btContact?.let {
            return it.encounters.first().metaData
        }

        return qrContact!!.metaData
    }
}
