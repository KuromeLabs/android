package com.kuromelabs.kurome.UI

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kuromelabs.kurome.BuildConfig
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
import kotlin.system.exitProcess

class MainFragment : Fragment(R.layout.fragment_main), PairingDialogFragment.NoticeDialogListener {
    private lateinit var repository: DeviceRepository
    private var requestedManageFilePermissions = false
    private val deviceViewModel: DeviceViewModel by viewModels {
        DeviceViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (activity?.application as KuromeApplication).repository
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val startButton = view.findViewById<Button>(R.id.start_button)
        val stopButton = view.findViewById<Button>(R.id.stop_button)
        val serviceIntent = Intent(context, ForegroundConnectionService::class.java)
        startButton.setOnClickListener(startServiceWithPermission)
        stopButton.setOnClickListener { requireContext().stopService(serviceIntent) }
        val recyclerView: RecyclerView = view.findViewById(R.id.device_list)
        val deviceAdapter = DeviceAdapter {
            Log.e("kurome/mainfragment", "CLICKED! $it")
            val dialog = PairingDialogFragment(it, this)
            dialog.show(requireActivity().supportFragmentManager, "")
           // deviceViewModel.insert(device)
        }
        recyclerView.adapter = deviceAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        deviceViewModel.combinedDevices.observe(viewLifecycleOwner) { devices ->
            devices.let { deviceAdapter.submitList(it) }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                val serviceIntent = Intent(context, ForegroundConnectionService::class.java)
                requireContext().startService(serviceIntent)
            } else {
                Toast.makeText(
                    context,
                    "Kurome can't work without File Access permissions.",
                    Toast.LENGTH_LONG
                ).show()
                exitProcess(0)
            }
        }


    private val startServiceWithPermission: (View) -> Unit = {
        val serviceIntent = Intent(context, ForegroundConnectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager())
                getFilePermission(true)
            else
                requireContext().startService(serviceIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            )
                requireContext().startService(serviceIntent)
            else
                getFilePermission(false)
        }
    }

    @SuppressLint("NewApi")
    override fun onResume() {
        super.onResume()
        //If we are returning from Settings after requesting MANAGE_EXTERNAL_STORAGE, which is
        //only after the user wants to start the service, check if we can start the service.
        if (requestedManageFilePermissions && Environment.isExternalStorageManager()){
            val serviceIntent = Intent(context, ForegroundConnectionService::class.java)
            requireContext().startService(serviceIntent)
            requestedManageFilePermissions = false
        }
    }

    @SuppressLint("InlinedApi")
    private fun getFilePermission(isROrHigher: Boolean) {
        val builder = AlertDialog.Builder(requireContext(), R.style.Theme_Kurome_Dialog)
            .setTitle("File Access Permission")
            .setMessage(
                "Kurome is a filesystem application and needs to manage your device's storage." +
                        " Please allow access to all files in the next screen."
            )
            .setCancelable(true)
            .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                if (isROrHigher) {
                    requestedManageFilePermissions = true
                    val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
                } else
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton("NO THANKS") { _: DialogInterface, _: Int ->
                Toast.makeText(
                    context,
                    "Kurome can't work without File Access permissions.",
                    Toast.LENGTH_LONG
                ).show()
            }
        val alert = builder.create()
        alert.show()
    }

    override fun onSuccess(device: Device) {
        deviceViewModel.insert(device)
    }
}