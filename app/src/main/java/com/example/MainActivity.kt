package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.SupabaseRepository
import com.example.ui.*
import com.example.ui.theme.MyReelsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SupabaseRepository(applicationContext)
        val startDestination = if (repo.accessToken != null) "home" else "login"

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            MyReelsTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

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
