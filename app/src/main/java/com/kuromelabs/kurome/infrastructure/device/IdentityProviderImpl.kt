package com.kuromelabs.kurome.infrastructure.device

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.kuromelabs.kurome.application.interfaces.IdentityProvider
import java.util.*
import javax.inject.Inject

class IdentityProviderImpl @Inject constructor(var context: Context) : IdentityProvider {
    override fun getEnvironmentName(): String {
        return Build.MODEL
    }

    override fun getEnvironmentId(): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var id = preferences.getString("id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            preferences.edit().putString("id", id).apply()
        }
        return id
    }
}