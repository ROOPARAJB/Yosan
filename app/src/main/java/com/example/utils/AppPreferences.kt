package com.example.utils

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
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

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var appLockTimeoutSeconds: Int
        get() = prefs.getInt(KEY_APP_LOCK_TIMEOUT_SECONDS, 0)
        set(value) = prefs.edit().putInt(KEY_APP_LOCK_TIMEOUT_SECONDS, value).apply()

    var isExternalIntentActive: Boolean
        get() = prefs.getBoolean(KEY_EXTERNAL_INTENT_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_EXTERNAL_INTENT_ACTIVE, value).apply()

    companion object {
        private const val PREFS_NAME = "yosan_app_preferences"
        private const val KEY_ONBOARDING_COMPLETED = "is_onboarding_completed"
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_BIOMETRIC_ENABLED = "is_biometric_enabled"
        private const val KEY_APP_LOCK_TIMEOUT_SECONDS = "app_lock_timeout_seconds"
        private const val KEY_EXTERNAL_INTENT_ACTIVE = "external_intent_active"
    }
}
