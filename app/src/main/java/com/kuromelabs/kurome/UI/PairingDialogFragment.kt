package com.kuromelabs.kurome.UI

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.getGuid
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.network.Link
import com.kuromelabs.kurome.network.LinkProvider
import com.kuromelabs.kurome.network.Packets
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit


class PairingDialogFragment(val device: Device, val listener: NoticeDialogListener) : DialogFragment(),
    DialogInterface.OnShowListener {

    interface NoticeDialogListener {
        fun onSuccess(device: Device)
    }
    lateinit var alert: AlertDialog
    private var link = Link()
    private val pairingScope = CoroutineScope(Dispatchers.IO)
    private val timeout =  object : CountDownTimer(30000, 1000) {
        override fun onTick(l: Long) {
            alert.getButton(AlertDialog.BUTTON_NEGATIVE).text = "Cancel (${TimeUnit.MILLISECONDS.toSeconds(l) + 1})"
        }

        override fun onFinish() {
            Toast.makeText(requireContext(), "Pairing operation timed out", Toast.LENGTH_LONG).show()
            link.stopConnection()
            pairingScope.cancel()
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.Theme_Kurome_Dialog)
        builder.setTitle("Pairing with ${device.name}")
            .setMessage("Remote ID: ${device.id} \n\nThis phone's ID: " + getGuid(requireContext()))
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(requireContext(), "Pairing operation cancelled", Toast.LENGTH_LONG)
                    .show()
                timeout.cancel()
                link.stopConnection()
                pairingScope.cancel()
                dismiss()
            }

        alert = builder.create()
        alert.setOnShowListener(this)
        return alert;
    }

    override fun onShow(p0: DialogInterface?) {

        pairingScope.launch {
            link = LinkProvider.createPairLink(device.ip, 33587)
            link.sendMessage(
                byteArrayOf(Packets.ACTION_PAIR) +
                        (Build.MODEL + ':' + getGuid(requireContext())).toByteArray(), false
            )
            val message = link.receiveMessage()
            if (message[0] == Packets.RESULT_ACTION_SUCCESS){
                val repository = (activity?.application as KuromeApplication).repository
                device.isPaired = true
                repository.insert(device)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Paired successfully", Toast.LENGTH_LONG)
                        .show()
                    timeout.cancel()
                    listener.onSuccess(device)
                    link.stopConnection()
                    pairingScope.cancel()
                    dismiss()
                }
            }
        }

            timeout.start()

    }
}