package com.stopthephone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stopthephone.StopThePhoneApp
import com.stopthephone.ui.detail.AppDetailScreen
import com.stopthephone.ui.detail.AppDetailViewModel
import com.stopthephone.ui.home.HomeScreen
import com.stopthephone.ui.home.HomeViewModel
import com.stopthephone.ui.picker.AppPickerScreen
import com.stopthephone.ui.picker.AppPickerViewModel
import com.stopthephone.ui.settings.SettingsScreen
import com.stopthephone.ui.settings.SettingsViewModel
import com.stopthephone.ui.theme.StpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StpTheme {
                StpNavHost()
            }
        }
    }
}

@Composable
private fun StpNavHost() {
    val app = LocalContext.current.applicationContext as StopThePhoneApp
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel { HomeViewModel(app.controlRepository, app.usageSync) },
                onPickApps = { navController.navigate("picker") },
                onOpenApp = { navController.navigate("detail/$it") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("picker") {
            AppPickerScreen(
                viewModel = viewModel { AppPickerViewModel(app.controlRepository, app.installedApps) },
                onBack = { navController.popBackStack() },
            )
        }
        composable("detail/{packageName}") { entry ->
            val packageName = entry.arguments?.getString("packageName").orEmpty()
            AppDetailScreen(
                viewModel = viewModel { AppDetailViewModel(app.controlRepository, packageName) },
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel { SettingsViewModel(app.settingsStore) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
