package com.kuromelabs.kurome.UI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.database.DeviceRepository
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.models.DeviceViewModel
import com.kuromelabs.kurome.models.DeviceViewModelFactory
import com.kuromelabs.kurome.services.ForegroundConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MainFragment: Fragment(R.layout.fragment_main) {
    var mContext: Context? = null
    val pairingScope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: DeviceRepository
    private val deviceViewModel: DeviceViewModel by viewModels {
        DeviceViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (activity?.application as KuromeApplication).repository
        mContext = context
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mContext = context

        val serviceIntent = Intent(context, ForegroundConnectionService::class.java)
        val startButton = view.findViewById<Button>(R.id.start_button)
        val stopButton = view.findViewById<Button>(R.id.stop_button)
        startButton.setOnClickListener {
            ContextCompat.startForegroundService(mContext!!, serviceIntent)
        }
        stopButton.setOnClickListener { mContext!!.stopService(serviceIntent) }
        val recyclerView: RecyclerView = view.findViewById(R.id.device_list)
        val deviceAdapter = DeviceAdapter {
            Log.e("kurome/mainfragment", "CLICKED! $it")
            val device = Device(it.name, it.id)
            device.isPaired = true
            deviceViewModel.insert(device)
        }
        recyclerView.adapter = deviceAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        deviceViewModel.combinedDevices.observe(viewLifecycleOwner) { devices ->
            devices.let { deviceAdapter.submitList(it) }
        }
    }
}