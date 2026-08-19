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
                onEditPose = {
                    navController.navigate(POSE_SELECTOR_SCREEN)
                },
                navController = navController
            )
        }

        // Pose Selector Route
        composable(POSE_SELECTOR_SCREEN) {
            PoseSelectorMainView(
                onBack = {
                    navController.popBackStack()
                },
                onConfirm = { offset, rotation ->
                    // FRC Coords: X is Depth (Up), Y is Width (Left)
                    // Offset.y negative -> Up
                    // Offset.x negative -> Left
                    val frcX = -offset.y / 10f
                    val frcY = -offset.x / 10f
                    
                    navController.previousBackStackEntry?.savedStateHandle?.set("pose_x", frcX.toDouble())
                    navController.previousBackStackEntry?.savedStateHandle?.set("pose_y", frcY.toDouble())
                    navController.previousBackStackEntry?.savedStateHandle?.set("pose_r", rotation.toDouble())
                }
            )
        }
    }
}
