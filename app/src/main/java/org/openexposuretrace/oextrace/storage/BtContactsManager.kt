package org.openexposuretrace.oextrace.storage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.gson.Gson
import org.openexposuretrace.oextrace.data.ContactCoord
import org.openexposuretrace.oextrace.data.ContactMetaData
import org.openexposuretrace.oextrace.ui.contacts.Contact
import org.openexposuretrace.oextrace.utils.CryptoUtil
import org.openexposuretrace.oextrace.utils.CryptoUtil.base64DecodeByteArray

object BtContactsManager : PreferencesHolder("bt-contacts") {

    private const val CONTACTS = "contacts"

    private val liveData = MutableLiveData(contacts.values.toList())

    val contactsLiveData = Transformations.map(liveData) { contacts ->
        contacts.map {
            it.toContact()
        }
    }

    var contacts: Map<String, BtContact>
        get() {
            val jsonString = getString(CONTACTS)
            (Gson().fromJson(jsonString) as? Map<String, BtContact>)?.let {
                return it
            } ?: kotlin.run { return mapOf() }
        }
        set(value) {
            val hashMapString = Gson().toJson(value)
            setString(CONTACTS, hashMapString)

            liveData.postValue(value.values.toList())
        }

    fun removeOldContacts() {
        val expirationDay = DataManager.expirationDay()

        val newContacts = contacts.filterValues { it.day > expirationDay }

        contacts = newContacts
    }

    fun matchContacts(keysData: KeysData): Pair<Boolean, ContactCoord?> {
        val newContacts = contacts

        var hasExposure = false
        var lastExposedContactCoord: ContactCoord? = null

        newContacts.forEach { (_, contact) ->
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
                        contact.encounters.forEach { encounter ->
                            encounter.metaData = CryptoUtil.decodeMetaData(
                                encounter.meta.base64DecodeByteArray(),
                                metaKey.base64DecodeByteArray()
                            )

                            encounter.metaData?.coord?.let {
                                lastExposedContactCoord = it
                            }
                        }

                    }

                    hasExposure = true
                }
            }
        }

        contacts = newContacts

        return Pair(hasExposure, lastExposedContactCoord)
    }

    fun addContact(rollingId: String, day: Int, encounter: BtEncounter) {
        val newContacts = contacts.toMutableMap()

        newContacts[rollingId]?.let {
            it.encounters += encounter
        } ?: run {
            newContacts[rollingId] = BtContact(rollingId, day, listOf(encounter))
        }

        contacts = newContacts
    }

}


data class BtContact(
    val rollingId: String,
    val day: Int,
    var encounters: List<BtEncounter>,
    var exposed: Boolean = false
) {
    fun toContact(): Contact {
        return Contact(btContact = this, qrContact = null)
    }
}


data class BtEncounter(
    val rssi: Int,
    val meta: String,
    var metaData: ContactMetaData? = null
)
