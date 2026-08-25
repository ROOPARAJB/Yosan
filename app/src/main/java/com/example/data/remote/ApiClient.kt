package com.example.data.remote

import android.content.Context
import android.os.Build
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

    private fun getBaseUrl(): String {
        val isEmulator = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)

        return if (isEmulator) {
            "http://10.0.2.2:3000/"
        } else {
            "http://192.168.0.105:3000/"
        }
    }

    private var retrofit: Retrofit? = null

    fun getAuthApi(context: Context): AuthApi {
        if (retrofit == null) {
            val tokenManager = SecureTokenManager(context)

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val accessToken = tokenManager.getAccessToken()

                val requestBuilder = original.newBuilder()
                if (!accessToken.isNull_or_empty()) {
                    requestBuilder.header("Authorization", "Bearer $accessToken")
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
                .baseUrl(getBaseUrl())
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        return retrofit!!.create(AuthApi::class.java)
    }
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
