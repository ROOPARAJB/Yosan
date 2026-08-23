package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class GoogleAuthRequest(
    @field:Json(name = "idToken") val idToken: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @field:Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class LogoutRequest(
    @field:Json(name = "refreshToken") val refreshToken: String?
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @field:Json(name = "id") val id: Long,
    @field:Json(name = "googleSub") val googleSub: String,
    @field:Json(name = "email") val email: String,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "profilePictureUrl") val profilePictureUrl: String? = null,
    @field:Json(name = "emailVerified") val emailVerified: Boolean = true,
    @field:Json(name = "currencySymbol") val currencySymbol: String = "₹",
    @field:Json(name = "timezone") val timezone: String = "Asia/Kolkata",
    @field:Json(name = "theme") val theme: String = "SYSTEM",
    @field:Json(name = "isDriveConnected") val isDriveConnected: Boolean = false,
    @field:Json(name = "lastBackupAt") val lastBackupAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @field:Json(name = "isNewUser") val isNewUser: Boolean = false,
    @field:Json(name = "user") val user: UserDto,
    @field:Json(name = "accessToken") val accessToken: String,
    @field:Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class UserResponse(
    @field:Json(name = "user") val user: UserDto
)

@JsonClass(generateAdapter = true)
data class DriveConnectRequest(
    @field:Json(name = "authCode") val authCode: String? = null,
    @field:Json(name = "refreshToken") val refreshToken: String? = null,
    @field:Json(name = "googleSub") val googleSub: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveStatusResponse(
    @field:Json(name = "isConnected") val isConnected: Boolean,
    @field:Json(name = "googleAccountSub") val googleAccountSub: String? = null,
    @field:Json(name = "lastBackupAt") val lastBackupAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class RestoreBackupRequest(
    @field:Json(name = "confirm") val confirm: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ApiResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "message") val message: String = ""
)

interface AuthApi {

    @POST("api/auth/google")
    suspend fun authenticateGoogle(@Body req: GoogleAuthRequest): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshSession(@Body req: RefreshTokenRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body req: LogoutRequest): Response<ApiResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<UserResponse>

    @DELETE("api/auth/account")
    suspend fun deleteAccount(): Response<ApiResponse>

    @POST("api/drive/connect")
    suspend fun connectDrive(@Body req: DriveConnectRequest): Response<ApiResponse>

    @GET("api/drive/status")
    suspend fun getDriveStatus(): Response<DriveStatusResponse>

    @POST("api/drive/disconnect")
    suspend fun disconnectDrive(): Response<ApiResponse>

    @POST("api/backup/now")
    suspend fun backupNow(): Response<ApiResponse>

    @POST("api/backup/restore")
    suspend fun restoreBackup(@Body req: RestoreBackupRequest): Response<ApiResponse>
}
