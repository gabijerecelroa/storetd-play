package com.storetd.play.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storetd.play.feature.account.AccountScreen
import com.storetd.play.feature.favorites.FavoritesScreen
import com.storetd.play.feature.history.HistoryScreen
import com.storetd.play.feature.home.HomeScreen
import com.storetd.play.feature.live.LiveTvScreen
import com.storetd.play.feature.player.PlayerScreen
import com.storetd.play.feature.settings.SettingsScreen
import com.storetd.play.feature.support.SupportScreen

@Composable
fun StoreTdPlayNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                onOpenLiveTv = { navController.navigate(Routes.LiveTv) },
                onOpenFavorites = { navController.navigate(Routes.Favorites) },
                onOpenHistory = { navController.navigate(Routes.History) },
                onOpenAccount = { navController.navigate(Routes.Account) },
                onOpenSupport = { navController.navigate(Routes.Support) },
                onOpenSettings = { navController.navigate(Routes.Settings) }
            )
        }

        composable(Routes.LiveTv) {
            LiveTvScreen(
                onBack = { navController.popBackStack() },
                onPlay = { channel ->
                    navController.navigate(
                        "${Routes.Player}/${Uri.encode(channel.name)}/${Uri.encode(channel.streamUrl)}"
                    )
                }
            )
        }

        composable(
            route = "${Routes.Player}/{name}/{url}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType }
            )
        ) { entry ->
            PlayerScreen(
                channelName = entry.arguments?.getString("name").orEmpty(),
                streamUrl = entry.arguments?.getString("url").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Favorites) { FavoritesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.History) { HistoryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.Account) { AccountScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.Support) { SupportScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.Settings) { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
