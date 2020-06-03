package org.openexposuretrace.oextrace.storage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.gson.Gson
import org.openexposuretrace.oextrace.data.ContactCoord
import org.openexposuretrace.oextrace.data.ContactMetaData
import org.openexposuretrace.oextrace.ui.contacts.Contact
import org.openexposuretrace.oextrace.utils.CryptoUtil
import org.openexposuretrace.oextrace.utils.CryptoUtil.base64DecodeByteArray
import org.openexposuretrace.oextrace.utils.CryptoUtil.base64EncodedString

object QrContactsManager : PreferencesHolder("qr-contacts") {

    private const val CONTACTS = "contacts"

    private val liveData = MutableLiveData(contacts)

    val contactsLiveData = Transformations.map(liveData) { contacts ->
        contacts.map {
            it.toContact()
        }
    }

    var contacts: List<QrContact>
        get() {
            val jsonString = getString(CONTACTS)
            (Gson().fromJson(jsonString) as? List<QrContact>)?.let {
                return it
            } ?: kotlin.run { return arrayListOf() }
        }
        set(value) {
            val hashMapString = Gson().toJson(value)
            setString(CONTACTS, hashMapString)

            liveData.postValue(value)
        }

    fun removeOldContacts() {
        val expirationDay = DataManager.expirationDay()

        val newContacts = contacts.filter { it.day > expirationDay }

        contacts = newContacts
    }

    fun matchContacts(keysData: KeysData): Pair<Boolean, ContactCoord?> {
        val newContacts = contacts

        var hasExposure = false
        var lastExposedContactCoord: ContactCoord? = null

        newContacts.forEach { contact ->
            keysData.keys.filter {
                it.day == contact.day
            }.forEach { key ->
                if (CryptoUtil.match(
                        contact.rollingId,
                        contact.day,
                        key.value.base64DecodeByteArray()
                    )
                ) {
                    contact.exposed = true

                    key.meta?.let { metaKey ->
                        contact.metaData = CryptoUtil.decodeMetaData(
                            contact.meta.base64DecodeByteArray(),
                            metaKey.base64DecodeByteArray()
                        )

                        contact.metaData?.coord?.let {
                            lastExposedContactCoord = it
                        }
                    }

                    hasExposure = true
                }
            }
        }

        contacts = newContacts

        return Pair(hasExposure, lastExposedContactCoord)
    }

    fun addContact(contact: QrContact) {
        val newContacts = contacts.toMutableList()

        newContacts.add(contact)

        contacts = newContacts
    }

}


data class QrContact(
    val rollingId: String,
    val meta: String,
    val day: Int = CryptoUtil.currentDayNumber(),
    var exposed: Boolean = false,
    var metaData: ContactMetaData? = null
) {
    companion object {
        fun create(rpi: String): QrContact {
            val rpiData = rpi.base64DecodeByteArray()

            return QrContact(
                rpiData.sliceArray(0 until CryptoUtil.KEY_LENGTH).base64EncodedString(),
                rpiData.sliceArray(CryptoUtil.KEY_LENGTH until CryptoUtil.KEY_LENGTH * 2)
                    .base64EncodedString()
            )
        }
    }

    fun toContact(): Contact {
        return Contact(btContact = null, qrContact = this)
    }
}
