package com.itools.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.itools.app.data.SettingsStore
import com.itools.app.ui.screens.HomeScreen
import com.itools.app.ui.screens.SettingsScreen
import com.itools.app.ui.screens.ToolScreen

@Composable
fun AppNav(store: SettingsStore) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                store = store,
                onOpenTool = { nav.navigate("tool/$it") },
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable("tool/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
            ToolScreen(e.arguments?.getString("id").orEmpty(), store, onBack = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(store, onBack = { nav.popBackStack() }) }
    }
}
