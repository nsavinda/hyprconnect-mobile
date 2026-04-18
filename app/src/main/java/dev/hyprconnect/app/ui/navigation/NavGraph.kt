package dev.hyprconnect.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.hyprconnect.app.ui.clipboard.ClipboardScreen
import dev.hyprconnect.app.ui.home.HomeScreen
import dev.hyprconnect.app.ui.pairing.PairingScreen
import dev.hyprconnect.app.ui.settings.SettingsScreen
import dev.hyprconnect.app.ui.device.DeviceDetailScreen
import dev.hyprconnect.app.ui.filetransfer.FileTransferScreen
import dev.hyprconnect.app.ui.media.MediaControlScreen
import dev.hyprconnect.app.ui.remoteinput.RemoteInputScreen

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
    object MediaControl : Screen("media_control")
    object RemoteInput : Screen("remote_input")
    object ClipboardHistory : Screen("clipboard_history")
}

@Composable
fun NavGraph(sharedUris: List<Uri> = emptyList()) {
    val navController = rememberNavController()
    val startDestination = if (sharedUris.isNotEmpty()) Screen.FileTransfer.route else Screen.Home.route

    NavHost(navController = navController, startDestination = startDestination) {
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
                onNavigateToFileTransfer = { navController.navigate(Screen.FileTransfer.route) },
                onNavigateToMediaControl = { navController.navigate(Screen.MediaControl.route) },
                onNavigateToRemoteInput = { navController.navigate(Screen.RemoteInput.route) },
                onNavigateToClipboard = { navController.navigate(Screen.ClipboardHistory.route) }
            )
        }
        composable(Screen.ClipboardHistory.route) {
            ClipboardScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.FileTransfer.route) {
            FileTransferScreen(
                onNavigateBack = { navController.popBackStack() },
                sharedUris = sharedUris
            )
        }
        composable(Screen.MediaControl.route) {
            MediaControlScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.RemoteInput.route) {
            RemoteInputScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
