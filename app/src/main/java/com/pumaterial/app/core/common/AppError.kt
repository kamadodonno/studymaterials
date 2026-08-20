package com.pumaterial.app.core.common

sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError(message: String = "No internet connection. Showing offline data.", cause: Throwable? = null) : AppError(message, cause)
    class AuthError(message: String = "Authentication failed. Please try again.", cause: Throwable? = null) : AppError(message, cause)
    class DuplicateEnrollmentError(message: String = "This enrollment number is already registered.") : AppError(message)
    class FileSizeExceededError(message: String = "File exceeds the maximum size of 50 MB.") : AppError(message)
    class UnsupportedFileTypeError(message: String = "Only PDF, PPT, PPTX, DOC, and DOCX files are supported.") : AppError(message)
    class StorageFullError(message: String = "Insufficient device storage to download this file.") : AppError(message)
    class DownloadFailedError(message: String = "Download interrupted. Please try again.", cause: Throwable? = null) : AppError(message, cause)
    class PermissionDeniedError(message: String = "You do not have permission to perform this action.") : AppError(message)
    class MaterialNotFoundError(message: String = "Requested study material is no longer available.") : AppError(message)
    class UnknownError(message: String = "An unexpected error occurred.", cause: Throwable? = null) : AppError(message, cause)
}
