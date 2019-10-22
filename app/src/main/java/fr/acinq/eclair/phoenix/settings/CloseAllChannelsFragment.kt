/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.phoenix.settings

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.channel.`NORMAL$`
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsCloseAllChannelsBinding
import fr.acinq.eclair.phoenix.utils.Converter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class CloseAllChannelsFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsCloseAllChannelsBinding

  private lateinit var model: CloseAllChannelsViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsCloseAllChannelsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(CloseAllChannelsViewModel::class.java)
    mBinding.model = model
    mBinding.instructions.text = Converter.html(getString(R.string.closeall_instructions))
  }

  override fun onStart() {
    super.onStart()
    getChannels()
    mBinding.confirmButton.setOnClickListener {
      AlertDialog.Builder(context)
        .setMessage(R.string.closeall_confirm_dialog_message)
        .setPositiveButton(R.string.btn_confirm) { _, _ -> closeAllChannels() }
        .setNegativeButton(R.string.btn_cancel, null)
        .show()
    }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
  }

  private fun getChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving list of channels: ", exception)
      model.state.value = ClosingChannelsState.ERROR
    }) {
      model.state.value = ClosingChannelsState.CHECKING_CHANNELS
      val channels = appKit.getChannels(null)
      val normals = channels.filter { c -> c.state() == `NORMAL$`.`MODULE$` }
      if (normals.isEmpty()) {
        model.state.value = ClosingChannelsState.NO_CHANNELS
      } else {
        model.state.value = ClosingChannelsState.READY
      }
    }
  }

  private fun closeAllChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when closing all channels: ", exception)
      model.state.value = ClosingChannelsState.ERROR
      Handler().postDelayed({ model.state.value = ClosingChannelsState.READY }, 2000)
    }) {
      model.state.value = ClosingChannelsState.IN_PROGRESS
      appKit.closeAllChannels(mBinding.addressInput.text.toString(), model.forceClose.value!!)
      model.state.value = ClosingChannelsState.DONE
    }
  }
}

enum class ClosingChannelsState {
  CHECKING_CHANNELS, NO_CHANNELS, READY, IN_PROGRESS, DONE, ERROR
}

class CloseAllChannelsViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(CloseAllChannelsViewModel::class.java)

  val state = MutableLiveData(ClosingChannelsState.CHECKING_CHANNELS)
  val forceClose = MutableLiveData(false)

}
