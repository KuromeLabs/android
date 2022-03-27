package com.kuromelabs.kurome.domain.util

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.*

object IdentityProvider {
    fun getGuid(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var id = preferences.getString("id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            preferences.edit().putString("id", id).apply()
        }
        return id
    }
}