package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.SupabaseRepository
import com.example.ui.*
import com.example.ui.theme.MyReelsTheme

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SupabaseRepository(applicationContext)
        val startDestination = if (repo.accessToken != null) "home" else "login"

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            MyReelsTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val bottomNavItems = listOf(
                    BottomNavItem("home", "Home", Icons.Default.Home),
                    BottomNavItem("new_series", "Create", Icons.Default.AddCircle),
                    BottomNavItem("history", "History", Icons.Default.PlayArrow),
                    BottomNavItem("settings", "Settings", Icons.Default.Settings)
                )

                // Show fixed bottom tabs on all authenticated screens except login and video player
                val showBottomBar = currentRoute != null &&
                        currentRoute != "login" &&
                        !currentRoute.startsWith("video_player")

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                bottomNavItems.forEach { item ->
                                    val isSelected = currentRoute == item.route
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != item.route) {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = {
                                            Text(item.label)
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavHost(navController = navController, startDestination = startDestination) {
                            composable("login") {
                                LoginScreen(repo = repo) {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            }
                            composable("home") {
                                HomeScreen(
                                    repo = repo,
                                    isDarkTheme = isDarkTheme,
                                    onToggleTheme = { isDarkTheme = it },
                                    onNavigateNewSeries = { navController.navigate("new_series") },
                                    onNavigateSeries = { seriesId -> navController.navigate("series_detail/$seriesId") },
                                    onNavigateSelectNiche = { navController.navigate("select_niche") },
                                    onNavigateSelectVoice = { navController.navigate("select_voice") },
                                    onNavigateHistory = { navController.navigate("history") },
                                    onNavigateSettings = { navController.navigate("settings") },
                                    onSignedOut = {
                                        navController.navigate("login") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("new_series") {
                                NewSeriesScreen(repo = repo) { seriesId ->
                                    navController.navigate("series_detail/$seriesId") {
                                        popUpTo("new_series") { inclusive = true }
                                    }
                                }
                            }
                            composable("select_niche") {
                                SelectNicheScreen { niche, custom ->
                                    navController.popBackStack()
                                }
                            }
                            composable("select_voice") {
                                SelectVoiceScreen { voice, style ->
                                    navController.popBackStack()
                                }
                            }
                            composable("history") {
                                HistoryScreen(repo = repo) { videoId ->
                                    navController.navigate("video_player/$videoId")
                                }
                            }
                            composable("settings") {
                                SettingsScreen {
                                    navController.popBackStack()
                                }
                            }
                            composable(
                                route = "series_detail/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val id = backStackEntry.arguments?.getString("id") ?: ""
                                SeriesDetailScreen(
                                    seriesId = id,
                                    repo = repo,
                                    onNavigateVideo = { videoId -> navController.navigate("video_player/$videoId") }
                                )
                            }
                            composable(
                                route = "video_player/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val id = backStackEntry.arguments?.getString("id") ?: ""
                                VideoPlayerScreen(videoId = id, repo = repo)
                            }
                        }
                    }
                }
            }
        }
    }
}
