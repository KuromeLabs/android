package com.noirelabs.kurome.activities

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.noirelabs.kurome.R
import com.noirelabs.kurome.network.SocketInstance
import com.noirelabs.kurome.services.ForegroundConnectionService


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val serviceIntent = Intent(this, ForegroundConnectionService::class.java)
        val startButton = findViewById<Button>(R.id.start_button)
        val stopButton = findViewById<Button>(R.id.stop_button)
        startButton.setOnClickListener {
            ContextCompat.startForegroundService(this, serviceIntent)
        }
        stopButton.setOnClickListener { stopService(serviceIntent) }
    }


}