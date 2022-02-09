package com.kuromelabs.kurome.UI

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.database.DeviceRepository
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.models.DeviceViewModel
import com.kuromelabs.kurome.models.DeviceViewModelFactory
import com.kuromelabs.kurome.services.KuromeService
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

    override fun onResume() {
        super.onResume()
        initializePermission()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = view.findViewById(R.id.device_list)
        val deviceAdapter = DeviceAdapter {
            val dialog = PairingDialogFragment(it, this)
            dialog.show(requireActivity().supportFragmentManager, "")
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
                val serviceIntent = Intent(context, KuromeService::class.java)
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


    private fun initializePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager())
                getFilePermission(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            )
                getFilePermission(false)
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
            .setCancelable(false)
            .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                if (isROrHigher) {
                    requestedManageFilePermissions = true
                    val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            uri
                        )
                    )
                } else
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton("NO THANKS") { _: DialogInterface, _: Int ->
                Toast.makeText(
                    context,
                    "Kurome can't work without File Access permissions.",
                    Toast.LENGTH_LONG
                ).show()
                exitProcess(0)
            }
        val alert = builder.create()
        alert.show()
    }

    override fun onSuccess(device: Device) {
        Toast.makeText(context, "${device.name} paired successfully", Toast.LENGTH_LONG)
            .show()
    }
}