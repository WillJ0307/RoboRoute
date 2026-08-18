package com.team2207.roboroute.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.team2207.roboroute.ui.home.MainView
import com.team2207.roboroute.ui.settings.SettingsView

const val HOME_SCREEN = "home"
const val SETTINGS_SCREEN = "settings"

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_SCREEN) {
        // Home Screen Route
        composable(HOME_SCREEN) {
            MainView(onNavigateToSettings = {
                navController.navigate(SETTINGS_SCREEN)
            })
        }

        // Settings Screen Route
        composable(SETTINGS_SCREEN) {
            SettingsView(onBack = {
                navController.popBackStack()
            })
        }
    }
}
