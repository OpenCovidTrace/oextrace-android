package org.openexposuretrace.oextrace.ui.contacts

import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.andrefrsousa.superbottomsheet.SuperBottomSheetFragment
import kotlinx.android.synthetic.main.list_item_contact.view.*
import org.openexposuretrace.oextrace.MainActivity
import org.openexposuretrace.oextrace.R
import org.openexposuretrace.oextrace.ext.text.dateFormat
import org.openexposuretrace.oextrace.ext.text.dateFullFormat
import org.openexposuretrace.oextrace.ui.base.BaseAdapter
import org.openexposuretrace.oextrace.ui.base.BaseViewHolder
import org.openexposuretrace.oextrace.utils.CryptoUtil


class ContactsAdapter(private val fragment: SuperBottomSheetFragment) :
    BaseAdapter<Contact, ContactsAdapter.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return R.layout.list_item_contact
    }

    override fun newViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(viewType, parent)


    inner class ViewHolder(@LayoutRes layoutRes: Int, parent: ViewGroup) :
        BaseViewHolder<Contact>(layoutRes, parent) {

        override fun updateView(item: Contact) {
            with(itemView) {
                item.btContact?.let { btContact ->
                    contactTypeText.text = context.getString(R.string.bt_contact)

                    btContact.encounters.first().metaData?.let { firstMetaData ->
                        btContact.encounters.last().metaData?.let { lastMetaData ->
                            if (btContact.encounters.size == 1) {
                                contactTimeText.text = firstMetaData.date.dateFullFormat()
                            } else {
                                contactTimeText.text = firstMetaData.date.dateFullFormat() + " - " +
                                        lastMetaData.date.dateFullFormat()
                            }

                            locationImage.visibility = if (firstMetaData.coord == null) {
                                INVISIBLE
                            } else {
                                VISIBLE
                            }
                        }
                    } ?: run {
                        contactTimeText.text = CryptoUtil.getDate(btContact.day).dateFormat()

                        locationImage.visibility = INVISIBLE
                    }

                    encountersText.text =
                        context.getString(R.string.encounters, btContact.encounters.size)
                    encountersText.visibility = VISIBLE

                    exposedText.visibility = if (btContact.exposed) {
                        VISIBLE
                    } else {
                        INVISIBLE
                    }
                } ?: run {
                    val qrContact = item.qrContact!!
                    contactTypeText.text = context.getString(R.string.qr_contact)

                    qrContact.metaData?.let { metaData ->
                        contactTimeText.text = metaData.date.dateFullFormat()

                        locationImage.visibility = if (metaData.coord == null) {
                            INVISIBLE
                        } else {
                            VISIBLE
                        }
                    } ?: run {
                        contactTimeText.text = CryptoUtil.getDate(qrContact.day).dateFormat()

                        locationImage.visibility = INVISIBLE
                    }

                    encountersText.visibility = INVISIBLE

                    exposedText.visibility = if (qrContact.exposed) {
                        VISIBLE
                    } else {
                        INVISIBLE
                    }
                }

                itemView.setOnClickListener {
                    item.metaData()?.let { metaData ->
                        metaData.coord?.let { coord ->
                            (fragment.requireActivity() as MainActivity).goToContact(coord)

                            fragment.dismiss()
                        }
                    }
                }
            }
        }
    }

}
