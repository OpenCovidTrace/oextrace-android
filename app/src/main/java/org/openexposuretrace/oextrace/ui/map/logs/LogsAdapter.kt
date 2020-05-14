package org.openexposuretrace.oextrace.ui.map.logs

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlinx.android.synthetic.main.list_item_log.view.*
import org.openexposuretrace.oextrace.R
import org.openexposuretrace.oextrace.data.LogTableValue
import org.openexposuretrace.oextrace.ui.base.BaseAdapter
import org.openexposuretrace.oextrace.ui.base.BaseViewHolder


class LogsAdapter : BaseAdapter<LogTableValue, LogsAdapter.ViewHolder>() {


    override fun getItemViewType(position: Int): Int {
        return R.layout.list_item_log
    }

    override fun newViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(viewType, parent)


    inner class ViewHolder(@LayoutRes layoutRes: Int, parent: ViewGroup) :
        BaseViewHolder<LogTableValue>(layoutRes, parent) {

        override fun updateView(item: LogTableValue) {
            with(itemView) {
                dateTimeTextView.text = item.getTimeWithTag()
                logValueTextView.text = item.getLogValue()
            }
        }
    }
}



