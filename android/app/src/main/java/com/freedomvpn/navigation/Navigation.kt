package com.freedomvpn.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freedomvpn.chat.ui.ChatScreen
import com.freedomvpn.chat.viewmodel.ChatViewModel
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
    object Chat : Screen("chat")
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
        
        // Secure Chat screen
        composable(
            route = Screen.Chat.route
        ) {
            val chatViewModel: ChatViewModel = hiltViewModel()
            val uiState by chatViewModel.uiState.collectAsState()
            val context = LocalContext.current
            
            ChatScreen(
                uiState = uiState,
                onSetActiveChat = chatViewModel::setActiveChat,
                onSendMessage = chatViewModel::sendMessage,
                onAddContact = chatViewModel::addContact,
                onDeleteConversation = chatViewModel::deleteConversation,
                onSetDisappearTimer = chatViewModel::setDisappearTimer,
                onWipeAll = chatViewModel::wipeAll,
                onCopyToClipboard = { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Freedom ID", text))
                }
            )
        }
    }
}
