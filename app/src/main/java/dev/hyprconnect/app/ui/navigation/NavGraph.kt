package dev.hyprconnect.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.hyprconnect.app.ui.home.HomeScreen
import dev.hyprconnect.app.ui.pairing.PairingScreen
import dev.hyprconnect.app.ui.settings.SettingsScreen
import dev.hyprconnect.app.ui.device.DeviceDetailScreen
import dev.hyprconnect.app.ui.filetransfer.FileTransferScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Pairing : Screen("pairing/{deviceId}") {
        fun createRoute(deviceId: String) = "pairing/$deviceId"
    }
    object Settings : Screen("settings")
    object DeviceDetail : Screen("device/{deviceId}") {
        fun createRoute(deviceId: String) = "device/$deviceId"
    }
    object FileTransfer : Screen("file_transfer")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToPairing = { deviceId -> navController.navigate(Screen.Pairing.createRoute(deviceId)) },
                onNavigateToDeviceDetail = { deviceId -> navController.navigate(Screen.DeviceDetail.createRoute(deviceId)) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Pairing.route) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            PairingScreen(
                deviceId = deviceId,
                onNavigateBack = { navController.popBackStack() },
                onPairingSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.DeviceDetail.route) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            DeviceDetailScreen(
                deviceId = deviceId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFileTransfer = { navController.navigate(Screen.FileTransfer.route) }
            )
        }
        composable(Screen.FileTransfer.route) {
            FileTransferScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
