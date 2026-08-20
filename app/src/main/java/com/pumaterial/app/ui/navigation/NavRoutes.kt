package com.pumaterial.app.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Splash : NavRoutes("splash")
    data object Register : NavRoutes("register")
    data object Home : NavRoutes("home")
    data object Subjects : NavRoutes("subjects")
    data object SubjectDetail : NavRoutes("subject_detail/{subjectId}/{subjectName}") {
        fun createRoute(subjectId: String, subjectName: String) = "subject_detail/$subjectId/$subjectName"
    }
    data object PdfViewer : NavRoutes("pdf_viewer?filePath={filePath}&title={title}") {
        fun createRoute(filePath: String, title: String) = "pdf_viewer?filePath=${java.net.URLEncoder.encode(filePath, "UTF-8")}&title=${java.net.URLEncoder.encode(title, "UTF-8")}"
    }
    data object Downloads : NavRoutes("downloads")
    data object Submit : NavRoutes("submit")
    data object Search : NavRoutes("search")
    data object Profile : NavRoutes("profile")
    data object Announcements : NavRoutes("announcements")
    
    // Admin Routes
    data object AdminAuth : NavRoutes("admin_auth")
    data object AdminDashboard : NavRoutes("admin_dashboard")
    data object AdminMaterials : NavRoutes("admin_materials")
    data object AdminSubmissions : NavRoutes("admin_submissions")
    data object AdminUsers : NavRoutes("admin_users")
    data object AdminAnnouncements : NavRoutes("admin_announcements")
}
