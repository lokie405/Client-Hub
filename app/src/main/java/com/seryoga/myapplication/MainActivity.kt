package com.seryoga.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.seryoga.myapplication.ui.ClientViewModel
import com.seryoga.myapplication.ui.screens.ClientDetailScreen
import com.seryoga.myapplication.ui.screens.ClientListScreen
import com.seryoga.myapplication.ui.screens.SettingsScreen
import com.seryoga.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val viewModel: ClientViewModel = viewModel()

                NavHost(navController = navController, startDestination = "list") {
                    composable("list") {
                        ClientListScreen(
                            viewModel = viewModel,
                            onClientClick = { id ->
                                navController.navigate("detail/$id")
                            },
                            onAddClientClick = {
                                navController.navigate("detail/new")
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    composable("detail/{clientId}") { backStackEntry ->
                        val clientId = backStackEntry.arguments?.getString("clientId")
                        ClientDetailScreen(
                            viewModel = viewModel,
                            clientId = if (clientId == "new") null else clientId?.toLong(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            isDarkTheme = isDarkTheme,
                            onThemeChange = { newValue -> isDarkTheme = newValue },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
