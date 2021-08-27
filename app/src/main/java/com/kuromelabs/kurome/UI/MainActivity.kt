package com.kuromelabs.kurome.UI

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.telecom.TelecomManager.DURATION_LONG
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.R
import java.lang.System.exit
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity(R.layout.activity_main) {
    val fm = supportFragmentManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container_view, MainFragment::class.java, null)
                .commit()
        }
    }


}