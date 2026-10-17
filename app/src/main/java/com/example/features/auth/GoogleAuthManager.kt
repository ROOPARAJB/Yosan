package com.example.features.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.BuildConfig

sealed class GoogleAuthResult {
    data class Success(
        val idToken: String,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null
    ) : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Error(val message: String, val code: String = "GOOGLE_AUTH_FAILED") : GoogleAuthResult()
}

class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)
    private val TAG = "GoogleAuthManager"

    fun isConfigured(): Boolean {
        val clientId = getWebClientId()
        return !clientId.contains("example.apps.googleusercontent.com") && clientId.isNotBlank()
    }

    private fun getActivityContext(ctx: Context): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    suspend fun getGoogleIdToken(webClientId: String = getWebClientId()): GoogleAuthResult {
        if (!isConfigured()) {
            return GoogleAuthResult.Error(
                "Google Sign-In requires your Google Cloud Web Client ID. Please configure GOOGLE_WEB_CLIENT_ID in your environment.",
                "CONFIG_REQUIRED"
            )
        }
        val targetContext = getActivityContext(context) ?: context

        Log.i(TAG, "Initiating Google Sign-In with Web Client ID: $webClientId")

        return try {
            val response = try {
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                Log.d(TAG, "Requesting credential via GetSignInWithGoogleOption")
                credentialManager.getCredential(targetContext, request)
            } catch (fallbackEx: Exception) {
                if (fallbackEx is GetCredentialCancellationException) {
                    throw fallbackEx
                }
                Log.w(TAG, "GetSignInWithGoogleOption fallback to GetGoogleIdOption: ${fallbackEx.message}")
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                credentialManager.getCredential(targetContext, fallbackRequest)
            }

            val credential = response.credential
            Log.i(TAG, "Credential received: class=${credential::class.java.simpleName}")

            var idToken: String? = null
            var googleEmail: String? = null
            var googleDisplayName: String? = null
            var googlePhotoUrl: String? = null

            if (credential is CustomCredential) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    idToken = googleIdTokenCredential.idToken
                    googleEmail = googleIdTokenCredential.id
                    googleDisplayName = googleIdTokenCredential.displayName
                    googlePhotoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                } catch (pe: Exception) {
                    Log.w(TAG, "createFrom parse note (${pe.message}), inspecting data bundle")
                }

                if (idToken.isNullOrBlank()) {
                    idToken = credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                        ?: credential.data.getString("id_token")
                        ?: credential.data.getString("idToken")
                        ?: credential.data.getString("token")
                    if (googleEmail.isNullOrBlank()) {
                        googleEmail = credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID")
                            ?: credential.data.getString("id")
                            ?: credential.data.getString("email")
                    }
                    if (googleDisplayName.isNullOrBlank()) {
                        googleDisplayName = credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_DISPLAY_NAME")
                            ?: credential.data.getString("displayName")
                            ?: credential.data.getString("name")
                    }
                }
            }

            if (!idToken.isNullOrBlank()) {
                Log.i(TAG, "Google ID Token successfully obtained (length=${idToken.length})")
                GoogleAuthResult.Success(
                    idToken = idToken,
                    email = googleEmail,
                    displayName = googleDisplayName,
                    photoUrl = googlePhotoUrl
                )
            } else {
                Log.e(TAG, "No ID Token returned by Google Play Services")
                GoogleAuthResult.Error(
                    "Google Play Services did not provide an ID token. Verify your Web Client ID configuration.",
                    "INVALID_TOKEN"
                )
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google Sign-In picker")
            GoogleAuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google accounts available on device: ${e.message}")
            GoogleAuthResult.Error(
                "No Google accounts found on this device. Please add a Google account in phone Settings.",
                "NO_CREDENTIALS"
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException [type=${e.type}]: ${e.message}", e)
            val rawMsg = e.message ?: "Google Sign-In failed"
            if (rawMsg.contains("28444") || rawMsg.contains("developer console", ignoreCase = true)) {
                GoogleAuthResult.Error(
                    "Google Cloud Console configuration error (28444): Ensure your SHA-1 fingerprint and package name (yosan.finance) are registered as an Android Client ID.",
                    "DEVELOPER_ERROR_28444"
                )
            } else {
                GoogleAuthResult.Error(rawMsg, "GOOGLE_AUTH_FAILED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected auth error: ${e.message}", e)
            GoogleAuthResult.Error(e.message ?: "An unexpected error occurred during Google Auth", "GOOGLE_AUTH_FAILED")
        }
    }

    fun getWebClientId(): String {
        return try {
            val field = BuildConfig::class.java.getField("GOOGLE_WEB_CLIENT_ID")
            val raw = field.get(null) as? String
            if (!raw.isNullOrBlank() && !raw.contains("example.apps.googleusercontent.com")) {
                raw
            } else {
                DEFAULT_CLIENT_ID
            }
        } catch (e: Exception) {
            DEFAULT_CLIENT_ID
        }
    }

    companion object {
        const val DEFAULT_CLIENT_ID = "649210682299-k4cl29pr14o0pkblsodq208iajj2ivad.apps.googleusercontent.com"
    }
}
