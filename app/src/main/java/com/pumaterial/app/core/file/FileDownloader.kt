package com.pumaterial.app.core.file

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FileDownloader(
    private val context: Context
) {
    companion object {
        private const val TAG = "FileDownloader"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    // In-memory cookie jar so Google Drive session cookies persist across download redirects
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            list.addAll(cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val materialsDir: File by lazy {
        File(context.filesDir, "materials").apply {
            if (!exists()) mkdirs()
        }
    }

    private val tempDir: File by lazy {
        File(context.cacheDir, "temp_downloads").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getLocalMaterialFile(materialId: String, fileName: String): File {
        val sanitized = FileHelper.sanitizeFileName(fileName)
        return File(materialsDir, "${materialId}_$sanitized")
    }

    fun checkAvailableStorageBytes(): Long {
        val stat = StatFs(context.filesDir.path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    suspend fun downloadMaterialSafely(
        materialId: String,
        fileName: String,
        downloadUrl: String,
        expectedSizeBytes: Long,
        onProgress: (progress: Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val sanitizedName = FileHelper.sanitizeFileName(fileName)
        val tempFile = File(tempDir, "${materialId}_${System.currentTimeMillis()}.tmp")
        val targetFile = File(materialsDir, "${materialId}_$sanitizedName")
        val ext = FileHelper.getFileExtension(fileName).lowercase()

        // 1. Storage check
        val availableBytes = checkAvailableStorageBytes()
        if (expectedSizeBytes > 0 && availableBytes < (expectedSizeBytes + 5 * 1024 * 1024)) {
            return@withContext Result.failure(
                IllegalStateException("Insufficient device storage. Need at least ${FileHelper.formatFileSize(expectedSizeBytes)}")
            )
        }

        try {
            var currentUrl = FileHelper.convertToDirectDownloadUrl(downloadUrl)
            var response = executeDownloadRequest(currentUrl)

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Download failed (HTTP ${response.code}): ${response.message}")
                )
            }

            var body = response.body ?: return@withContext Result.failure(
                IllegalStateException("Empty response from download server.")
            )

            // 2. Stream to temporary file
            writeResponseBodyToFile(body, tempFile, expectedSizeBytes, onProgress)

            // 3. Inspect downloaded file for Google Drive HTML Interstitial (Virus Scan warning / Confirm page)
            if (isHtmlFile(tempFile)) {
                Log.w(TAG, "Downloaded file contains HTML content. Checking for Google Drive confirmation...")
                val htmlContent = tempFile.readText()

                val resolvedUrl = extractGoogleDriveConfirmUrl(htmlContent, currentUrl)
                if (resolvedUrl != null) {
                    Log.d(TAG, "Resolved Google Drive direct download URL: $resolvedUrl")
                    currentUrl = resolvedUrl
                    response = executeDownloadRequest(currentUrl)

                    if (response.isSuccessful && response.body != null) {
                        tempFile.delete()
                        writeResponseBodyToFile(response.body!!, tempFile, expectedSizeBytes, onProgress)
                    }
                } else if (htmlContent.contains("Access Denied", ignoreCase = true) ||
                    htmlContent.contains("You need access", ignoreCase = true) ||
                    htmlContent.contains("accounts.google.com", ignoreCase = true) ||
                    htmlContent.contains("ServiceLogin", ignoreCase = true)
                ) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IllegalStateException("Google Drive file is private. Please ensure file sharing in Google Drive is set to 'Anyone with the link can view'.")
                    )
                }
            }

            // 4. Validate binary integrity
            val validationError = validateFileIntegrity(tempFile, ext)
            if (validationError != null) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException(validationError))
            }

            // 5. Atomic replacement to final materials folder
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $fileName", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }

    private fun executeDownloadRequest(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()
        return client.newCall(request).execute()
    }

    private fun writeResponseBodyToFile(
        body: ResponseBody,
        targetFile: File,
        expectedSizeBytes: Long,
        onProgress: (progress: Float) -> Unit
    ) {
        val contentLength = if (expectedSizeBytes > 0) expectedSizeBytes else body.contentLength()
        var downloadedBytes = 0L

        body.byteStream().use { input: InputStream ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (contentLength > 0) {
                        val progress = (downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
                output.flush()
            }
        }
    }

    private fun isHtmlFile(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val headerBytes = ByteArray(minOf(file.length().toInt(), 512))
        file.inputStream().use { it.read(headerBytes) }
        val headerString = String(headerBytes).trim().lowercase()
        return headerString.startsWith("<!doctype html") ||
                headerString.startsWith("<html") ||
                headerString.contains("<head>") ||
                headerString.contains("<body")
    }

    private fun extractGoogleDriveConfirmUrl(html: String, originalUrl: String): String? {
        // Regex 1: /uc?export=download...
        val ucMatch = "href=\"(/uc\\?export=download[^\"]+)\"".toRegex().find(html)
        if (ucMatch != null) {
            val path = ucMatch.groupValues[1].replace("&amp;", "&")
            return "https://drive.google.com$path"
        }

        // Regex 2: Form action with confirm token
        val confirmMatch = "confirm=([a-zA-Z0-9_-]+)".toRegex().find(html)
        val idMatch = "id=([a-zA-Z0-9_-]+)".toRegex().find(originalUrl) ?: "id=([a-zA-Z0-9_-]+)".toRegex().find(html)
        if (confirmMatch != null && idMatch != null) {
            val confirm = confirmMatch.groupValues[1]
            val id = idMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=$confirm"
        }

        return null
    }

    private fun validateFileIntegrity(file: File, extension: String): String? {
        if (!file.exists() || file.length() < 10L) {
            return "Downloaded file is empty."
        }

        val header = ByteArray(minOf(file.length().toInt(), 16))
        file.inputStream().use { it.read(header) }

        val isZipHeader = header.size >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
        val isPdfHeader = header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte() // %PDF
        val isOleHeader = header.size >= 4 && header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() && header[2] == 0x11.toByte() && header[3] == 0xE0.toByte() // OLE DOC/PPT

        when (extension) {
            "pptx", "docx", "xlsx" -> {
                if (!isZipHeader) {
                    if (isHtmlFile(file)) {
                        return "Invalid file: Server returned a webpage instead of the presentation/document."
                    }
                    // Some office files might have slight wrapper variations, but warn if clearly text
                    val textPreview = String(header)
                    if (textPreview.contains("<") || textPreview.contains("{")) {
                        return "The presentation file appears corrupted or invalid."
                    }
                }
            }
            "pdf" -> {
                if (!isPdfHeader && isHtmlFile(file)) {
                    return "Invalid file: Server returned a webpage instead of a PDF document."
                }
            }
            "ppt", "doc" -> {
                if (isHtmlFile(file)) {
                    return "Invalid file: Server returned a webpage instead of the document."
                }
            }
        }

        return null
    }

    fun deleteLocalMaterialFile(materialId: String, fileName: String): Boolean {
        val file = getLocalMaterialFile(materialId, fileName)
        return if (file.exists()) file.delete() else true
    }
}
