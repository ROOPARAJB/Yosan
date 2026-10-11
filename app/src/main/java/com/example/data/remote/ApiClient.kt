package com.example.data.remote

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.utils.SecureTokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val PREFS_NAME = "api_client_prefs"
    private const val KEY_CUSTOM_URL = "custom_backend_url"

    fun getBaseUrl(context: Context? = null): String {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_CUSTOM_URL, null)
            if (!custom.isNullOrBlank()) {
                val clean = custom.trim()
                return if (clean.endsWith("/")) clean else "$clean/"
            }
        }

        val isEmulator = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)

        val defaultOnlineUrl = try {
            com.example.BuildConfig.DEFAULT_BACKEND_URL.trim()
        } catch (_: Exception) {
            ""
        }

        return when {
            defaultOnlineUrl.isNotBlank() -> if (defaultOnlineUrl.endsWith("/")) defaultOnlineUrl else "$defaultOnlineUrl/"
            isEmulator -> "http://10.0.2.2:3000/"
            else -> "http://192.168.1.10:3000/"
        }
    }

    @Synchronized
    fun setCustomBaseUrl(context: Context, url: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (url.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_URL).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_URL, url.trim()).apply()
        }
        retrofit = null
    }

    @Volatile
    private var retrofit: Retrofit? = null

    @Synchronized
    fun getAuthApi(context: Context): AuthApi {
        if (retrofit == null) {
            val tokenManager = SecureTokenManager(context)

            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val accessToken = tokenManager.getAccessToken()
                val userEmail = tokenManager.getUserEmail()

                val requestBuilder = original.newBuilder()
                if (!accessToken.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $accessToken")
                }
                if (!userEmail.isNullOrBlank()) {
                    requestBuilder.header("X-User-Email", userEmail)
                }
                chain.proceed(requestBuilder.build())
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(getBaseUrl(context))
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        return retrofit!!.create(AuthApi::class.java)
    }
}
