package com.example.features.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.BuildConfig

sealed class GoogleAuthResult {
    data class Success(val idToken: String) : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Error(val message: String, val code: String = "GOOGLE_AUTH_FAILED") : GoogleAuthResult()
}

class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun getGoogleIdToken(webClientId: String = getWebClientId()): GoogleAuthResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthResult.Success(googleIdTokenCredential.idToken)
            } else {
                GoogleAuthResult.Error("Unsupported credential type returned from Google", "INVALID_GOOGLE_TOKEN")
            }
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
        const val DEFAULT_CLIENT_ID = "1090000000000-example.apps.googleusercontent.com"
    }
}
