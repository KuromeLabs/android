package com.kuromelabs.kurome.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.adapters.DeviceAdapter
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.models.DeviceViewModel
import com.kuromelabs.kurome.models.DeviceViewModelFactory
import com.kuromelabs.kurome.services.ForegroundConnectionService

class MainFragment: Fragment(R.layout.fragment_main) {
    var mContext: Context? = null
    private val deviceViewModel: DeviceViewModel by viewModels {
        DeviceViewModelFactory((activity?.application as KuromeApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val deviceAdapter = DeviceAdapter()
        recyclerView.adapter = deviceAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        deviceViewModel.allDevices.observe(viewLifecycleOwner) { devices ->
            // Update the cached copy of the words in the adapter.
            devices.let { deviceAdapter.submitList(it) }
        }

        deviceViewModel.insert(Device("asdf","fdsa"))
    }
}