package com.vahancam.app

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AppUpdateHelper {

    private const val DEFAULT_UPDATE_URL = "https://github.com/ajmerim72-stack/vahancam/releases/latest/download/VahanCam.apk"
    private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/ajmerim72-stack/vahancam/releases/latest"
    private const val NOTIFICATION_CHANNEL_ID = "vahancam_updates_channel"
    private const val NOTIFICATION_ID = 2001
    private const val PREFS_NAME = "vahancam_prefs"
    private const val KEY_CUSTOM_UPDATE_URL = "custom_update_url"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VahanCam App Updates"
            val descriptionText = "Notifications when a new version of VahanCam is available"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks GitHub API asynchronously for newer releases.
     * Triggers notification and callback if update is available.
     */
    fun checkForUpdates(
        activity: Activity,
        onResult: (hasUpdate: Boolean, latestTag: String, apkUrl: String) -> Unit
    ) {
        executor.execute {
            try {
                val url = URL(GITHUB_API_LATEST_RELEASE)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "VahanCam-App")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                }

                if (connection.responseCode != 200) {
                    mainHandler.post { onResult(false, "", "") }
                    return@execute
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "").trim()

                var downloadUrl = DEFAULT_UPDATE_URL
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", DEFAULT_UPDATE_URL)
                            break
                        }
                    }
                }

                val currentVersion = try {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.3.0"
                } catch (e: Exception) { "1.3.0" }

                val hasUpdate = isNewerVersion(currentVersion, tagName)

                mainHandler.post {
                    if (hasUpdate) {
                        sendUpdateNotification(activity, tagName)
                    }
                    onResult(hasUpdate, tagName, downloadUrl)
                }

            } catch (e: Exception) {
                mainHandler.post { onResult(false, "", "") }
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (latest.isEmpty()) return false
        val cleanCurrent = current.removePrefix("v").removePrefix("V").trim()
        val cleanLatest = latest.removePrefix("v").removePrefix("V").trim()
        if (cleanCurrent == cleanLatest) return false

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val c = if (i < currentParts.size) currentParts[i] else 0
            val l = if (i < latestParts.size) latestParts[i] else 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun sendUpdateNotification(context: Context, newVersion: String) {
        try {
            createNotificationChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("trigger_update_dialog", true)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_system_update)
                .setContentTitle("🚀 VahanCam Update $newVersion Available!")
                .setContentText("A new version is ready. Tap to download and install now.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSavedUpdateUrl(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_UPDATE_URL, null)
        if (saved.isNullOrEmpty() || saved.contains("192.168.0.110")) {
            return DEFAULT_UPDATE_URL
        }
        return saved
    }

    fun setSavedUpdateUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_UPDATE_URL, url)
            .apply()
    }

    /**
     * Shows a clean update dialog where the user can initiate in-app update or modify update URL.
     */
    fun showUpdateDialog(activity: Activity) {
        val currentUrl = getSavedUpdateUrl(activity)

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update_app, null)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tvUpdateDesc)
        val etUrl = dialogView.findViewById<EditText>(R.id.etUpdateUrl)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvUpdateStatus)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.pbUpdateDownload)
        val btnStart = dialogView.findViewById<Button>(R.id.btnStartUpdate)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelUpdate)

        val versionName = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        } catch (e: Exception) { "1.3.0" }
        tvDesc.text = "Current Version: $versionName\nDownload and install latest update directly online from GitHub Releases."

        etUrl.setText(currentUrl)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnStart.setOnClickListener {
            val urlString = etUrl.text.toString().trim()
            if (urlString.isEmpty()) {
                Toast.makeText(activity, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setSavedUpdateUrl(activity, urlString)

            btnStart.isEnabled = false
            btnCancel.isEnabled = false
            etUrl.isEnabled = false
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Connecting to update server..."

            downloadAndInstallApk(
                activity = activity,
                downloadUrl = urlString,
                onProgress = { progress, totalMb, downloadedMb ->
                    mainHandler.post {
                        progressBar.progress = progress
                        tvStatus.text = "Downloading: $downloadedMb MB / $totalMb MB ($progress%)"
                    }
                },
                onComplete = { apkFile ->
                    mainHandler.post {
                        dialog.dismiss()
                        promptInstall(activity, apkFile)
                    }
                },
                onError = { errorMsg ->
                    mainHandler.post {
                        btnStart.isEnabled = true
                        btnCancel.isEnabled = true
                        etUrl.isEnabled = true
                        progressBar.visibility = View.GONE
                        tvStatus.text = "Error: $errorMsg\nMake sure PC server is running."
                    }
                }
            )
        }

        dialog.show()
    }

    private fun downloadAndInstallApk(
        activity: Activity,
        downloadUrl: String,
        onProgress: (Int, String, String) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    onError("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                    return@execute
                }

                val fileLength = connection.contentLength
                val totalMb = String.format("%.2f", fileLength.toFloat() / (1024 * 1024))

                val outputFile = File(activity.cacheDir, "VahanCam_latest.apk")
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                var inputStream: InputStream? = null
                var outputStream: FileOutputStream? = null

                try {
                    inputStream = connection.inputStream
                    outputStream = FileOutputStream(outputFile)

                    val buffer = ByteArray(8192)
                    var totalRead: Long = 0
                    var count: Int

                    while (inputStream.read(buffer).also { count = it } != -1) {
                        totalRead += count
                        outputStream.write(buffer, 0, count)

                        if (fileLength > 0) {
                            val percent = (totalRead * 100 / fileLength).toInt()
                            val downloadedMb = String.format("%.2f", totalRead.toFloat() / (1024 * 1024))
                            onProgress(percent, totalMb, downloadedMb)
                        }
                    }
                    outputStream.flush()
                } finally {
                    outputStream?.close()
                    inputStream?.close()
                    connection.disconnect()
                }

                onComplete(outputFile)

            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.message ?: "Download failed")
            }
        }
    }

    /**
     * Triggers Android package installer for the downloaded APK.
     */
    fun promptInstall(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        activity,
                        "Please allow VahanCam to install app updates",
                        Toast.LENGTH_LONG
                    ).show()
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(installIntent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares the app's own APK file directly to WhatsApp, Telegram, Quick Share, etc.
     */
    fun shareAppApk(activity: Activity) {
        Toast.makeText(activity, "Preparing APK to share...", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val appInfo = activity.applicationInfo
                val sourceApk = File(appInfo.sourceDir)
                if (!sourceApk.exists()) {
                    mainHandler.post {
                        Toast.makeText(activity, "Cannot locate installed APK", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                // Copy APK to cache directory with clean name VahanCam.apk
                val shareDir = File(activity.cacheDir, "shared_apk")
                if (!shareDir.exists()) shareDir.mkdirs()
                val targetApk = File(shareDir, "VahanCam.apk")

                sourceApk.copyTo(targetApk, overwrite = true)

                val apkUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    targetApk
                )

                mainHandler.post {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, apkUri)
                        putExtra(Intent.EXTRA_SUBJECT, "VahanCam App APK")
                        putExtra(Intent.EXTRA_TEXT, "VahanCam App - Document upload & 3:4 Doc Clear Camera for Parivahan portal.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(Intent.createChooser(shareIntent, "Share VahanCam App APK"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    Toast.makeText(activity, "Error sharing APK: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
