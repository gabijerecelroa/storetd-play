package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AppUpdateInfo(
    val success: Boolean,
    val updateAvailable: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean,
    val changelog: String
)

object AppUpdateApi {
    fun check(currentVersionCode: Int = BuildConfig.VERSION_CODE): AppUpdateInfo {
        val base = BuildConfig.API_BASE_URL
            .trim()
            .trimEnd('/')

        if (base.isBlank()) {
            return AppUpdateInfo(
                success = false,
                updateAvailable = false,
                latestVersionCode = currentVersionCode,
                latestVersionName = BuildConfig.VERSION_NAME,
                apkUrl = "",
                forceUpdate = false,
                changelog = ""
            )
        }

        val encodedVersion = URLEncoder.encode(currentVersionCode.toString(), "UTF-8")
        val url = URL("$base/api/app-update?versionCode=$encodedVersion")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            AppUpdateInfo(
                success = json.optBoolean("success", false),
                updateAvailable = json.optBoolean("updateAvailable", false),
                latestVersionCode = json.optInt("latestVersionCode", currentVersionCode),
                latestVersionName = json.optString("latestVersionName", BuildConfig.VERSION_NAME),
                apkUrl = json.optString("apkUrl", ""),
                forceUpdate = json.optBoolean("forceUpdate", false),
                changelog = json.optString("changelog", "")
            )
        } catch (_: Exception) {
            AppUpdateInfo(
                success = false,
                updateAvailable = false,
                latestVersionCode = currentVersionCode,
                latestVersionName = BuildConfig.VERSION_NAME,
                apkUrl = "",
                forceUpdate = false,
                changelog = ""
            )
        } finally {
            connection.disconnect()
        }
    }
}
