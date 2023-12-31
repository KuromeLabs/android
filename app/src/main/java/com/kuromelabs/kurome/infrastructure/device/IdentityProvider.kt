package com.kuromelabs.kurome.infrastructure.device

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.UUID

class IdentityProvider(var context: Context)  {
    private var id: String? = null
    fun getEnvironmentName(): String {
        return Build.MODEL
    }

    fun getEnvironmentId(): String {
        if (id != null) {
            return id!!
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        id = preferences.getString("id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            preferences.edit().putString("id", id).apply()
        }
        return id!!
    }
}