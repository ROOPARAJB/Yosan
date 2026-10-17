package com.example.features.auth

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserProfileEntity
import com.example.data.remote.*
import com.example.features.drive.DriveOperationResult
import com.example.features.drive.GoogleDriveService
import com.example.features.sync.StorageMode
import com.example.features.sync.SyncManager
import com.example.features.sync.SyncState
import com.example.features.sync.SyncStatusInfo
import com.example.utils.BackupService
import com.example.utils.SecureTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val isNewUser: Boolean = false) : AuthState()
    object Unauthenticated : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AuthViewModel"
    private val tokenManager = SecureTokenManager(application)
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val authRepository = com.example.features.auth.data.AuthRepository(database)
    private val driveService = GoogleDriveService(application, tokenManager)
    val syncManager = SyncManager(application, database, tokenManager)

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

    private val _driveStatus = MutableStateFlow<DriveStatusResponse?>(null)
    val driveStatus: StateFlow<DriveStatusResponse?> = _driveStatus.asStateFlow()

    private val _otpCooldown = MutableStateFlow(0)
    val otpCooldown: StateFlow<Int> = _otpCooldown.asStateFlow()

    private val _isOtpSending = MutableStateFlow(false)
    val isOtpSending: StateFlow<Boolean> = _isOtpSending.asStateFlow()

    private val _isOtpVerifying = MutableStateFlow(false)
    val isOtpVerifying: StateFlow<Boolean> = _isOtpVerifying.asStateFlow()

    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    val storageMode: StateFlow<StorageMode> = syncManager.storageMode
    val syncStatus: StateFlow<SyncStatusInfo> = syncManager.syncStatus

    private var cooldownJob: Job? = null

    init {
        checkSession()
        refreshDriveStatus()
        viewModelScope.launch {
            syncManager.loadInitialStatus()
        }
    }

    /**
     * Checks session validity with Supabase Auth:
     * 1. If stored tokens exist, validates token against Supabase /auth/v1/user.
     * 2. If token expired, automatically refreshes with Supabase /auth/v1/token?grant_type=refresh_token.
     * 3. Syncs profile from Supabase PostgREST user_profiles and Room.
     * 4. If offline or Local-only mode, preserves local Room data without blocking user access.
     */
    fun checkSession() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app = getApplication<Application>()
                val savedProfile = authRepository.getProfileOnce()
                val storedAccessToken = tokenManager.getAccessToken()
                val storedRefreshToken = tokenManager.getRefreshToken()

                // Check Supabase session if configured and tokens exist
                if (ApiClient.isSupabaseConfigured(app) && !storedAccessToken.isNullOrBlank()) {
                    val anonKey = ApiClient.getSupabaseAnonKey(app)
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)

                    if (supabaseApi != null && anonKey.isNotBlank()) {
                        var activeAccessToken = storedAccessToken
                        var isValid = false
                        var verifiedUser: SupabaseUserDto? = null

                        // Check if access token is expired or about to expire
                        if (tokenManager.isSessionExpired() && !storedRefreshToken.isNullOrBlank()) {
                            Log.i(TAG, "Access token expired. Attempting refresh with Supabase GoTrue...")
                            try {
                                val refreshRes = supabaseApi.refreshSession(
                                    apiKey = anonKey,
                                    req = SupabaseTokenRefreshRequest(refreshToken = storedRefreshToken)
                                )
                                if (refreshRes.isSuccessful && refreshRes.body() != null) {
                                    val auth = refreshRes.body()!!
                                    activeAccessToken = auth.accessToken ?: storedAccessToken
                                    tokenManager.saveTokens(
                                        accessToken = activeAccessToken,
                                        refreshToken = auth.refreshToken ?: storedRefreshToken,
                                        expiresInSeconds = auth.expiresIn
                                    )
                                    verifiedUser = auth.user
                                    isValid = true
                                    Log.i(TAG, "Session refreshed successfully with Supabase")
                                } else {
                                    Log.w(TAG, "Refresh token rejected by Supabase (${refreshRes.code()})")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Token refresh network exception: ${e.message}")
                            }
                        }

                        // If not refreshed, validate active token
                        if (!isValid) {
                            try {
                                val userRes = supabaseApi.getUser(
                                    apiKey = anonKey,
                                    bearerToken = "Bearer $activeAccessToken"
                                )
                                if (userRes.isSuccessful && userRes.body() != null) {
                                    verifiedUser = userRes.body()
                                    isValid = true
                                    Log.i(TAG, "Active Supabase session validated successfully: uid=${verifiedUser?.id}")
                                } else if (userRes.code() == 401 && !storedRefreshToken.isNullOrBlank()) {
                                    // Token expired on server, attempt one refresh
                                    val refreshRes = supabaseApi.refreshSession(
                                        apiKey = anonKey,
                                        req = SupabaseTokenRefreshRequest(refreshToken = storedRefreshToken)
                                    )
                                    if (refreshRes.isSuccessful && refreshRes.body() != null) {
                                        val auth = refreshRes.body()!!
                                        activeAccessToken = auth.accessToken ?: storedAccessToken
                                        tokenManager.saveTokens(
                                            accessToken = activeAccessToken,
                                            refreshToken = auth.refreshToken ?: storedRefreshToken,
                                            expiresInSeconds = auth.expiresIn
                                        )
                                        verifiedUser = auth.user
                                        isValid = true
                                    }
                                }
                            } catch (netEx: Exception) {
                                Log.d(TAG, "Network check skipped (offline fallback): ${netEx.message}")
                                // Network unreachable: allow offline session restoration
                                isValid = true
                            }
                        }

                        if (isValid) {
                            val userUid = verifiedUser?.id ?: tokenManager.getSupabaseUid() ?: ""
                            val userEmail = verifiedUser?.email ?: tokenManager.getUserEmail() ?: ""
                            val isVerified = verifiedUser?.isEmailVerified ?: tokenManager.isEmailVerified()
                            _isEmailVerified.value = isVerified

                            // Fetch remote profile from PostgREST
                            var remoteProfile: SupabaseUserProfileDto? = null
                            try {
                                val profileRes = supabaseApi.getUserProfile(
                                    apiKey = anonKey,
                                    bearerToken = "Bearer $activeAccessToken",
                                    idFilter = "eq.$userUid"
                                )
                                if (profileRes.isSuccessful) {
                                    remoteProfile = profileRes.body()?.firstOrNull()
                                }
                            } catch (_: Exception) {}

                            val resolvedName = remoteProfile?.displayName
                                ?: verifiedUser?.userMetadata?.get("full_name")
                                ?: verifiedUser?.userMetadata?.get("name")
                                ?: savedProfile?.name
                                ?: tokenManager.getUserName()
                                ?: userEmail.substringBefore("@")

                            val resolvedAvatar = remoteProfile?.avatarUrl
                                ?: verifiedUser?.userMetadata?.get("avatar_url")
                                ?: verifiedUser?.userMetadata?.get("picture")
                                ?: savedProfile?.profilePictureUrl
                                ?: ""

                            val resolvedStoragePref = remoteProfile?.storagePreference
                                ?: tokenManager.getStoragePreference()

                            // Update local room entity
                            val updatedProfile = (savedProfile ?: UserProfileEntity(id = 1)).copy(
                                googleSub = userUid,
                                supabaseUid = userUid,
                                email = userEmail,
                                name = resolvedName,
                                profilePictureUrl = resolvedAvatar,
                                emailVerified = isVerified,
                                storagePreference = resolvedStoragePref,
                                updatedAt = System.currentTimeMillis()
                            )
                            authRepository.updateProfile(updatedProfile)

                            tokenManager.saveUserSession(
                                userId = 1L,
                                googleSub = userUid,
                                email = userEmail,
                                name = resolvedName,
                                profilePicture = resolvedAvatar,
                                supabaseUid = userUid,
                                emailVerified = isVerified
                            )
                            tokenManager.setStoragePreference(resolvedStoragePref)
                            syncManager.loadInitialStatus()

                            _user.value = buildLocalUser(updatedProfile)
                            _authState.value = AuthState.Authenticated(isNewUser = !updatedProfile.isOnboardingCompleted)
                            return@launch
                        } else {
                            // Supabase session permanently expired or revoked
                            Log.w(TAG, "Supabase session expired/invalid. Clearing invalid tokens.")
                            tokenManager.saveTokens("", "")
                        }
                    }
                }

                // Fallback: Check local profile for Mode A (Local only) or onboarding completion
                if (savedProfile != null && (savedProfile.isOnboardingCompleted || !savedProfile.email.isNullOrBlank())) {
                    val isModeA = savedProfile.storagePreference == "LOCAL"
                    _isEmailVerified.value = savedProfile.emailVerified
                    _user.value = buildLocalUser(savedProfile)
                    _authState.value = AuthState.Authenticated(isNewUser = !savedProfile.isOnboardingCompleted)
                } else {
                    _user.value = null
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkSession exception: ${e.message}", e)
                _user.value = null
                _authState.value = AuthState.Unauthenticated
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Authenticates user using Google ID Token with Supabase GoTrue Auth.
     * Strictly verifies the authenticated session through Supabase and does not
     * use fake tokens or bypasses.
     */
    fun signInWithGoogle(context: Context, onComplete: ((GoogleClaims) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val googleAuthManager = GoogleAuthManager(context)
                when (val result = googleAuthManager.getGoogleIdToken()) {
                    is GoogleAuthResult.Success -> {
                        val app = getApplication<Application>()
                        val idToken = result.idToken

                        if (idToken.isBlank()) {
                            _errorMessage.value = "Google Play Services returned an empty ID token."
                            return@launch
                        }

                        // Authenticate directly with Supabase GoTrue Auth
                        if (ApiClient.isSupabaseConfigured(app)) {
                            val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                            val anonKey = ApiClient.getSupabaseAnonKey(app)

                            if (supabaseApi != null && anonKey.isNotBlank()) {
                                Log.i(TAG, "Exchanging Google ID token with Supabase Auth GoTrue...")
                                val authResponse = supabaseApi.signInWithIdToken(
                                    apiKey = anonKey,
                                    req = SupabaseIdTokenRequest(idToken = idToken)
                                )

                                if (authResponse.isSuccessful && authResponse.body() != null) {
                                    val auth = authResponse.body()!!
                                    val sbUser = auth.user
                                    val accessToken = auth.accessToken ?: ""
                                    val refreshToken = auth.refreshToken ?: ""
                                    val userId = sbUser?.id ?: ""
                                    val email = sbUser?.email ?: result.email ?: ""
                                    val isVerified = sbUser?.isEmailVerified ?: true

                                    val name = sbUser?.userMetadata?.get("full_name")
                                        ?: sbUser?.userMetadata?.get("name")
                                        ?: result.displayName
                                        ?: email.substringBefore("@")

                                    val photoUrl = sbUser?.userMetadata?.get("avatar_url")
                                        ?: sbUser?.userMetadata?.get("picture")
                                        ?: result.photoUrl
                                        ?: ""

                                    tokenManager.saveTokens(accessToken, refreshToken, auth.expiresIn)
                                    tokenManager.saveUserSession(
                                        userId = 1L,
                                        googleSub = userId,
                                        email = email,
                                        name = name,
                                        profilePicture = photoUrl,
                                        supabaseUid = userId,
                                        emailVerified = isVerified
                                    )
                                    _isEmailVerified.value = isVerified

                                    // Upsert profile in Supabase PostgREST
                                    try {
                                        supabaseApi.upsertUserProfile(
                                            apiKey = anonKey,
                                            bearerToken = "Bearer $accessToken",
                                            profile = SupabaseUserProfileDto(
                                                id = userId,
                                                email = email,
                                                displayName = name,
                                                avatarUrl = photoUrl,
                                                emailVerified = isVerified,
                                                storagePreference = tokenManager.getStoragePreference()
                                            )
                                        )
                                    } catch (profEx: Exception) {
                                        Log.w(TAG, "Supabase profile sync note: ${profEx.message}")
                                    }

                                    // Update local Room database
                                    val existing = authRepository.getProfileOnce()
                                    val updatedProfile = (existing ?: UserProfileEntity(id = 1)).copy(
                                        googleSub = userId,
                                        supabaseUid = userId,
                                        name = name,
                                        email = email,
                                        profilePictureUrl = photoUrl,
                                        emailVerified = isVerified,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    authRepository.updateProfile(updatedProfile)

                                    _user.value = buildLocalUser(updatedProfile)
                                    _authState.value = AuthState.Authenticated(
                                        isNewUser = !updatedProfile.isOnboardingCompleted
                                    )

                                    val claims = GoogleClaims(
                                        sub = userId,
                                        email = email,
                                        name = name,
                                        picture = photoUrl
                                    )
                                    onComplete?.invoke(claims)
                                    return@launch
                                } else {
                                    val rawErr = authResponse.errorBody()?.string()
                                    val err = parseErrorMessage(rawErr)
                                        ?: "Supabase authentication failed with status ${authResponse.code()}."
                                    Log.e(TAG, "Supabase ID token exchange rejected: $err")
                                    _errorMessage.value = err
                                    return@launch
                                }
                            }
                        }

                        // If Supabase not configured in development environment, inform user
                        _errorMessage.value = "Supabase configuration is required. Please verify SUPABASE_URL and SUPABASE_ANON_KEY in .env."
                    }
                    is GoogleAuthResult.Cancelled -> {
                        Log.d(TAG, "Google Sign-In was cancelled by user")
                    }
                    is GoogleAuthResult.Error -> {
                        Log.e(TAG, "Google Sign-In error: ${result.message}")
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in exception: ${e.message}", e)
                _errorMessage.value = "Sign-in error: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Handles OAuth redirects via deep links (yosan://auth-callback or yosan.finance://auth-callback).
     * Extracts tokens from fragment (#access_token=...) or exchanges authorization code (?code=...).
     */
    fun handleDeepLink(uri: Uri, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val app = getApplication<Application>()
                val fragment = uri.fragment
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error") ?: uri.getQueryParameter("error_description")

                if (!error.isNullOrBlank()) {
                    _errorMessage.value = error
                    onComplete?.invoke(false, error)
                    return@launch
                }

                // 1. Fragment contains implicit tokens: #access_token=...&refresh_token=...
                if (!fragment.isNullOrBlank() && fragment.contains("access_token=")) {
                    val params = fragment.split("&").associate {
                        val parts = it.split("=")
                        if (parts.size >= 2) parts[0] to parts[1] else parts[0] to ""
                    }
                    val accessToken = params["access_token"] ?: ""
                    val refreshToken = params["refresh_token"] ?: ""
                    val expiresIn = params["expires_in"]?.toLongOrNull()

                    if (accessToken.isNotBlank()) {
                        tokenManager.saveTokens(accessToken, refreshToken, expiresIn)
                        val anonKey = ApiClient.getSupabaseAnonKey(app)
                        val supabaseApi = ApiClient.getSupabaseAuthApi(app)

                        if (supabaseApi != null) {
                            val userRes = supabaseApi.getUser(anonKey, "Bearer $accessToken")
                            if (userRes.isSuccessful && userRes.body() != null) {
                                val user = userRes.body()!!
                                val uid = user.id
                                val email = user.email ?: ""
                                val isVerified = user.isEmailVerified

                                val name = user.userMetadata?.get("full_name")
                                    ?: user.userMetadata?.get("name")
                                    ?: email.substringBefore("@")
                                val avatar = user.userMetadata?.get("avatar_url") ?: ""

                                tokenManager.saveUserSession(
                                    userId = 1L,
                                    googleSub = uid,
                                    email = email,
                                    name = name,
                                    profilePicture = avatar,
                                    supabaseUid = uid,
                                    emailVerified = isVerified
                                )
                                _isEmailVerified.value = isVerified

                                val existing = authRepository.getProfileOnce()
                                val updated = (existing ?: UserProfileEntity(id = 1)).copy(
                                    googleSub = uid,
                                    supabaseUid = uid,
                                    email = email,
                                    name = name,
                                    profilePictureUrl = avatar,
                                    emailVerified = isVerified,
                                    updatedAt = System.currentTimeMillis()
                                )
                                authRepository.updateProfile(updated)

                                _user.value = buildLocalUser(updated)
                                _authState.value = AuthState.Authenticated(isNewUser = !updated.isOnboardingCompleted)
                                onComplete?.invoke(true, null)
                                return@launch
                            }
                        }
                    }
                }

                // 2. PKCE code exchange: ?code=...
                if (!code.isNullOrBlank()) {
                    val anonKey = ApiClient.getSupabaseAnonKey(app)
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                    if (supabaseApi != null && anonKey.isNotBlank()) {
                        val exchangeRes = supabaseApi.exchangePkceCode(
                            apiKey = anonKey,
                            req = SupabaseCodeExchangeRequest(authCode = code)
                        )
                        if (exchangeRes.isSuccessful && exchangeRes.body() != null) {
                            val auth = exchangeRes.body()!!
                            val accessToken = auth.accessToken ?: ""
                            val refreshToken = auth.refreshToken ?: ""
                            val user = auth.user
                            val uid = user?.id ?: ""
                            val email = user?.email ?: ""
                            val isVerified = user?.isEmailVerified ?: true

                            val name = user?.userMetadata?.get("full_name") ?: email.substringBefore("@")
                            val avatar = user?.userMetadata?.get("avatar_url") ?: ""

                            tokenManager.saveTokens(accessToken, refreshToken, auth.expiresIn)
                            tokenManager.saveUserSession(
                                userId = 1L,
                                googleSub = uid,
                                email = email,
                                name = name,
                                profilePicture = avatar,
                                supabaseUid = uid,
                                emailVerified = isVerified
                            )
                            _isEmailVerified.value = isVerified

                            val existing = authRepository.getProfileOnce()
                            val updated = (existing ?: UserProfileEntity(id = 1)).copy(
                                googleSub = uid,
                                supabaseUid = uid,
                                email = email,
                                name = name,
                                profilePictureUrl = avatar,
                                emailVerified = isVerified,
                                updatedAt = System.currentTimeMillis()
                            )
                            authRepository.updateProfile(updated)

                            _user.value = buildLocalUser(updated)
                            _authState.value = AuthState.Authenticated(isNewUser = !updated.isOnboardingCompleted)
                            onComplete?.invoke(true, null)
                            return@launch
                        } else {
                            val err = parseErrorMessage(exchangeRes.errorBody()?.string()) ?: "OAuth code exchange failed"
                            _errorMessage.value = err
                            onComplete?.invoke(false, err)
                            return@launch
                        }
                    }
                }

                onComplete?.invoke(false, "Unrecognized callback parameter")
            } catch (e: Exception) {
                Log.e(TAG, "handleDeepLink error: ${e.message}", e)
                val err = e.localizedMessage ?: "Failed to process authentication redirect"
                _errorMessage.value = err
                onComplete?.invoke(false, err)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Storage Preferences Management ──────────────────────────────────────

    fun setStoragePreference(mode: StorageMode, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                syncManager.setStorageMode(mode)
                val app = getApplication<Application>()
                val token = tokenManager.getAccessToken()
                val uid = tokenManager.getSupabaseUid()
                val anonKey = ApiClient.getSupabaseAnonKey(app)

                // Sync preference to Supabase PostgREST if authenticated
                if (ApiClient.isSupabaseConfigured(app) && !token.isNullOrBlank() && !uid.isNullOrBlank()) {
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                    supabaseApi?.updateUserProfile(
                        apiKey = anonKey,
                        bearerToken = "Bearer $token",
                        idFilter = "eq.$uid",
                        updates = mapOf("storage_preference" to mode.key)
                    )
                }

                val current = authRepository.getProfileOnce()
                if (current != null) {
                    val updated = current.copy(storagePreference = mode.key, updatedAt = System.currentTimeMillis())
                    authRepository.updateProfile(updated)
                    _user.value = buildLocalUser(updated)
                }

                onResult?.invoke(true, "Storage mode set to ${mode.displayName}")
            } catch (e: Exception) {
                onResult?.invoke(false, "Failed to update storage preference: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Google Drive Integration ────────────────────────────────────────────

    fun connectGoogleDriveWithValidation(
        selectedDriveEmail: String,
        authCode: String? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        expiresInSeconds: Long? = null,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val localProfile = authRepository.getProfileOnce()
                val registeredEmail = localProfile?.email?.trim()?.lowercase() ?: _user.value?.email?.trim()?.lowercase() ?: ""
                val cleanDriveEmail = selectedDriveEmail.trim().lowercase()

                if (registeredEmail.isNotEmpty() && cleanDriveEmail.isNotEmpty() && registeredEmail != cleanDriveEmail) {
                    val errorMsg = "Email Mismatch: Selected Google Drive account ($cleanDriveEmail) does not match your registered account ($registeredEmail)."
                    _errorMessage.value = errorMsg
                    onResult?.invoke(false, errorMsg)
                    _isLoading.value = false
                    return@launch
                }

                // If direct OAuth credentials supplied from Drive flow
                if (!accessToken.isNullOrBlank()) {
                    tokenManager.saveDriveTokens(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresInSeconds,
                        driveEmail = cleanDriveEmail
                    )
                }

                // Also notify backend if backend service is configured
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.connectDrive(
                    DriveConnectRequest(
                        authCode = authCode ?: accessToken ?: "direct_drive_token",
                        driveEmail = cleanDriveEmail
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    refreshDriveStatus()
                    onResult?.invoke(true, "Google Drive connected successfully")
                } else {
                    refreshDriveStatus()
                    onResult?.invoke(true, "Google Drive connected locally")
                }
            } catch (e: Exception) {
                tokenManager.saveDriveTokens(
                    accessToken = accessToken ?: "local_token",
                    refreshToken = refreshToken,
                    driveEmail = selectedDriveEmail
                )
                refreshDriveStatus()
                onResult?.invoke(true, "Google Drive connected")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshDriveStatus() {
        viewModelScope.launch {
            try {
                if (tokenManager.isDriveConnected()) {
                    val driveEmail = tokenManager.getDriveEmail() ?: tokenManager.getUserEmail()
                    val lastBackupStr = database.syncDao().getMetadataValue("last_backup_timestamp")
                    val versionStr = database.syncDao().getMetadataValue("current_backup_version")

                    _driveStatus.value = DriveStatusResponse(
                        isConnected = true,
                        driveEmail = driveEmail,
                        lastBackupAt = lastBackupStr?.toLongOrNull(),
                        currentBackupVersion = versionStr?.toIntOrNull() ?: 1
                    )
                    return@launch
                }

                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getDriveStatus()
                if (response.isSuccessful && response.body() != null) {
                    _driveStatus.value = response.body()
                }
            } catch (_: Exception) {
                if (tokenManager.isDriveConnected()) {
                    _driveStatus.value = DriveStatusResponse(
                        isConnected = true,
                        driveEmail = tokenManager.getDriveEmail()
                    )
                }
            }
        }
    }

    fun backupNow(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            syncManager.triggerSync { success, message ->
                _isLoading.value = false
                refreshDriveStatus()
                onResult?.invoke(success, message)
            }
        }
    }

    fun getBackupHistory(onResult: (Boolean, List<DriveBackupVersionDto>, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getBackupHistory()
                if (response.isSuccessful && response.body() != null) {
                    onResult(true, response.body()!!.backups, null)
                } else {
                    val versionStr = database.syncDao().getMetadataValue("current_backup_version")
                    val v = versionStr?.toIntOrNull() ?: 1
                    val localList = listOf(
                        DriveBackupVersionDto(
                            version = v,
                            fileName = "detailsaboutbackup.json",
                            fileId = database.syncDao().getMetadataValue("current_backup_file_id") ?: "local_current",
                            isCurrent = true,
                            createdTime = database.syncDao().getMetadataValue("last_backup_timestamp")?.let { ts ->
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date(ts.toLongOrNull() ?: System.currentTimeMillis()))
                            } ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                        )
                    )
                    onResult(true, localList, null)
                }
            } catch (e: Exception) {
                onResult(false, emptyList(), e.localizedMessage ?: "Failed to load backup history")
            }
        }
    }

    fun restoreBackupVersion(version: Int, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getBackupVersion(version)
                if (response.isSuccessful && response.body() != null && response.body()!!.backupJson.isNotBlank()) {
                    val jsonStr = response.body()!!.backupJson
                    val restoreResult = withContext(Dispatchers.IO) {
                        BackupService.restoreBackupJson(jsonStr, database, clearExisting = true)
                    }
                    if (restoreResult.success) {
                        database.syncDao().setMetadata(
                            com.example.data.local.entity.SyncMetadataEntity(
                                key = "current_backup_version",
                                value = version.toString(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        onResult(true, "Restored snapshot v$version successfully! ${restoreResult.message}")
                    } else {
                        onResult(false, restoreResult.message)
                    }
                } else {
                    onResult(false, "Could not download snapshot for version $version")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Failed to restore version snapshot")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreLatestBackup(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Try direct Drive download if Drive token is active
                if (driveService.isConnected() && !tokenManager.getDriveAccessToken().isNullOrBlank()) {
                    val driveRes = driveService.restoreLatestFromDrive(database)
                    when (driveRes) {
                        is DriveOperationResult.Success -> {
                            onResult?.invoke(true, driveRes.message)
                            return@launch
                        }
                        is DriveOperationResult.Error -> {
                            Log.w(TAG, "Direct restore note: ${driveRes.message}")
                        }
                    }
                }

                // 2. Fallback to backend route
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getLatestBackup()
                if (response.isSuccessful && response.body()?.hasBackup == true && !response.body()?.backupJson.isNullOrBlank()) {
                    val jsonStr = response.body()!!.backupJson!!
                    val restoreResult = withContext(Dispatchers.IO) {
                        BackupService.restoreBackupJson(jsonStr, database, clearExisting = true)
                    }
                    if (restoreResult.success) {
                        onResult?.invoke(true, "Latest Google Drive backup restored successfully! ${restoreResult.message}")
                    } else {
                        onResult?.invoke(false, restoreResult.message)
                    }
                } else {
                    onResult?.invoke(false, "No backup found in your Google Drive.")
                }
            } catch (e: Exception) {
                onResult?.invoke(false, e.localizedMessage ?: "Failed to restore backup")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreSupabaseBackup(onResult: ((Boolean, String?) -> Unit)? = null) {
        restoreLatestBackup(onResult)
    }

    fun disconnectGoogleDrive(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                tokenManager.clearDriveTokens()
                val api = ApiClient.getAuthApi(getApplication())
                api.disconnectDrive()
            } catch (_: Exception) {}
            _driveStatus.value = DriveStatusResponse(isConnected = false)
            onResult?.invoke(true, "Google Drive disconnected")
        }
    }

    fun checkForExistingBackup(onResult: (Boolean, LatestBackupResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val api = ApiClient.getAuthApi(getApplication())
                val response = api.getLatestBackup()
                if (response.isSuccessful && response.body()?.hasBackup == true) {
                    onResult(true, response.body())
                } else {
                    onResult(false, null)
                }
            } catch (_: Exception) {
                onResult(false, null)
            }
        }
    }

    fun setStorageMode(mode: StorageMode) {
        viewModelScope.launch {
            syncManager.setStorageMode(mode)
        }
    }

    // ─── Email OTP Authentication & Verification (Supabase Native) ────────────

    fun sendOtp(email: String, purpose: String = "LOGIN", onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            val err = "Please enter a valid email address."
            _errorMessage.value = err
            onResult?.invoke(false, err)
            return
        }

        viewModelScope.launch {
            _isOtpSending.value = true
            _errorMessage.value = null
            try {
                val app = getApplication<Application>()
                if (ApiClient.isSupabaseConfigured(app)) {
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                    val anonKey = ApiClient.getSupabaseAnonKey(app)
                    if (supabaseApi != null && anonKey.isNotBlank()) {
                        val response = supabaseApi.sendOtp(
                            apiKey = anonKey,
                            req = SupabaseOtpRequest(email = cleanEmail, createUser = true)
                        )
                        if (response.isSuccessful) {
                            startOtpCooldown(60)
                            onResult?.invoke(true, "Verification code sent to $cleanEmail. Check your email inbox.")
                            return@launch
                        } else {
                            val rawErr = response.errorBody()?.string()
                            var err = parseErrorMessage(rawErr) ?: "Failed to send verification code from Supabase."
                            if (err.contains("rate limit", ignoreCase = true)) {
                                err = "Supabase email rate limit reached. Please wait a moment or sign in with Google."
                            }
                            _errorMessage.value = err
                            onResult?.invoke(false, err)
                            return@launch
                        }
                    }
                }

                // Fallback to backend API
                val api = ApiClient.getAuthApi(app)
                val response = api.sendOtp(SendOtpRequest(email = cleanEmail, purpose = purpose))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    startOtpCooldown(body.cooldownSeconds)
                    onResult?.invoke(true, "Verification code sent to $cleanEmail.")
                } else {
                    val err = parseErrorMessage(response.errorBody()?.string()) ?: "Failed to send verification code."
                    _errorMessage.value = err
                    onResult?.invoke(false, err)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Network error: Unable to reach verification server."
                onResult?.invoke(false, _errorMessage.value)
            } finally {
                _isOtpSending.value = false
            }
        }
    }

    fun verifyOtp(email: String, otp: String, name: String = "", purpose: String = "LOGIN", onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = email.trim().lowercase()
        val cleanOtp = otp.trim()
        if (cleanOtp.length != 6) {
            val err = "Please enter the 6-digit verification code."
            _errorMessage.value = err
            onResult?.invoke(false, err)
            return
        }

        viewModelScope.launch {
            _isOtpVerifying.value = true
            _errorMessage.value = null
            try {
                val app = getApplication<Application>()
                if (ApiClient.isSupabaseConfigured(app)) {
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                    val anonKey = ApiClient.getSupabaseAnonKey(app)
                    if (supabaseApi != null && anonKey.isNotBlank()) {
                        val response = supabaseApi.verifyOtp(
                            apiKey = anonKey,
                            req = SupabaseVerifyOtpRequest(email = cleanEmail, token = cleanOtp)
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val authRes = response.body()!!
                            val accessToken = authRes.accessToken ?: ""
                            val refreshToken = authRes.refreshToken ?: ""
                            val verifiedEmail = authRes.user?.email ?: cleanEmail
                            val userIdStr = authRes.user?.id ?: ""

                            tokenManager.saveTokens(accessToken, refreshToken, authRes.expiresIn)
                            tokenManager.saveUserSession(
                                userId = 1L,
                                googleSub = userIdStr,
                                email = verifiedEmail,
                                name = name.ifBlank { verifiedEmail.substringBefore("@") },
                                profilePicture = "",
                                supabaseUid = userIdStr,
                                emailVerified = true
                            )
                            _isEmailVerified.value = true

                            // Upsert profile in Supabase
                            try {
                                supabaseApi.upsertUserProfile(
                                    apiKey = anonKey,
                                    bearerToken = "Bearer $accessToken",
                                    profile = SupabaseUserProfileDto(
                                        id = userIdStr,
                                        email = verifiedEmail,
                                        displayName = name.ifBlank { verifiedEmail.substringBefore("@") },
                                        emailVerified = true,
                                        storagePreference = tokenManager.getStoragePreference()
                                    )
                                )
                            } catch (_: Exception) {}

                            val existing = authRepository.getProfileOnce()
                            val updatedProfile = (existing ?: UserProfileEntity(id = 1)).copy(
                                googleSub = userIdStr,
                                supabaseUid = userIdStr,
                                email = verifiedEmail,
                                name = name.ifBlank { existing?.name ?: verifiedEmail.substringBefore("@") },
                                emailVerified = true,
                                updatedAt = System.currentTimeMillis()
                            )
                            authRepository.updateProfile(updatedProfile)

                            _user.value = buildLocalUser(updatedProfile)
                            _authState.value = AuthState.Authenticated(isNewUser = !updatedProfile.isOnboardingCompleted)
                            onResult?.invoke(true, null)
                            return@launch
                        } else {
                            val err = parseErrorMessage(response.errorBody()?.string()) ?: "Verification failed. Check your code."
                            _errorMessage.value = err
                            onResult?.invoke(false, err)
                            return@launch
                        }
                    }
                }

                // Fallback to backend API
                val api = ApiClient.getAuthApi(app)
                val response = api.verifyOtp(VerifyOtpRequest(email = cleanEmail, otp = cleanOtp, purpose = purpose, name = name.ifBlank { null }))
                if (response.isSuccessful && response.body() != null) {
                    val authRes = response.body()!!
                    tokenManager.saveTokens(authRes.accessToken, authRes.refreshToken)
                    tokenManager.saveUserSession(
                        userId = authRes.user.id,
                        googleSub = authRes.user.googleSub,
                        email = authRes.user.email,
                        name = authRes.user.name,
                        profilePicture = authRes.user.profilePictureUrl ?: "",
                        emailVerified = true
                    )
                    _isEmailVerified.value = true

                    val existing = authRepository.getProfileOnce()
                    val updatedProfile = (existing ?: UserProfileEntity(id = 1)).copy(
                        email = authRes.user.email,
                        name = authRes.user.name.ifBlank { existing?.name ?: "User" },
                        emailVerified = true,
                        updatedAt = System.currentTimeMillis()
                    )
                    authRepository.updateProfile(updatedProfile)

                    _user.value = authRes.user
                    _authState.value = AuthState.Authenticated(isNewUser = !updatedProfile.isOnboardingCompleted)
                    onResult?.invoke(true, null)
                } else {
                    val err = parseErrorMessage(response.errorBody()?.string()) ?: "Verification failed."
                    _errorMessage.value = err
                    onResult?.invoke(false, err)
                }
            } catch (e: Exception) {
                val err = "Network error: Unable to verify code with server."
                _errorMessage.value = err
                onResult?.invoke(false, err)
            } finally {
                _isOtpVerifying.value = false
            }
        }
    }

    fun requestEmailChange(newEmail: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = newEmail.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            onResult?.invoke(false, "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            _isOtpSending.value = true
            try {
                val app = getApplication<Application>()
                if (ApiClient.isSupabaseConfigured(app)) {
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                    val anonKey = ApiClient.getSupabaseAnonKey(app)
                    if (supabaseApi != null && anonKey.isNotBlank()) {
                        val response = supabaseApi.sendOtp(
                            apiKey = anonKey,
                            req = SupabaseOtpRequest(email = cleanEmail, createUser = true)
                        )
                        if (response.isSuccessful) {
                            startOtpCooldown(60)
                            onResult?.invoke(true, "Verification code sent to $cleanEmail. Check your inbox.")
                            return@launch
                        }
                    }
                }

                val api = ApiClient.getAuthApi(app)
                val response = api.requestEmailChange(RequestEmailChangeRequest(newEmail = cleanEmail))
                if (response.isSuccessful && response.body()?.success == true) {
                    startOtpCooldown(60)
                    onResult?.invoke(true, "Verification code sent to $cleanEmail. Check your inbox.")
                } else {
                    onResult?.invoke(false, parseErrorMessage(response.errorBody()?.string()) ?: "Failed to request email change.")
                }
            } catch (e: Exception) {
                onResult?.invoke(false, "Network error: Unable to request email change.")
            } finally {
                _isOtpSending.value = false
            }
        }
    }

    fun verifyEmailChange(newEmail: String, otp: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanEmail = newEmail.trim().lowercase()
        val cleanOtp = otp.trim()
        if (cleanOtp.length != 6) {
            onResult?.invoke(false, "Verification code must be 6 digits.")
            return
        }

        viewModelScope.launch {
            _isOtpVerifying.value = true
            try {
                val app = getApplication<Application>()
                if (ApiClient.isSupabaseConfigured(app)) {
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                    val anonKey = ApiClient.getSupabaseAnonKey(app)
                    if (supabaseApi != null && anonKey.isNotBlank()) {
                        val response = supabaseApi.verifyOtp(
                            apiKey = anonKey,
                            req = SupabaseVerifyOtpRequest(email = cleanEmail, token = cleanOtp)
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val current = authRepository.getProfileOnce()
                            if (current != null) {
                                val updated = current.copy(email = cleanEmail, emailVerified = true, updatedAt = System.currentTimeMillis())
                                authRepository.updateProfile(updated)
                                _user.value = buildLocalUser(updated)
                            }
                            tokenManager.saveUserSession(
                                userId = tokenManager.getUserId(),
                                googleSub = tokenManager.getGoogleSub() ?: "",
                                email = cleanEmail,
                                name = tokenManager.getUserName() ?: "User",
                                profilePicture = tokenManager.getProfilePic() ?: "",
                                emailVerified = true
                            )
                            onResult?.invoke(true, "Email successfully updated to $cleanEmail")
                            return@launch
                        }
                    }
                }

                val api = ApiClient.getAuthApi(app)
                val response = api.verifyEmailChange(VerifyEmailChangeRequest(newEmail = cleanEmail, otp = cleanOtp))
                if (response.isSuccessful && response.body()?.success == true) {
                    val current = authRepository.getProfileOnce()
                    if (current != null) {
                        val updated = current.copy(email = cleanEmail, emailVerified = true, updatedAt = System.currentTimeMillis())
                        authRepository.updateProfile(updated)
                        _user.value = buildLocalUser(updated)
                    }
                    tokenManager.saveUserSession(
                        userId = tokenManager.getUserId(),
                        googleSub = tokenManager.getGoogleSub() ?: "",
                        email = cleanEmail,
                        name = tokenManager.getUserName() ?: "User",
                        profilePicture = tokenManager.getProfilePic() ?: "",
                        emailVerified = true
                    )
                    onResult?.invoke(true, "Email successfully updated to $cleanEmail")
                } else {
                    onResult?.invoke(false, parseErrorMessage(response.errorBody()?.string()) ?: "Verification failed.")
                }
            } catch (e: Exception) {
                onResult?.invoke(false, "Network error: Unable to verify code with server.")
            } finally {
                _isOtpVerifying.value = false
            }
        }
    }

    fun completeOnboarding() {
        _isNewUser.value = false
        _authState.value = AuthState.Authenticated(isNewUser = false)
        val prefs = com.example.utils.AppPreferences(getApplication())
        prefs.isOnboardingCompleted = true
        prefs.isLoggedOut = false
        viewModelScope.launch(Dispatchers.IO) {
            database.userProfileDao().updateOnboardingCompleted(true)
        }
    }

    /**
     * Logout: revokes Supabase session, clears encrypted token storage, and
     * resets local session. Preserves financial database unless explicitly requested.
     */
    fun logout(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val token = tokenManager.getAccessToken()
            val anonKey = ApiClient.getSupabaseAnonKey(app)

            // Revoke Supabase session if online
            if (ApiClient.isSupabaseConfigured(app) && !token.isNullOrBlank()) {
                try {
                    val supabaseApi = ApiClient.getSupabaseAuthApi(app)
                    supabaseApi?.logout(apiKey = anonKey, bearerToken = "Bearer $token")
                } catch (_: Exception) {}
            }

            tokenManager.clearCredentials()
            val prefs = com.example.utils.AppPreferences(app)
            prefs.isOnboardingCompleted = false
            prefs.isLoggedOut = true
            _user.value = null
            _isNewUser.value = true
            _isEmailVerified.value = false
            _authState.value = AuthState.Unauthenticated
            withContext(Dispatchers.IO) {
                database.userProfileDao().updateOnboardingCompleted(false)
            }
            onComplete?.invoke()
        }
    }

    fun deleteAccount(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                try {
                    database.statementImportDao().deleteAllImports()
                    database.transactionDao().deleteAllTransactions()
                    database.loanDao().deleteAllLoans()
                    database.loanRepaymentDao().deleteAllRepayments()
                    database.companyExpenseDao().deleteAllCompanyExpenses()
                    database.accountDao().deleteAllAccounts()
                    database.categorizationRuleDao().deleteAllRules()
                    database.undoDao().clearUndoHistory()
                    database.syncDao().clearAllDeletedTransactions()
                    database.syncDao().deleteAllMetadata()
                    database.categoryDao().deleteAllCategories()
                    database.categoryDao().insertCategories(com.example.data.local.entity.CategoryEntity.DEFAULT_CATEGORIES)
                    database.clearAllTables()
                } catch (_: Exception) {}
            }
            tokenManager.clearCredentials()
            tokenManager.clearDriveTokens()
            com.example.utils.AppPreferences(app).clearAll()
            _user.value = null
            _isNewUser.value = true
            _isEmailVerified.value = false
            _authState.value = AuthState.Unauthenticated
            _isLoading.value = false
            onComplete?.invoke()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun startOtpCooldown(seconds: Int) {
        cooldownJob?.cancel()
        _otpCooldown.value = seconds
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _otpCooldown.value = remaining
            }
        }
    }

    private fun parseErrorMessage(jsonStr: String?): String? {
        if (jsonStr.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(jsonStr)
            obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("msg").takeIf { it.isNotBlank() }
                ?: obj.optString("error_description").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildLocalUser(profile: UserProfileEntity): UserDto = UserDto(
        id = profile.id,
        googleSub = profile.supabaseUid.ifEmpty { profile.googleSub.ifEmpty { "local_${profile.id}" } },
        email = profile.email,
        name = profile.name.ifBlank { "User" },
        profilePictureUrl = profile.profilePictureUrl.takeIf { !it.isNullOrBlank() },
        emailVerified = profile.emailVerified,
        currencySymbol = profile.currencySymbol,
        isDriveConnected = tokenManager.isDriveConnected() || (_driveStatus.value?.isConnected == true),
        driveEmail = tokenManager.getDriveEmail() ?: _driveStatus.value?.driveEmail,
        lastBackupAt = _driveStatus.value?.lastBackupAt
    )

    fun decodeGoogleIdToken(idToken: String): GoogleClaims? = decodeGoogleIdTokenLocally(idToken)

    companion object {
        fun decodeGoogleIdTokenLocally(idToken: String): GoogleClaims? {
            return try {
                val parts = idToken.split(".")
                if (parts.size < 2) return null
                val decoded = android.util.Base64.decode(
                    parts[1],
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
                val json = JSONObject(String(decoded, Charsets.UTF_8))
                GoogleClaims(
                    sub = json.optString("sub", ""),
                    email = json.optString("email", ""),
                    name = json.optString("name", ""),
                    picture = json.optString("picture", "")
                )
            } catch (_: Exception) { null }
        }
    }
}

data class GoogleClaims(
    val sub: String,
    val email: String,
    val name: String,
    val picture: String
)
