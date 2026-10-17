package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class SupabaseOtpRequest(
    @field:Json(name = "email") val email: String,
    @field:Json(name = "create_user") val createUser: Boolean = true
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyOtpRequest(
    @field:Json(name = "type") val type: String = "email",
    @field:Json(name = "email") val email: String,
    @field:Json(name = "token") val token: String
)

@JsonClass(generateAdapter = true)
data class SupabaseUserDto(
    @field:Json(name = "id") val id: String = "",
    @field:Json(name = "email") val email: String? = null,
    @field:Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null,
    @field:Json(name = "confirmed_at") val confirmedAt: String? = null,
    @field:Json(name = "user_metadata") val userMetadata: Map<String, String>? = null,
    @field:Json(name = "created_at") val createdAt: String? = null,
    @field:Json(name = "updated_at") val updatedAt: String? = null
) {
    val isEmailVerified: Boolean
        get() = !emailConfirmedAt.isNullOrBlank() || !confirmedAt.isNullOrBlank()
}

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @field:Json(name = "access_token") val accessToken: String? = null,
    @field:Json(name = "token_type") val tokenType: String? = null,
    @field:Json(name = "expires_in") val expiresIn: Long? = null,
    @field:Json(name = "expires_at") val expiresAt: Long? = null,
    @field:Json(name = "refresh_token") val refreshToken: String? = null,
    @field:Json(name = "user") val user: SupabaseUserDto? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseTokenRefreshRequest(
    @field:Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseCodeExchangeRequest(
    @field:Json(name = "auth_code") val authCode: String,
    @field:Json(name = "code_verifier") val codeVerifier: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabasePasswordAuthRequest(
    @field:Json(name = "email") val email: String,
    @field:Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseSignUpRequest(
    @field:Json(name = "email") val email: String,
    @field:Json(name = "password") val password: String,
    @field:Json(name = "data") val data: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseRpcValidateRequest(
    @field:Json(name = "user_email") val userEmail: String,
    @field:Json(name = "user_name") val userName: String = ""
)

@JsonClass(generateAdapter = true)
data class SupabaseRpcValidateResponse(
    @field:Json(name = "user_id") val userId: String? = null,
    @field:Json(name = "email") val email: String? = null,
    @field:Json(name = "name") val name: String? = null,
    @field:Json(name = "status") val status: String? = null,
    @field:Json(name = "email_verified") val emailVerified: Boolean? = null,
    @field:Json(name = "storage_preference") val storagePreference: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseIdTokenRequest(
    @field:Json(name = "provider") val provider: String = "google",
    @field:Json(name = "id_token") val idToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseUserProfileDto(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "email") val email: String,
    @field:Json(name = "display_name") val displayName: String? = null,
    @field:Json(name = "avatar_url") val avatarUrl: String? = null,
    @field:Json(name = "email_verified") val emailVerified: Boolean = false,
    @field:Json(name = "storage_preference") val storagePreference: String = "LOCAL",
    @field:Json(name = "currency_symbol") val currencySymbol: String? = "₹",
    @field:Json(name = "created_at") val createdAt: String? = null,
    @field:Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseBackupMetadataDto(
    @field:Json(name = "user_id") val userId: String,
    @field:Json(name = "drive_folder_id") val driveFolderId: String? = null,
    @field:Json(name = "current_backup_file_id") val currentBackupFileId: String? = null,
    @field:Json(name = "backup_version") val backupVersion: Int = 1,
    @field:Json(name = "last_backup_at") val lastBackupAt: Long = 0L,
    @field:Json(name = "schema_version") val schemaVersion: Int = 1,
    @field:Json(name = "checksum") val checksum: String? = null,
    @field:Json(name = "sync_status") val syncStatus: String? = "SYNCED"
)

interface SupabaseAuthApi {

    @POST("auth/v1/otp")
    suspend fun sendOtp(
        @Header("apikey") apiKey: String,
        @Body req: SupabaseOtpRequest
    ): Response<Unit>

    @POST("auth/v1/verify")
    suspend fun verifyOtp(
        @Header("apikey") apiKey: String,
        @Body req: SupabaseVerifyOtpRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=id_token")
    suspend fun signInWithIdToken(
        @Header("apikey") apiKey: String,
        @Body req: SupabaseIdTokenRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshSession(
        @Header("apikey") apiKey: String,
        @Body req: SupabaseTokenRefreshRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=pkce")
    suspend fun exchangePkceCode(
        @Header("apikey") apiKey: String,
        @Body req: SupabaseCodeExchangeRequest
    ): Response<SupabaseAuthResponse>

    @GET("auth/v1/user")
    suspend fun getUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String
    ): Response<SupabaseUserDto>

    @POST("auth/v1/logout")
    suspend fun logout(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String
    ): Response<Unit>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Header("apikey") apiKey: String,
        @Body req: SupabasePasswordAuthRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") apiKey: String,
        @Body req: SupabaseSignUpRequest
    ): Response<SupabaseAuthResponse>

    @POST("rest/v1/rpc/validate_yosan_user")
    suspend fun validateUserRpc(
        @Header("apikey") apiKey: String,
        @Body req: SupabaseRpcValidateRequest
    ): Response<SupabaseRpcValidateResponse>

    // Profile Management (PostgREST)
    @GET("rest/v1/user_profiles")
    suspend fun getUserProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String = "", // e.g. "eq.<uuid>"
        @Query("select") select: String = "*"
    ): Response<List<SupabaseUserProfileDto>>

    @POST("rest/v1/user_profiles")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertUserProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Body profile: SupabaseUserProfileDto
    ): Response<Unit>

    @PATCH("rest/v1/user_profiles")
    suspend fun updateUserProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("id") idFilter: String,
        @Body updates: Map<String, String>
    ): Response<Unit>

    // Backup Metadata
    @POST("rest/v1/user_backup_metadata")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun saveBackupMetadata(
        @Header("apikey") apiKey: String,
        @Header("Authorization") userBearerToken: String,
        @Body metadata: SupabaseBackupMetadataDto
    ): Response<Unit>

    @GET("rest/v1/user_backup_metadata?select=*")
    suspend fun getBackupMetadata(
        @Header("apikey") apiKey: String,
        @Header("Authorization") userBearerToken: String
    ): Response<List<SupabaseBackupMetadataDto>>
}
