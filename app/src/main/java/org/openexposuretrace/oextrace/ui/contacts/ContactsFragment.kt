package org.openexposuretrace.oextrace.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.andrefrsousa.superbottomsheet.SuperBottomSheetFragment
import kotlinx.android.synthetic.main.fragment_contacts.*
import org.openexposuretrace.oextrace.R


class ContactsFragment : SuperBottomSheetFragment() {

    private lateinit var contactsViewModel: ContactsViewModel
    private val contactsAdapter = ContactsAdapter(this)

    private val contactComparator = Comparator<Contact> { first, second ->
        if (first.day() == second.day()) {
            first.metaData()?.let { firstMetaData ->
                second.metaData()?.let { secondMetaData ->
                    (firstMetaData.date.time - secondMetaData.date.time).toInt()
                } ?: 1
            } ?: second.metaData()?.let { _ ->
                -1
            } ?: first.btContact?.let { firstBtContact ->
                second.btContact?.let { secondBtContact ->
                    firstBtContact.encounters.size - secondBtContact.encounters.size
                } ?: 1
            } ?: second.btContact?.let { _ ->
                -1
            } ?: 0
        } else {
            first.day() - second.day()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        contactsViewModel = ViewModelProvider(this).get(ContactsViewModel::class.java)

        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun getCornerRadius() = resources.getDimension(R.dimen.sheet_rounded_corner)

    override fun animateCornerRadius() = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contactsRecyclerView.adapter = contactsAdapter

        contactsViewModel.btContactsLiveData.observe(this, Observer { btContacts ->
            val items = mutableListOf<Contact>()

            items.addAll(btContacts)

            contactsViewModel.qrContactsLiveData.value?.let {
                items.addAll(it)
            }

            items.sortWith(contactComparator)
            items.reverse()

            contactsAdapter.setItems(items)
        })

        contactsViewModel.qrContactsLiveData.observe(this, Observer { qrContacts ->
            val items = mutableListOf<Contact>()

            contactsViewModel.btContactsLiveData.value?.let {
                items.addAll(it)
            }

            items.addAll(qrContacts)

            items.sortWith(contactComparator)
            items.reverse()

            contactsAdapter.setItems(items)
        })

        closeImageButton.setOnClickListener { dismiss() }
    }

}
