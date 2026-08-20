package com.pumaterial.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.pumaterial.app.PuMaterialApp
import com.pumaterial.app.core.components.AppBottomNavBar
import com.pumaterial.app.ui.admin.announcements.AdminAnnouncementsScreen
import com.pumaterial.app.ui.admin.announcements.AdminAnnouncementsViewModel
import com.pumaterial.app.ui.admin.auth.AdminLoginScreen
import com.pumaterial.app.ui.admin.auth.AdminLoginViewModel
import com.pumaterial.app.ui.admin.dashboard.AdminDashboardScreen
import com.pumaterial.app.ui.admin.dashboard.AdminDashboardViewModel
import com.pumaterial.app.ui.admin.materials.AdminMaterialsScreen
import com.pumaterial.app.ui.admin.materials.AdminMaterialsViewModel
import com.pumaterial.app.ui.admin.submissions.AdminSubmissionsScreen
import com.pumaterial.app.ui.admin.submissions.AdminSubmissionsViewModel
import com.pumaterial.app.ui.admin.users.AdminUsersScreen
import com.pumaterial.app.ui.admin.users.AdminUsersViewModel
import com.pumaterial.app.ui.announcements.AnnouncementsScreen
import com.pumaterial.app.ui.announcements.AnnouncementsViewModel
import com.pumaterial.app.ui.auth.RegistrationScreen
import com.pumaterial.app.ui.auth.RegistrationViewModel
import com.pumaterial.app.ui.downloads.DownloadsScreen
import com.pumaterial.app.ui.downloads.DownloadsViewModel
import com.pumaterial.app.ui.home.HomeScreen
import com.pumaterial.app.ui.home.HomeViewModel
import com.pumaterial.app.ui.profile.ProfileScreen
import com.pumaterial.app.ui.profile.ProfileViewModel
import com.pumaterial.app.ui.search.SearchScreen
import com.pumaterial.app.ui.search.SearchViewModel
import com.pumaterial.app.ui.splash.SplashScreen
import com.pumaterial.app.ui.splash.SplashViewModel
import com.pumaterial.app.ui.subjects.SubjectDetailScreen
import com.pumaterial.app.ui.subjects.SubjectListScreen
import com.pumaterial.app.ui.subjects.SubjectViewModel
import com.pumaterial.app.ui.submit.SubmitMaterialScreen
import com.pumaterial.app.ui.submit.SubmitMaterialViewModel
import com.pumaterial.app.ui.viewer.PdfViewerScreen
import java.net.URLDecoder

@Composable
fun AppNavHost(
    navController: NavHostController,
    app: PuMaterialApp,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.Splash.route

    val showBottomBar = currentRoute in listOf(
        NavRoutes.Home.route,
        NavRoutes.Subjects.route,
        NavRoutes.Downloads.route,
        NavRoutes.Submit.route,
        NavRoutes.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigateToRoute = { route ->
                        navController.navigate(route) {
                            popUpTo(NavRoutes.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Splash.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Splash Screen
            composable(NavRoutes.Splash.route) {
                val viewModel = remember { SplashViewModel(app.userSessionManager) }
                SplashScreen(
                    viewModel = viewModel,
                    onNavigateToHome = {
                        navController.navigate(NavRoutes.Home.route) {
                            popUpTo(NavRoutes.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(NavRoutes.Register.route) {
                            popUpTo(NavRoutes.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // Student Registration
            composable(NavRoutes.Register.route) {
                val viewModel = remember {
                    RegistrationViewModel(app.authRepository, app.materialRepository)
                }
                RegistrationScreen(
                    viewModel = viewModel,
                    onRegistrationSuccess = {
                        navController.navigate(NavRoutes.Home.route) {
                            popUpTo(NavRoutes.Register.route) { inclusive = true }
                        }
                    }
                )
            }

            // Student Home
            composable(NavRoutes.Home.route) {
                val viewModel = remember {
                    HomeViewModel(
                        authRepository = app.authRepository,
                        materialRepository = app.materialRepository,
                        announcementRepository = app.announcementRepository,
                        sessionManager = app.userSessionManager,
                        connectivityObserver = app.connectivityObserver
                    )
                }
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSubject = { id, name ->
                        navController.navigate(NavRoutes.SubjectDetail.createRoute(id, name))
                    },
                    onNavigateToSearch = {
                        navController.navigate(NavRoutes.Search.route)
                    },
                    onNavigateToAnnouncements = {
                        navController.navigate(NavRoutes.Announcements.route)
                    },
                    onNavigateToProfile = {
                        navController.navigate(NavRoutes.Profile.route) {
                            popUpTo(NavRoutes.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // Subjects List
            composable(NavRoutes.Subjects.route) {
                val viewModel = remember {
                    SubjectViewModel(app.materialRepository, app.downloadRepository, app.personalFolderRepository, app.userSessionManager)
                }
                SubjectListScreen(
                    viewModel = viewModel,
                    onNavigateToSubjectDetail = { id, name ->
                        navController.navigate(NavRoutes.SubjectDetail.createRoute(id, name))
                    }
                )
            }

            // Subject Detail (Modules & Materials)
            composable(
                route = NavRoutes.SubjectDetail.route,
                arguments = listOf(
                    navArgument("subjectId") { type = NavType.StringType },
                    navArgument("subjectName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val subjectId = backStackEntry.arguments?.getString("subjectId") ?: ""
                val subjectName = backStackEntry.arguments?.getString("subjectName") ?: ""
                val viewModel = remember {
                    SubjectViewModel(app.materialRepository, app.downloadRepository, app.personalFolderRepository, app.userSessionManager)
                }
                SubjectDetailScreen(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPdfInApp = { path, title ->
                        navController.navigate(NavRoutes.PdfViewer.createRoute(path, title))
                    }
                )
            }

            // In-App PDF Viewer
            composable(
                route = NavRoutes.PdfViewer.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "Document" }
                )
            ) { backStackEntry ->
                val rawPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val rawTitle = backStackEntry.arguments?.getString("title") ?: "Document"
                val filePath = URLDecoder.decode(rawPath, "UTF-8")
                val title = URLDecoder.decode(rawTitle, "UTF-8")

                PdfViewerScreen(
                    filePath = filePath,
                    title = title,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Offline Downloads & Personal Folders
            composable(NavRoutes.Downloads.route) {
                val viewModel = remember {
                    DownloadsViewModel(app.downloadRepository, app.personalFolderRepository)
                }
                DownloadsScreen(
                    viewModel = viewModel,
                    onOpenPdfInApp = { path, title ->
                        navController.navigate(NavRoutes.PdfViewer.createRoute(path, title))
                    }
                )
            }

            // Submit Material
            composable(NavRoutes.Submit.route) {
                val viewModel = remember {
                    SubmitMaterialViewModel(app.submissionRepository, app.materialRepository)
                }
                SubmitMaterialScreen(viewModel = viewModel)
            }

            // Search
            composable(NavRoutes.Search.route) {
                val viewModel = remember {
                    SearchViewModel(app.materialRepository, app.downloadRepository)
                }
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPdfInApp = { path, title ->
                        navController.navigate(NavRoutes.PdfViewer.createRoute(path, title))
                    }
                )
            }

            // Profile
            composable(NavRoutes.Profile.route) {
                val viewModel = remember {
                    ProfileViewModel(app.authRepository, app.userSessionManager, app.database.downloadedMaterialDao())
                }
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAdminAuth = { navController.navigate(NavRoutes.AdminAuth.route) },
                    onResetComplete = {
                        navController.navigate(NavRoutes.Register.route) {
                            popUpTo(NavRoutes.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            // Announcements Screen
            composable(NavRoutes.Announcements.route) {
                val viewModel = remember {
                    AnnouncementsViewModel(app.announcementRepository, app.authRepository)
                }
                AnnouncementsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Admin Login
            composable(NavRoutes.AdminAuth.route) {
                val viewModel = remember { AdminLoginViewModel(app.authRepository) }
                AdminLoginScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onLoginSuccess = {
                        navController.navigate(NavRoutes.AdminDashboard.route) {
                            popUpTo(NavRoutes.AdminAuth.route) { inclusive = true }
                        }
                    }
                )
            }

            // Admin Dashboard
            composable(NavRoutes.AdminDashboard.route) {
                val viewModel = remember { AdminDashboardViewModel(app.adminRepository) }
                AdminDashboardScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMaterials = { navController.navigate(NavRoutes.AdminMaterials.route) },
                    onNavigateToSubmissions = { navController.navigate(NavRoutes.AdminSubmissions.route) },
                    onNavigateToUsers = { navController.navigate(NavRoutes.AdminUsers.route) },
                    onNavigateToAnnouncements = { navController.navigate(NavRoutes.AdminAnnouncements.route) }
                )
            }

            // Admin Materials
            composable(NavRoutes.AdminMaterials.route) {
                val viewModel = remember { AdminMaterialsViewModel(app.adminRepository, app.materialRepository) }
                AdminMaterialsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Admin Submissions
            composable(NavRoutes.AdminSubmissions.route) {
                val viewModel = remember { AdminSubmissionsViewModel(app.adminRepository, app.materialRepository) }
                AdminSubmissionsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Admin Users
            composable(NavRoutes.AdminUsers.route) {
                val viewModel = remember { AdminUsersViewModel(app.adminRepository, app.materialRepository) }
                AdminUsersScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Admin Announcements
            composable(NavRoutes.AdminAnnouncements.route) {
                val viewModel = remember { AdminAnnouncementsViewModel(app.announcementRepository, app.materialRepository) }
                AdminAnnouncementsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
