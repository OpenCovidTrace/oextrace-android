package org.openexposuretrace.oextrace.ui.status

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_status.*
import org.openexposuretrace.oextrace.R
import org.openexposuretrace.oextrace.ext.ui.confirm
import org.openexposuretrace.oextrace.ext.ui.showInfo
import org.openexposuretrace.oextrace.storage.KeysManager
import org.openexposuretrace.oextrace.storage.TracksManager
import org.openexposuretrace.oextrace.storage.UserStatusManager

class StatusFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeStatusButton.setOnClickListener { changeStatus() }
        refreshStatus()
    }

    private fun changeStatus() {
        if (UserStatusManager.sick()) {
            showInfo(R.string.whats_next_info)
        } else {
            confirm(R.string.change_status_info_1) {
                updateUserStatus(UserStatusManager.symptoms)
            }
        }
    }

    private fun updateUserStatus(status: String) {
        UserStatusManager.setStatus(status)

        TracksManager.uploadNewTracks()
        KeysManager.uploadNewKeys(includeToday = true)

        refreshStatus()
    }

    private fun refreshStatus() {
        if (UserStatusManager.sick()) {
            currentStatusTextView.text =
                getString(R.string.current_status, getString(R.string.status_symptoms))

            changeStatusButton.setText(R.string.whats_next)
            changeStatusButton.setBackgroundResource(R.drawable.bg_green_button)
        } else {
            currentStatusTextView.text =
                getString(R.string.current_status, getString(R.string.status_normal))

            changeStatusButton.setText(R.string.i_got_symptoms)
            changeStatusButton.setBackgroundResource(R.drawable.bg_red_button)
        }
    }
}
