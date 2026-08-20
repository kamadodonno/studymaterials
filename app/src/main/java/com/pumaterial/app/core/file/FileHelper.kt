package com.pumaterial.app.core.file

import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

object FileHelper {

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(sizeBytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(sizeBytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    fun sanitizeFileName(fileName: String): String {
        return fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }

    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot != -1 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * Converts Google Drive share links, Google Slides, Google Docs, OneDrive, Dropbox or web URLs into direct download streams.
     */
    fun convertToDirectDownloadUrl(url: String): String {
        val trimmed = url.trim()

        // 1. Google Slides -> PPTX export
        val slidesRegex = "docs\\.google\\.com/presentation/d/([a-zA-Z0-9_-]+)".toRegex()
        val slidesMatch = slidesRegex.find(trimmed)
        if (slidesMatch != null) {
            val fileId = slidesMatch.groupValues[1]
            return "https://docs.google.com/presentation/d/$fileId/export/pptx"
        }

        // 2. Google Docs -> DOCX export
        val docsRegex = "docs\\.google\\.com/document/d/([a-zA-Z0-9_-]+)".toRegex()
        val docsMatch = docsRegex.find(trimmed)
        if (docsMatch != null) {
            val fileId = docsMatch.groupValues[1]
            return "https://docs.google.com/document/d/$fileId/export?format=docx"
        }

        // 3. Google Sheets -> XLSX export
        val sheetsRegex = "docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9_-]+)".toRegex()
        val sheetsMatch = sheetsRegex.find(trimmed)
        if (sheetsMatch != null) {
            val fileId = sheetsMatch.groupValues[1]
            return "https://docs.google.com/spreadsheets/d/$fileId/export?format=xlsx"
        }

        // 4. Google Drive Raw File: https://drive.google.com/file/d/FILE_ID/view...
        val driveFileRegex = "drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)".toRegex()
        val driveFileMatch = driveFileRegex.find(trimmed)
        if (driveFileMatch != null) {
            val fileId = driveFileMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        // 5. Google Drive Open: https://drive.google.com/open?id=FILE_ID
        val driveOpenRegex = "drive\\.google\\.com/open\\?id=([a-zA-Z0-9_-]+)".toRegex()
        val driveOpenMatch = driveOpenRegex.find(trimmed)
        if (driveOpenMatch != null) {
            val fileId = driveOpenMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        // 6. Google Drive UC: https://drive.google.com/uc?id=FILE_ID
        val driveUcRegex = "drive\\.google\\.com/uc\\?(?:[^&]*&)*id=([a-zA-Z0-9_-]+)".toRegex()
        val driveUcMatch = driveUcRegex.find(trimmed)
        if (driveUcMatch != null) {
            val fileId = driveUcMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        // 7. Dropbox dl=0 -> dl=1
        if (trimmed.contains("dropbox.com") && trimmed.contains("dl=0")) {
            return trimmed.replace("dl=0", "dl=1")
        }

        return trimmed
    }
}
