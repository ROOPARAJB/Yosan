package com.example.features.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserProfileEntity
import com.example.data.remote.*
import com.example.features.auth.GoogleAuthManager

import com.example.utils.SecureTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.features.auth.data.AuthRepository

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val isNewUser: Boolean = false) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = SecureTokenManager(application)
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val authRepository = AuthRepository(database)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _isNewUser = MutableStateFlow(false)
    val isNewUser: StateFlow<Boolean> = _isNewUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()


    init {
        checkSession()
    }

    /**
     * Checks the local Room DB for an existing profile.
     * No network call is made. The app is fully offline-first.
     * All data lives on the device and is only deleted when the app is uninstalled.
     */
    fun checkSession() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val savedProfile = authRepository.getProfileOnce()

                when {
                    savedProfile != null &&
                    savedProfile.isOnboardingCompleted &&
                    savedProfile.name.isNotBlank() &&
                    !savedProfile.name.equals("User", ignoreCase = true) -> {
                        // Returning user — restore from local profile, no server needed
                        _user.value = buildLocalUser(savedProfile)
                        _authState.value = AuthState.Authenticated(isNewUser = false)
                    }
                    savedProfile != null -> {
                        // Profile row exists but onboarding not finished
                        _user.value = buildLocalUser(savedProfile)
                        _authState.value = AuthState.Authenticated(isNewUser = true)
                    }
                    else -> {
                        // Fresh install — seed a blank profile and show onboarding
                        val emptyProfile = UserProfileEntity(id = 1)
                        authRepository.updateProfile(emptyProfile)
                        _user.value = buildLocalUser(emptyProfile)
                        _authState.value = AuthState.Authenticated(isNewUser = true)
                    }
                }
            } catch (e: Exception) {
                // Fallback: treat as new user so onboarding is shown
                _user.value = UserDto(
                    id = -1L,
                    googleSub = "",
                    email = "",
                    name = "User",
                    profilePictureUrl = ""
                )
                _authState.value = AuthState.Authenticated(isNewUser = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Optional Google Sign-In to enrich the local profile with the user's
     * real name and profile picture. The ID token is decoded **locally** — 
     * no backend server call is made. The app works entirely without this step.
     */
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val googleAuthManager = GoogleAuthManager(context)
                when (val result = googleAuthManager.getGoogleIdToken()) {
                    is GoogleAuthResult.Success -> {
                        val claims = decodeGoogleIdTokenLocally(result.idToken)
                        if (claims != null) {
                            val existing = authRepository.getProfileOnce()
                            val updatedProfile = (existing ?: UserProfileEntity(id = 1)).copy(
                                googleSub = claims.sub,
                                name = claims.name.ifBlank { existing?.name ?: "User" },
                                email = claims.email,
                                profilePictureUrl = claims.picture,
                                updatedAt = System.currentTimeMillis()
                            )
                            authRepository.updateProfile(updatedProfile)
                            // Save profile identifiers locally (no tokens needed)
                            tokenManager.saveUserSession(
                                userId = 1L,
                                googleSub = claims.sub,
                                email = claims.email,
                                name = claims.name,
                                profilePicture = claims.picture
                            )
                            _user.value = buildLocalUser(updatedProfile)
                            _authState.value = AuthState.Authenticated(
                                isNewUser = !updatedProfile.isOnboardingCompleted
                            )
                        } else {
                            _errorMessage.value = "Could not read Google profile. You can still use the app locally."
                        }
                    }
                    is GoogleAuthResult.Cancelled -> {
                        _errorMessage.value = "Google Sign-In was cancelled."
                    }
                    is GoogleAuthResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Sign-in error: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeOnboarding() {
        _isNewUser.value = false
        _authState.value = AuthState.Authenticated(isNewUser = false)
    }

    /**
     * Logout: clears the encrypted token store and resets the local Room profile
     * so onboarding is shown on next launch. Financial data (transactions, accounts,
     * loans, etc.) is also purged — giving a clean slate.
     */
    fun logout(onComplete: (() -> Unit)? = null) {
        tokenManager.clearCredentials()
        _user.value = null
        _isNewUser.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.data.local.DatabaseInitializer.purgeLegacyDemoData(database)
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                val resetProfile = UserProfileEntity(
                    id = 1,
                    name = "User",
                    email = "",
                    currencySymbol = "₹",
                    isDarkMode = false,
                    isOnboardingCompleted = false
                )
                try { authRepository.updateProfile(resetProfile) } catch (_: Exception) {}
                _authState.value = AuthState.Authenticated(isNewUser = true)
                onComplete?.invoke()
            }
        }
    }

    fun deleteAccount(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            // Local-only: purge all data then reset profile
            withContext(Dispatchers.IO) {
                try {
                    com.example.data.local.DatabaseInitializer.purgeLegacyDemoData(database)
                } catch (_: Exception) {}
            }
            _isLoading.value = false
            logout(onComplete)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private fun buildLocalUser(profile: UserProfileEntity): UserDto = UserDto(
        id = profile.id,
        googleSub = profile.googleSub.ifEmpty { "local_${profile.id}" },
        email = profile.email,
        name = profile.name.ifBlank { "User" },
        profilePictureUrl = profile.profilePictureUrl.ifEmpty { null },
        currencySymbol = profile.currencySymbol,
        isDriveConnected = false,
        lastBackupAt = null
    )

    /**
     * Decodes the Google ID token (JWT) locally to extract name/email/picture.
     * We do NOT verify the signature — the claims are used only for display (local profile).
     */
    private fun decodeGoogleIdTokenLocally(idToken: String): GoogleClaims? {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val decoded = android.util.Base64.decode(
                parts[1].replace('-', '+').replace('_', '/'),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            val json = org.json.JSONObject(String(decoded, Charsets.UTF_8))
            GoogleClaims(
                sub   = json.optString("sub",     ""),
                email = json.optString("email",   ""),
                name  = json.optString("name",    ""),
                picture = json.optString("picture", "")
            )
        } catch (_: Exception) { null }
    }

    private data class GoogleClaims(
        val sub: String,
        val email: String,
        val name: String,
        val picture: String
    )
}
