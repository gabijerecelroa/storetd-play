package com.storetd.play.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storetd.play.core.network.AppConfigApi
import com.storetd.play.core.network.RemoteAppConfig
import com.storetd.play.core.player.PlayerSession
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalAppConfig
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.SavedChannel
import com.storetd.play.feature.account.AccountScreen
import com.storetd.play.feature.epg.EpgScreen
import com.storetd.play.feature.auth.ActivationScreen
import com.storetd.play.feature.favorites.FavoritesScreen
import com.storetd.play.feature.history.HistoryScreen
import com.storetd.play.feature.home.HomeScreen
import com.storetd.play.feature.live.LiveTvScreen
import com.storetd.play.feature.maintenance.MaintenanceScreen
import com.storetd.play.feature.player.PlayerScreen
import com.storetd.play.feature.settings.SettingsScreen
import com.storetd.play.feature.support.SupportScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StoreTdPlayNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appConfig by remember {
        mutableStateOf(LocalAppConfig.get(context))
    }

    val startDestination = remember {
        if (LocalAccount.isActivated(context)) Routes.Home else Routes.Activation
    }

    fun reloadConfig() {
        scope.launch {
            val remoteConfig = withContext(Dispatchers.IO) {
                AppConfigApi.load()
            }

            LocalAppConfig.save(context, remoteConfig)
            appConfig = remoteConfig
        }
    }

    LaunchedEffect(Unit) {
        reloadConfig()
    }

    fun navigateAndClear(route: String) {
        navController.navigate(route) {
            popUpTo(0) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun openPlayer(channel: SavedChannel) {
        navController.navigate(
            "${Routes.Player}/" +
                "${Uri.encode(channel.name)}/" +
                "${Uri.encode(channel.streamUrl)}/" +
                "${Uri.encode(channel.group)}/" +
                "${Uri.encode(channel.logoUrl ?: "-")}"
        )
    }

    if (appConfig.maintenanceMode) {
        MaintenanceScreen(
            config = appConfig,
            onRetry = { reloadConfig() }
        )
        return
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.Activation) {
            ActivationScreen(
                onActivate = { customerName, activationCode, status, expiresAt, playlistUrl, epgUrl, maxDevices, deviceCount ->
                    LocalAccount.activate(
                        context = context,
                        customerName = customerName,
                        activationCode = activationCode,
                        status = status,
                        expiresAt = expiresAt,
                        playlistUrl = playlistUrl,
                        epgUrl = epgUrl,
                        maxDevices = maxDevices,
                        deviceCount = deviceCount
                    )
                    navigateAndClear(Routes.Home)
                },
                onDemo = {
                    LocalAccount.activate(
                        context = context,
                        customerName = "Cliente Demo",
                        activationCode = "DEMO1234",
                        status = "Prueba",
                        expiresAt = "",
                        playlistUrl = "",
                        epgUrl = "",
                        maxDevices = 5,
                        deviceCount = 1
                    )
                    navigateAndClear(Routes.Home)
                }
            )
        }

        composable(Routes.Home) {
            HomeScreen(
                onOpenLiveTv = { navController.navigate(Routes.LiveTv) },
                onOpenFavorites = { navController.navigate(Routes.Favorites) },
                onOpenHistory = { navController.navigate(Routes.History) },
                onOpenEpg = { navController.navigate(Routes.Epg) },
                onOpenAccount = { navController.navigate(Routes.Account) },
                onOpenSupport = { navController.navigate(Routes.Support) },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                config = appConfig
            )
        }

        composable(Routes.Epg) {
            EpgScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LiveTv) {
            LiveTvScreen(
                onBack = { navController.popBackStack() },
                onPlay = { channel, visibleChannels ->
                    val saved = SavedChannel.from(channel)
                    PlayerSession.setQueue(
                        channels = visibleChannels.map { SavedChannel.from(it) },
                        currentStreamUrl = channel.streamUrl
                    )
                    LocalLibrary.addHistory(context, saved)
                    openPlayer(saved)
                }
            )
        }

        composable(
            route = "${Routes.Player}/{name}/{url}/{group}/{logo}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType },
                navArgument("group") { type = NavType.StringType },
                navArgument("logo") { type = NavType.StringType }
            )
        ) { entry ->
            val logo = entry.arguments?.getString("logo").orEmpty()

            PlayerScreen(
                channelName = entry.arguments?.getString("name").orEmpty(),
                streamUrl = entry.arguments?.getString("url").orEmpty(),
                groupName = entry.arguments?.getString("group").orEmpty(),
                logoUrl = logo.takeIf { it != "-" },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Favorites) {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onPlay = { channel ->
                    PlayerSession.setQueue(
                        channels = LocalLibrary.favorites(context),
                        currentStreamUrl = channel.streamUrl
                    )
                    LocalLibrary.addHistory(context, channel)
                    openPlayer(channel)
                }
            )
        }

        composable(Routes.History) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onPlay = { channel ->
                    PlayerSession.setQueue(
                        channels = LocalLibrary.history(context),
                        currentStreamUrl = channel.streamUrl
                    )
                    LocalLibrary.addHistory(context, channel)
                    openPlayer(channel)
                }
            )
        }

        composable(Routes.Account) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navigateAndClear(Routes.Activation)
                }
            )
        }

        composable(Routes.Support) {
            SupportScreen(
                onBack = { navController.popBackStack() },
                config = appConfig
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
