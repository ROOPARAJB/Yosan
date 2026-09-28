package com.example.utils

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isOnboardingCompleted: Boolean
        get() {
            if (prefs.contains(KEY_ONBOARDING_COMPLETED)) {
                return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
            }
            // Seamless zero-wait migration: if local SQLite DB file already exists on the device,
            // the user is an existing returning user who already onboarded / imported data.
            val dbFile = context.getDatabasePath("finance_manager_db")
            if (dbFile.exists() && dbFile.length() > 0) {
                prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                return true
            }
            return false
        }
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
        }

    var isDarkMode: Boolean?
        get() {
            return if (prefs.contains(KEY_DARK_MODE)) {
                prefs.getBoolean(KEY_DARK_MODE, false)
            } else {
                null
            }
        }
        set(value) {
            if (value != null) {
                prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            } else {
                prefs.edit().remove(KEY_DARK_MODE).apply()
            }
        }

    companion object {
        private const val PREFS_NAME = "yosan_app_preferences"
        private const val KEY_ONBOARDING_COMPLETED = "is_onboarding_completed"
        private const val KEY_DARK_MODE = "is_dark_mode"
    }
}
