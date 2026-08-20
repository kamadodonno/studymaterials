package com.pumaterial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.pumaterial.app.core.designsystem.PUMaterialTheme
import com.pumaterial.app.ui.navigation.AppNavHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as PuMaterialApp

        setContent {
            val themeMode by app.userSessionManager.themeModeFlow.collectAsState(initial = 0)
            val isDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            PUMaterialTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    app = app
                )
            }
        }
    }
}
