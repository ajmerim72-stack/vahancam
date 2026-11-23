package com.vahancam.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object StorageHelper {

    private const val PREFS_NAME = "vahancam_prefs"
    private const val KEY_COUNTER = "photo_sequential_counter"
    private const val KEY_SAVE_GALLERY = "pref_save_to_gallery"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Gets the current counter value without incrementing.
     * Default starting value is 1.
     */
    fun getCurrentCounter(context: Context): Int {
        return getPrefs(context).getInt(KEY_COUNTER, 1)
    }

    /**
     * Sets the counter value (e.g. if user entered a custom number on-page).
     */
    fun setCounter(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_COUNTER, value).apply()
    }

    /**
     * Increments and commits the counter for the next capture.
     * Ensures strict sequential order: 1, 2, 3...
     */
    fun incrementCounter(context: Context): Int {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_COUNTER, 1)
        val next = current + 1
        prefs.edit().putInt(KEY_COUNTER, next).apply()
        return next
    }

    /**
     * Preference for whether "Save to Gallery" is checked.
     * Defaults to true.
     */
    fun isSaveToGalleryEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SAVE_GALLERY, true)
    }

    fun setSaveToGalleryEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SAVE_GALLERY, enabled).apply()
    }

    /**
     * Generates a clean default filename format: strictly <counter>.jpg (e.g., 1.jpg, 2.jpg).
     * No dashes, underscores, timestamps, or extra dots.
     */
    fun getFilenameForCounter(counter: Int): String {
        return "$counter.jpg"
    }

    /**
     * Sanitizes user on-page rename input:
     * - Trims whitespace
     * - Replaces spaces and illegal filesystem characters with underscores
     * - Ensures exactly one .jpg extension at the end
     */
    fun sanitizeFilename(rawInput: String, defaultCounter: Int): String {
        var clean = rawInput.trim()
        if (clean.isEmpty()) {
            return "$defaultCounter.jpg"
        }

        // Remove .jpg or .jpeg if already at the end to clean base name
        if (clean.endsWith(".jpg", ignoreCase = true)) {
            clean = clean.substring(0, clean.length - 4)
        } else if (clean.endsWith(".jpeg", ignoreCase = true)) {
            clean = clean.substring(0, clean.length - 5)
        }

        // Replace spaces and special characters
        clean = clean.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        // Remove consecutive underscores
        clean = clean.replace(Regex("_+"), "_").trim('_')

        if (clean.isEmpty()) {
            clean = "$defaultCounter"
        }

        // If the user entered purely a number, update counter reference
        val num = clean.toIntOrNull()
        return "$clean.jpg"
    }

    /**
     * Returns the target File object in the app's cache directory.
     */
    fun getTargetCacheFile(context: Context, filename: String): File {
        val cacheDir = context.cacheDir
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File(cacheDir, filename)
    }

    /**
     * Generates a secure content:// URI via FileProvider.
     */
    fun getFileProviderUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Writes byte array to the destination file.
     */
    fun writeBytesToFile(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { fos ->
            fos.write(bytes)
            fos.flush()
        }
    }

    /**
     * Copies data from a local file to an output URI (e.g. MediaStore.EXTRA_OUTPUT from Chrome).
     */
    fun copyFileToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { outStream: OutputStream ->
                sourceFile.inputStream().use { inStream: InputStream ->
                    inStream.copyTo(outStream)
                }
                outStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves the processed document photo directly into the Phone's Public Gallery (Pictures/VahanCam).
     * Works seamlessly on all Android versions (Android 7 up to Android 14+).
     */
    fun saveToPublicGallery(context: Context, filename: String, bytes: ByteArray): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped Storage API for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/VahanCam")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                uri
            } else {
                // Legacy storage for Android 9 and below
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val vahanCamDir = File(picturesDir, "VahanCam")
                if (!vahanCamDir.exists()) {
                    vahanCamDir.mkdirs()
                }
                val destFile = File(vahanCamDir, filename)
                FileOutputStream(destFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
                // Notify Android Media Scanner
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
