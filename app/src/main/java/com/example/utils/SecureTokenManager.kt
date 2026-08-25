package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    fun saveUserSession(userId: Long, googleSub: String, email: String, name: String, profilePicture: String) {
        sharedPreferences.edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_GOOGLE_SUB, googleSub)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_PROFILE_PIC, profilePicture)
            .apply()
    }

    fun getUserId(): Long = sharedPreferences.getLong(KEY_USER_ID, -1L)
    fun getGoogleSub(): String? = sharedPreferences.getString(KEY_GOOGLE_SUB, null)
    fun getUserEmail(): String? = sharedPreferences.getString(KEY_USER_EMAIL, null)
    fun getUserName(): String? = sharedPreferences.getString(KEY_USER_NAME, null)
    fun getProfilePic(): String? = sharedPreferences.getString(KEY_PROFILE_PIC, null)

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_GOOGLE_SUB = "google_sub"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROFILE_PIC = "profile_picture_url"
    }
}
