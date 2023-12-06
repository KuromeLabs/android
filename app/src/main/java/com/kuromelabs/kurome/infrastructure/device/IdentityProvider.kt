package com.kuromelabs.kurome.infrastructure.device

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.UUID

class IdentityProvider constructor(var context: Context)  {
    fun getEnvironmentName(): String {
        return Build.MODEL
    }

    fun getEnvironmentId(): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var id = preferences.getString("id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            preferences.edit().putString("id", id).apply()
        }
        return id
    }
}