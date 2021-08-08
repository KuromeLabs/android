package com.kuromelabs.kurome

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.*


fun getGuid(context: Context): String {
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    var id = preferences.getString("id", null)
    if (id == null){
        id = UUID.randomUUID().toString()
        preferences.edit().putString("id",id).apply()
    }
    return id
}