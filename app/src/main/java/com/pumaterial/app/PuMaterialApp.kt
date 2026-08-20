package com.pumaterial.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.pumaterial.app.core.file.FileDownloader
import com.pumaterial.app.core.network.ConnectivityObserver
import com.pumaterial.app.core.network.NetworkConnectivityObserver
import com.pumaterial.app.data.local.database.AppDatabase
import com.pumaterial.app.data.local.datastore.UserSessionManager
import com.pumaterial.app.data.remote.firebase.FirebaseService
import com.pumaterial.app.data.repository.*
import com.pumaterial.app.domain.repository.*

class PuMaterialApp : Application() {

    lateinit var database: AppDatabase private set
    lateinit var userSessionManager: UserSessionManager private set
    lateinit var fileDownloader: FileDownloader private set
    lateinit var connectivityObserver: ConnectivityObserver private set

    lateinit var authRepository: AuthRepository private set
    lateinit var materialRepository: MaterialRepository private set
    lateinit var downloadRepository: DownloadRepository private set
    lateinit var personalFolderRepository: PersonalFolderRepository private set
    lateinit var submissionRepository: SubmissionRepository private set
    lateinit var announcementRepository: AnnouncementRepository private set
    lateinit var adminRepository: AdminRepository private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase safely if not already initialized
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (_: Exception) {}

        // Initialize Local Storage & Helpers
        database = AppDatabase.getInstance(this)
        userSessionManager = UserSessionManager(this)
        fileDownloader = FileDownloader(this)
        connectivityObserver = NetworkConnectivityObserver(this)

        // Initialize Repositories
        val auth = FirebaseService.auth
        val firestore = FirebaseService.firestore
        val storage = FirebaseService.storage

        authRepository = AuthRepositoryImpl(auth, firestore, userSessionManager)
        materialRepository = MaterialRepositoryImpl(firestore, database.downloadedMaterialDao(), database.cachedHierarchyDao())
        downloadRepository = DownloadRepositoryImpl(fileDownloader, database.downloadedMaterialDao())
        personalFolderRepository = PersonalFolderRepositoryImpl(database.personalFolderDao())
        submissionRepository = SubmissionRepositoryImpl(auth, firestore, storage, userSessionManager)
        announcementRepository = AnnouncementRepositoryImpl(firestore)
        adminRepository = AdminRepositoryImpl(auth, firestore, storage)
    }
}
