package com.pumaterial.app.core.file

object MimeTypes {
    const val PDF = "application/pdf"
    const val DOC = "application/msword"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val PPT = "application/vnd.ms-powerpoint"
    const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    fun getMimeTypeForExtension(extension: String): String {
        return when (extension.lowercase()) {
            "pdf" -> PDF
            "doc" -> DOC
            "docx" -> DOCX
            "ppt" -> PPT
            "pptx" -> PPTX
            else -> "*/*"
        }
    }

    fun isAllowedMimeType(mimeType: String?): Boolean {
        if (mimeType == null) return false
        return mimeType in listOf(PDF, DOC, DOCX, PPT, PPTX)
    }

    fun getExtensionFromMime(mimeType: String): String {
        return when (mimeType) {
            PDF -> "pdf"
            DOC -> "doc"
            DOCX -> "docx"
            PPT -> "ppt"
            PPTX -> "pptx"
            else -> "bin"
        }
    }
}
