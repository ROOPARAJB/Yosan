package com.example.features.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val isNewer: Boolean
)

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState()
    data class UpToDate(val currentVersion: String) : UpdateUiState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

class UpdateManager(
    private val context: Context,
    private val repoOwner: String = "ROOPARAJB",
    private val repoName: String = "Yosan"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun resetState() {
        _uiState.value = UpdateUiState.Idle
    }

    suspend fun checkForUpdates(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        _uiState.value = UpdateUiState.Checking
        try {
            val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Yosan-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        val state = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                        _uiState.value = state
                        return@withContext Result.success(null)
                    }
                    val err = "GitHub API returned error: ${response.code} ${response.message}"
                    _uiState.value = UpdateUiState.Error(err)
                    return@withContext Result.failure(Exception(err))
                }

                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "").trim()
                val releaseTitle = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "No release notes provided.")
                val assets = json.optJSONArray("assets")

                var apkDownloadUrl = ""
                var apkFileName = ""
                var apkSize = 0L

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkFileName = name
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val cleanCurrentVersion = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()

                val isNewer = isRemoteVersionNewer(cleanRemoteVersion, cleanCurrentVersion)

                if (isNewer && apkDownloadUrl.isNotBlank()) {
                    val updateInfo = UpdateInfo(
                        versionName = tagName,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        downloadUrl = apkDownloadUrl,
                        fileName = apkFileName,
                        fileSize = apkSize,
                        isNewer = true
                    )
                    _uiState.value = UpdateUiState.UpdateAvailable(updateInfo)
                    Result.success(updateInfo)
                } else {
                    _uiState.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to check for updates"
            _uiState.value = UpdateUiState.Error(errMsg)
            Result.failure(e)
        }
    }

    suspend fun downloadAndPrepareApk(updateInfo: UpdateInfo): Result<File> = withContext(Dispatchers.IO) {
        _uiState.value = UpdateUiState.Downloading(0f, 0L, updateInfo.fileSize)
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "yosan-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "Yosan-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "Download failed with HTTP ${response.code}"
                    _uiState.value = UpdateUiState.Error(msg)
                    return@withContext Result.failure(Exception(msg))
                }

                val responseBody = response.body ?: throw Exception("Empty response body")
                val totalLength = if (updateInfo.fileSize > 0) updateInfo.fileSize else responseBody.contentLength()
                var bytesDownloaded = 0L

                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var lastProgressUpdate = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || bytesDownloaded == totalLength) {
                                lastProgressUpdate = now
                                val progress = if (totalLength > 0) {
                                    (bytesDownloaded.toFloat() / totalLength).coerceIn(0f, 1f)
                                } else 0f
                                _uiState.value = UpdateUiState.Downloading(progress, bytesDownloaded, totalLength)
                            }
                        }
                        outputStream.flush()
                    }
                }
            }

            _uiState.value = UpdateUiState.ReadyToInstall(apkFile)
            Result.success(apkFile)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Download failed"
            _uiState.value = UpdateUiState.Error(errMsg)
            Result.failure(e)
        }
    }

    fun installApk(apkFile: File) {
        try {
            // Android 8.0+ unknown sources permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _uiState.value = UpdateUiState.Error("Failed to launch installer: ${e.localizedMessage}")
        }
    }

    private fun isRemoteVersionNewer(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val remoteParts = remote.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
