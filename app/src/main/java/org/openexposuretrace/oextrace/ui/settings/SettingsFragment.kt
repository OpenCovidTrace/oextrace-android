package org.openexposuretrace.oextrace.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.andrefrsousa.superbottomsheet.SuperBottomSheetFragment
import kotlinx.android.synthetic.main.fragment_settings.*
import org.openexposuretrace.oextrace.R
import org.openexposuretrace.oextrace.ext.ui.choose
import org.openexposuretrace.oextrace.ext.ui.confirm
import org.openexposuretrace.oextrace.ext.ui.showInfo
import org.openexposuretrace.oextrace.service.TrackingService
import org.openexposuretrace.oextrace.storage.KeysManager
import org.openexposuretrace.oextrace.storage.TracksManager
import org.openexposuretrace.oextrace.storage.UserSettingsManager

class SettingsFragment : SuperBottomSheetFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeStatusButton.setOnClickListener { changeStatus() }

        recordTrackSwitch.setOnClickListener {
            UserSettingsManager.recordTrack = recordTrackSwitch.isChecked

            activity?.startService(Intent(activity, TrackingService::class.java))
        }

        shareTrackSwitch.setOnClickListener {
            UserSettingsManager.uploadTrack = shareTrackSwitch.isChecked
        }

        shareMetaSwitch.setOnClickListener {
            UserSettingsManager.discloseMetaData = shareMetaSwitch.isChecked
        }

        refreshStatus()

        closeImageButton.setOnClickListener { dismiss() }
    }

    override fun onResume() {
        super.onResume()

        // This value could've change through on-boarding so we have to force refresh
        recordTrackSwitch.isChecked = UserSettingsManager.recordTrack
    }

    override fun isSheetAlwaysExpanded(): Boolean {
        return true
    }

    private fun changeStatus() {
        if (UserSettingsManager.sick()) {
            showInfo(R.string.whats_next_info)
        } else {
            confirm(R.string.report_exposure_confirmation) {
                updateUserStatus(UserSettingsManager.EXPOSED)

                choose(
                    R.string.tracks_upload_confirmation,
                    {
                        UserSettingsManager.uploadTrack = true
                        shareTrackSwitch.isChecked = true

                        TracksManager.uploadNewTracks()

                        requestMetaDataDisclosure()
                    },
                    {
                        requestMetaDataDisclosure()
                    }
                )
            }
        }
    }

    private fun updateUserStatus(status: String) {
        UserSettingsManager.status = status

        KeysManager.uploadNewKeys(true)

        refreshStatus()
    }

    private fun refreshStatus() {
        if (UserSettingsManager.sick()) {
            currentStatusTextView.text =
                getString(R.string.current_status, getString(R.string.status_symptoms))

            changeStatusButton.setText(R.string.whats_next)
            changeStatusButton.setBackgroundResource(R.drawable.bg_green_button)

            shareTrackSwitch.isEnabled = true
            shareTrackSwitch.isChecked = UserSettingsManager.uploadTrack

            shareMetaSwitch.isEnabled = true
            shareMetaSwitch.isChecked = UserSettingsManager.discloseMetaData
        } else {
            currentStatusTextView.text =
                getString(R.string.current_status, getString(R.string.status_normal))

            changeStatusButton.setText(R.string.i_got_symptoms)
            changeStatusButton.setBackgroundResource(R.drawable.bg_red_button)
        }
    }

    private fun requestMetaDataDisclosure() {
        choose(
            R.string.share_meta_data_confirmation,
            {
                UserSettingsManager.discloseMetaData = true
                shareMetaSwitch.isChecked = true

                KeysManager.uploadNewKeys(true)
            },
            {
                KeysManager.uploadNewKeys(true)
            }
        )
    }

}
