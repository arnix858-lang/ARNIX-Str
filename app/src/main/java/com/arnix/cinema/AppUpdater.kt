package com.arnix.cinema

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdater {
    private const val RELEASE_API =
        "https://api.github.com/repos/arnix858-lang/ARNIX-Str/releases/latest"
    private const val APK_ASSET = "ARNIX-Cinema.apk"

    fun checkForUpdate(context: Context): AppUpdate? {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 20000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ARNIX-Cinema-Android")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("GitHub Releases: HTTP $status")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null
            val tag = json.optString("tag_name").removePrefix("v")
            val latestCode = tag.substringAfterLast('.').toLongOrNull()
                ?: throw IOException("Не удалось определить номер версии из тега: $tag")
            val currentCode = if (Build.VERSION.SDK_INT >= 28) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
            if (latestCode <= currentCode) return null

            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") == APK_ASSET) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    break
                }
            }
            return apkUrl?.let {
                AppUpdate(tag, latestCode, it, json.optString("body").take(1200))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndInstall(context: Context, update: AppUpdate): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            Toast.makeText(
                context,
                "Разрешите установку приложений для ARNIX Cinema, затем нажмите «Обновить» ещё раз.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("ARNIX Cinema ${update.versionName}")
            .setDescription("Загрузка обновления приложения…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, "arnix-cinema-update.apk"
            )
        val downloadId = manager.enqueue(request)

        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            manager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) throw IOException("Загрузка обновления не найдена.")
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val apkUri = manager.getUriForDownloadedFile(downloadId)
                            ?: throw IOException("Не удалось получить загруженный APK.")
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(installIntent)
                        return true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        throw IOException("Не удалось скачать обновление (код $reason).")
                    }
                }
            }
            delay(800)
        }
    }
}
