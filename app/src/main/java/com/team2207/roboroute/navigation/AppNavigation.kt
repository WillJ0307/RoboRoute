package com.team2207.roboroute.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.team2207.roboroute.ui.home.MainView
import com.team2207.roboroute.ui.settings.SettingsView
import com.team2207.roboroute.ui.action.PoseSelectorMainView

const val HOME_SCREEN = "home"
const val SETTINGS_SCREEN = "settings"
const val POSE_SELECTOR_SCREEN = "pose_selector"

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
            SettingsView(
                onBack = {
                    navController.popBackStack()
                },
                onEditPose = { x, y, r ->
                    navController.navigate("$POSE_SELECTOR_SCREEN?x=$x&y=$y&r=$r")
                },
                navController = navController
            )
        }

        // Pose Selector Route
        composable(
            route = "$POSE_SELECTOR_SCREEN?x={x}&y={y}&r={r}",
            arguments = listOf(
                androidx.navigation.navArgument("x") { defaultValue = "0.0" },
                androidx.navigation.navArgument("y") { defaultValue = "0.0" },
                androidx.navigation.navArgument("r") { defaultValue = "0.0" }
            )
        ) { backStackEntry ->
            val x = backStackEntry.arguments?.getString("x")?.toDoubleOrNull() ?: 0.0
            val y = backStackEntry.arguments?.getString("y")?.toDoubleOrNull() ?: 0.0
            val r = backStackEntry.arguments?.getString("r")?.toDoubleOrNull() ?: 0.0

            PoseSelectorMainView(
                onBack = {
                    navController.popBackStack()
                },
                onConfirm = { confirmX, confirmY, confirmR ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("pose_x", confirmX)
                    navController.previousBackStackEntry?.savedStateHandle?.set("pose_y", confirmY)
                    navController.previousBackStackEntry?.savedStateHandle?.set("pose_r", confirmR)
                },
                initialX = x,
                initialY = y,
                initialR = r
            )
        }
    }
}
