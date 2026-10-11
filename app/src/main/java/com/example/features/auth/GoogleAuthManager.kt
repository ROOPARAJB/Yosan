package com.example.features.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.BuildConfig

sealed class GoogleAuthResult {
    data class Success(val idToken: String) : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Error(val message: String, val code: String = "GOOGLE_AUTH_FAILED") : GoogleAuthResult()
}

class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    fun isConfigured(): Boolean {
        val clientId = getWebClientId()
        return !clientId.contains("example.apps.googleusercontent.com") && clientId.isNotBlank()
    }

    suspend fun getGoogleIdToken(webClientId: String = getWebClientId()): GoogleAuthResult {
        if (!isConfigured()) {
            return GoogleAuthResult.Error(
                "Google Sign-In requires your Google Cloud Web Client ID in .env. You can use Email OTP or Offline Profile right now.",
                "CONFIG_REQUIRED"
            )
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential && 
                (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                 credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthResult.Success(googleIdTokenCredential.idToken)
            } else {
                GoogleAuthResult.Error("Unsupported credential type returned from Google", "INVALID_GOOGLE_TOKEN")
            }
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            GoogleAuthResult.Error("No Google account found or sign-in not available. Please verify Google Cloud SHA-1 setup or use Email OTP.", "NO_CREDENTIALS")
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleAuthResult.Error(e.message ?: "Google sign in failed", "GOOGLE_AUTH_FAILED")
        } catch (e: Exception) {
            GoogleAuthResult.Error(e.message ?: "An unexpected error occurred during Google Auth", "GOOGLE_AUTH_FAILED")
        }
    }

    private fun getWebClientId(): String {
        return try {
            val field = BuildConfig::class.java.getField("GOOGLE_WEB_CLIENT_ID")
            field.get(null) as? String ?: DEFAULT_CLIENT_ID
        } catch (e: Exception) {
            DEFAULT_CLIENT_ID
        }
    }

    companion object {
        const val DEFAULT_CLIENT_ID = "649210682299-f2hg4qgui6l6621km5371r87gcp5tmih.apps.googleusercontent.com"
    }
}
