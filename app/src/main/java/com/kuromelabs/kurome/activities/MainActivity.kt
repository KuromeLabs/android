package com.kuromelabs.kurome.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.fragments.MainFragment


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
        val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
        }
    }


}