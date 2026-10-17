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
                if (clean.contains("192.168.") || clean.contains("10.0.2.2") || clean.contains("localhost")) {
                    prefs.edit().remove(KEY_CUSTOM_URL).apply()
                } else {
                    return if (clean.endsWith("/")) clean else "$clean/"
                }
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
            else -> "https://yosan-xgsd.onrender.com/"
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

    private const val KEY_SUPABASE_URL = "custom_supabase_url"
    private const val KEY_SUPABASE_ANON_KEY = "custom_supabase_anon_key"

    fun getSupabaseUrl(context: Context? = null): String {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_SUPABASE_URL, null)
            if (!custom.isNullOrBlank()) {
                val clean = custom.trim()
                return if (clean.endsWith("/")) clean else "$clean/"
            }
        }
        val envUrl = try {
            val field = BuildConfig::class.java.getField("SUPABASE_URL")
            (field.get(null) as? String ?: "").trim()
        } catch (_: Exception) { "" }

        return if (envUrl.isNotBlank() && !envUrl.contains("your-project-ref")) {
            if (envUrl.endsWith("/")) envUrl else "$envUrl/"
        } else ""
    }

    fun getSupabaseAnonKey(context: Context? = null): String {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_SUPABASE_ANON_KEY, null)
            if (!custom.isNullOrBlank()) return custom.trim()
        }
        val envKey = try {
            val field = BuildConfig::class.java.getField("SUPABASE_ANON_KEY")
            (field.get(null) as? String ?: "").trim()
        } catch (_: Exception) { "" }

        return if (envKey.isNotBlank() && !envKey.contains("your_supabase_anon")) envKey else ""
    }

    fun isSupabaseConfigured(context: Context? = null): Boolean {
        val url = getSupabaseUrl(context)
        val key = getSupabaseAnonKey(context)
        return url.isNotBlank() && key.isNotBlank()
    }

    @Synchronized
    fun setCustomSupabase(context: Context, url: String?, anonKey: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(KEY_SUPABASE_URL) else putString(KEY_SUPABASE_URL, url.trim())
            if (anonKey.isNullOrBlank()) remove(KEY_SUPABASE_ANON_KEY) else putString(KEY_SUPABASE_ANON_KEY, anonKey.trim())
            apply()
        }
        supabaseRetrofit = null
    }

    @Volatile
    private var supabaseRetrofit: Retrofit? = null

    @Synchronized
    fun getSupabaseAuthApi(context: Context): SupabaseAuthApi? {
        val url = getSupabaseUrl(context)
        val anonKey = getSupabaseAnonKey(context)
        if (url.isBlank() || anonKey.isBlank()) return null

        if (supabaseRetrofit == null || supabaseRetrofit?.baseUrl().toString() != url) {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val reqBuilder = original.newBuilder()
                if (original.header("apikey") == null) {
                    reqBuilder.header("apikey", anonKey)
                }
                if (original.header("Authorization") == null) {
                    reqBuilder.header("Authorization", "Bearer $anonKey")
                }
                chain.proceed(reqBuilder.build())
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            supabaseRetrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        return supabaseRetrofit?.create(SupabaseAuthApi::class.java)
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
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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

    /**
     * Sends a non-blocking background request to the health endpoint to wake up
     * the backend if it is asleep (e.g. Render free tier cold start).
     */
    fun prewarmBackend(context: Context? = null) {
        try {
            val url = getBaseUrl(context) + "api/health"
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    // Silent fail: best effort wake-up ping
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        } catch (_: Exception) {
            // Best effort ignore
        }
    }

    @Volatile
    private var googleDriveRetrofit: Retrofit? = null

    @Synchronized
    fun getGoogleDriveApi(): GoogleDriveApi {
        if (googleDriveRetrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            googleDriveRetrofit = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        return googleDriveRetrofit!!.create(GoogleDriveApi::class.java)
    }
}

