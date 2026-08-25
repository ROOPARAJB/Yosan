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
    private val authApi = ApiClient.getAuthApi(application)
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

    private suspend fun fallbackToMockAuth() {
        val savedProfile = try {
            authRepository.getProfileOnce()
        } catch (e: Exception) {
            null
        }
        val profileName = savedProfile?.name?.takeIf { it.isNotBlank() } ?: "User"
        val mockUser = UserDto(
            id = 999L,
            googleSub = "mock_google_sub",
            email = savedProfile?.email ?: "",
            name = profileName,
            profilePictureUrl = ""
        )
        _user.value = mockUser
        // Do NOT save mock_access_token as refresh token — that tricks checkSession() into
        // attempting a real network call on next launch, then failing into an infinite loading loop.
        tokenManager.saveUserSession(999L, "mock_google_sub", savedProfile?.email ?: "", profileName, "")

        // Always sync profile to Room to unblock the userProfile == null loading screen
        try {
            syncRoomUserProfile(mockUser)
        } catch (_: Exception) {}

        val needsOnboarding = profileName.equals("User", ignoreCase = true)
        _authState.value = AuthState.Authenticated(isNewUser = needsOnboarding)
        _isLoading.value = false
    }

    fun checkSession() {
        viewModelScope.launch {
            _isLoading.value = true
            val refreshToken = tokenManager.getRefreshToken()

            if (refreshToken.isNullOrEmpty()) {
                fallbackToMockAuth()
                return@launch
            }

            try {
                val response = authApi.getCurrentUser()
                if (response.isSuccessful && response.body() != null) {
                    val userDto = response.body()!!.user
                    _user.value = userDto
                    syncRoomUserProfile(userDto)
                    _authState.value = AuthState.Authenticated(isNewUser = false)
                } else {
                    refreshSession()
                }
            } catch (e: Exception) {
                // Offline fallback with saved local secure profile if available
                val savedSub = tokenManager.getGoogleSub()
                val savedEmail = tokenManager.getUserEmail()
                val savedName = tokenManager.getUserName()

                if (!savedSub.isNullOrEmpty() && !savedEmail.isNullOrEmpty()) {
                    val fallbackUser = UserDto(
                        id = tokenManager.getUserId(),
                        googleSub = savedSub,
                        email = savedEmail,
                        name = savedName ?: "User",
                        profilePictureUrl = tokenManager.getProfilePic()
                    )
                    _user.value = fallbackUser
                    // Always sync to Room to prevent userProfile == null loading loop
                    try { syncRoomUserProfile(fallbackUser) } catch (_: Exception) {}
                    _authState.value = AuthState.Authenticated(isNewUser = false)
                } else {
                    fallbackToMockAuth()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val mockToken = "mock_id_token_109123456789012345678_user"
            authenticateWithBackend(mockToken)
        }
    }

    private suspend fun authenticateWithBackend(idToken: String) {
        try {
            val response = authApi.authenticateGoogle(GoogleAuthRequest(idToken))
            if (response.isSuccessful && response.body() != null) {
                val authBody = response.body()!!
                tokenManager.saveTokens(authBody.accessToken, authBody.refreshToken)
                tokenManager.saveUserSession(
                    authBody.user.id,
                    authBody.user.googleSub,
                    authBody.user.email,
                    authBody.user.name,
                    authBody.user.profilePictureUrl ?: ""
                )

                _user.value = authBody.user
                _isNewUser.value = authBody.isNewUser
                syncRoomUserProfile(authBody.user)

                _authState.value = AuthState.Authenticated(isNewUser = authBody.isNewUser)
            } else {
                _errorMessage.value = "Backend Google Token Verification Failed"
                fallbackToMockAuth()
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Authentication failed. Could not connect to backend server."
            fallbackToMockAuth()
        } finally {
            _isLoading.value = false
        }
    }


    fun completeOnboarding() {
        _isNewUser.value = false
        _authState.value = AuthState.Authenticated(isNewUser = false)
    }

    fun refreshSession() {
        viewModelScope.launch {
            val refreshToken = tokenManager.getRefreshToken() ?: return@launch
            try {
                val response = authApi.refreshSession(RefreshTokenRequest(refreshToken))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    _authState.value = AuthState.Authenticated(isNewUser = false)
                } else {
                    logout()
                }
            } catch (e: Exception) {
                logout()
            }
        }
    }

    fun logout() {
        val refreshToken = tokenManager.getRefreshToken()
        tokenManager.clearCredentials()
        _user.value = null
        _isNewUser.value = false

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!refreshToken.isNullOrEmpty()) {
                    authApi.logout(LogoutRequest(refreshToken))
                }
            } catch (_: Exception) {}
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.data.local.DatabaseInitializer.purgeLegacyDemoData(database)
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                // Reset profile to trigger onboarding on next launch without auto-re-authenticating
                val resetProfile = com.example.data.local.entity.UserProfileEntity(
                    id = 1,
                    name = "User",
                    email = "",
                    currencySymbol = "₹"
                )
                try { authRepository.updateProfile(resetProfile) } catch (_: Exception) {}
                _authState.value = AuthState.Authenticated(isNewUser = true)
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    authApi.deleteAccount()
                } catch (_: Exception) {}
            }
            _isLoading.value = false
            logout()
        }
    }




    fun clearError() {
        _errorMessage.value = null
    }

    private suspend fun syncRoomUserProfile(userDto: UserDto) {
        val existing = authRepository.getProfileOnce()
        authRepository.updateProfile(
            UserProfileEntity(
                id = 1,
                googleSub = userDto.googleSub,
                name = userDto.name,
                email = userDto.email,
                profilePictureUrl = userDto.profilePictureUrl ?: "",
                currencySymbol = userDto.currencySymbol,
                isDarkMode = existing?.isDarkMode ?: false,
                isPrivacyBlurEnabled = existing?.isPrivacyBlurEnabled ?: true,
                blurTimeoutSeconds = existing?.blurTimeoutSeconds ?: 5,
                isOnboardingCompleted = existing?.isOnboardingCompleted ?: false,
                dashboardCardsConfig = existing?.dashboardCardsConfig ?: "BALANCE:true,OFFICIAL:true,SPENDING:true,MONTHLY:true,INSIGHTS:true,LOANS:true,RECENT:true",
                updatedAt = System.currentTimeMillis()
            )
        )
    }


}
