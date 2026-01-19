package com.freedomvpn.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freedomvpn.ui.screens.ConnectionScreen
import com.freedomvpn.ui.screens.ServerListScreen
import com.freedomvpn.ui.screens.SettingsScreen

/**
 * Navigation routes
 */
sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object ServerList : Screen("server_list")
    object Settings : Screen("settings")
}

/**
 * Main navigation host
 */
@Composable
fun FreedomNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Connection.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right) }
    ) {
        // Main connection screen
        composable(
            route = Screen.Connection.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            ConnectionScreen(
                onNavigateToServers = {
                    navController.navigate(Screen.ServerList.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        // Server list screen
        composable(
            route = Screen.ServerList.route
        ) {
            ServerListScreen(
                onServerSelected = { server ->
                    // TODO: Pass selected server to MainViewModel
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Settings screen
        composable(
            route = Screen.Settings.route
        ) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
