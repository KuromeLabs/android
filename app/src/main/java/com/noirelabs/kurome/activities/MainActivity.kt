package com.noirelabs.kurome.activities

import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.noirelabs.kurome.R
import com.noirelabs.kurome.network.SocketInstance


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        val socket = SocketInstance()
        val s: List<String> = socket.receiveUDPMessage("235.132.20.12",33586).split(':')
        socket.startConnection(s[1], 33587)
        for (i in 1..64){
            socket.sendMessage("Hello  : $i")
        }
        val tv: TextView = findViewById(R.id.tv)
        tv.text = s.joinToString(":")
        socket.stopConnection()
    }


}