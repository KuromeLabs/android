package com.kuromelabs.kurome.UI

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kuromelabs.kurome.R


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