package com.pumaterial.app.core.file

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

sealed interface OpenFileResult {
    data object Success : OpenFileResult
    data class NoAppInstalled(
        val fileType: String,
        val suggestedAppName: String,
        val suggestedPackageName: String,
        val file: File? = null
    ) : OpenFileResult
    data class Error(val message: String) : OpenFileResult
}

object FileOpener {

    private const val TAG = "FileOpener"

    fun openFile(context: Context, file: File, fileType: String): OpenFileResult {
        if (!file.exists() || file.length() == 0L) {
            return OpenFileResult.Error("File not found on device storage or is empty.")
        }

        // 1. Stage the file into a dedicated shared directory with a clean filename
        val stagedFile = try {
            val sharedDir = File(context.cacheDir, "shared_documents").apply { if (!exists()) mkdirs() }
            val cleanExt = fileType.lowercase().trimStart('.')
            val cleanName = if (file.name.endsWith(".$cleanExt", ignoreCase = true)) {
                file.name
            } else {
                "${file.nameWithoutExtension}.$cleanExt"
            }
            val target = File(sharedDir, cleanName)
            file.copyTo(target, overwrite = true)
            target
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stage copy, using original file: ${e.message}")
            file
        }

        // 2. Generate secure FileProvider content URI
        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                stagedFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider error", e)
            return OpenFileResult.Error("Unable to share file with office apps: ${e.localizedMessage}")
        }

        val primaryMime = MimeTypes.getMimeTypeForExtension(fileType)
        val mimeCandidates = getCandidateMimeTypes(fileType, primaryMime)

        var lastException: Exception? = null

        for (mime in mimeCandidates) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    clipData = ClipData.newRawUri(stagedFile.name, contentUri)
                }

                // Explicitly grant read/write URI permission to all potential matching applications
                try {
                    val resolvedActivities = context.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )
                    for (resolveInfo in resolvedActivities) {
                        val pkg = resolveInfo.activityInfo?.packageName
                        if (!pkg.isNullOrBlank()) {
                            context.grantUriPermission(
                                pkg,
                                contentUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error granting package permissions", e)
                }

                val chooser = Intent.createChooser(intent, "Open ${fileType.uppercase()} with...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(chooser)
                return OpenFileResult.Success
            } catch (e: ActivityNotFoundException) {
                lastException = e
            } catch (e: Exception) {
                lastException = e
            }
        }

        val (appName, pkgName) = getSuggestedAppForType(fileType)
        return if (lastException is ActivityNotFoundException) {
            OpenFileResult.NoAppInstalled(
                fileType = fileType,
                suggestedAppName = appName,
                suggestedPackageName = pkgName,
                file = stagedFile
            )
        } else {
            OpenFileResult.Error("Unable to open file (${fileType.uppercase()}): ${lastException?.localizedMessage ?: "No compatible viewer application installed."}")
        }
    }

    private fun getCandidateMimeTypes(fileType: String, primaryMime: String): List<String> {
        val list = mutableListOf(primaryMime)
        when (fileType.lowercase()) {
            "pptx" -> {
                list.add("application/vnd.ms-powerpoint")
                list.add("application/mspowerpoint")
                list.add("application/x-mspowerpoint")
                list.add("application/vnd.openxmlformats-officedocument.presentationml.slideshow")
                list.add("application/octet-stream")
            }
            "ppt" -> {
                list.add("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                list.add("application/x-mspowerpoint")
                list.add("application/octet-stream")
            }
            "docx" -> {
                list.add("application/msword")
                list.add("application/vnd.ms-word.document.macroEnabled.12")
                list.add("application/octet-stream")
            }
            "doc" -> {
                list.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                list.add("application/octet-stream")
            }
            "pdf" -> {
                list.add("application/x-pdf")
                list.add("application/octet-stream")
            }
        }
        return list.distinct()
    }

    fun openInOnlineViewer(context: Context, webUrl: String) {
        try {
            val viewerUrl = "https://docs.google.com/viewer?url=${Uri.encode(webUrl)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewerUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openPlayStoreForApp(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    private fun getSuggestedAppForType(fileType: String): Pair<String, String> {
        return when (fileType.lowercase()) {
            "pdf" -> "Google PDF Viewer" to "com.google.android.apps.pdfviewer"
            "doc", "docx" -> "Google Docs / Microsoft Word" to "com.google.android.apps.docs.editors.docs"
            "ppt", "pptx" -> "Google Slides / Microsoft PowerPoint" to "com.google.android.apps.docs.editors.slides"
            else -> "WPS Office" to "cn.wps.moffice_eng"
        }
    }
}
