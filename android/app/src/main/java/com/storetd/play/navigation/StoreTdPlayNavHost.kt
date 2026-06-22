package com.storetd.play.navigation

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.storetd.play.core.network.AppConfigApi
import com.storetd.play.core.network.AccountStatusApi
import com.storetd.play.core.player.PlayerSession
import com.storetd.play.core.storage.LocalAccount
import com.storetd.play.core.storage.LocalAppConfig
import com.storetd.play.core.storage.LocalLibrary
import com.storetd.play.core.storage.SavedChannel
import com.storetd.play.feature.account.AccountScreen
import com.storetd.play.feature.auth.ActivationScreen
import com.storetd.play.feature.branding.BrandSplashScreen
import com.storetd.play.feature.epg.EpgScreen
import com.storetd.play.feature.favorites.FavoritesScreen
import com.storetd.play.feature.history.HistoryScreen
import com.storetd.play.feature.home.HomeScreen
import com.storetd.play.feature.live.ContentMode
import com.storetd.play.feature.live.LiveTvScreen
import com.storetd.play.feature.live.LiveTvViewModel
import com.storetd.play.feature.maintenance.MaintenanceScreen
import com.storetd.play.feature.player.PlayerScreen
import com.storetd.play.feature.player.WebViewPlayerScreen
import com.storetd.play.feature.vod.VodDetailScreen
import com.storetd.play.feature.settings.SettingsScreen
import com.storetd.play.feature.security.SecurityBlockedScreen
import com.storetd.play.feature.support.SupportScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.storetd.play.core.network.AppUpdateApi
import com.storetd.play.core.network.AppUpdateInfo
import com.storetd.play.core.update.AppUpdateDownloader
import androidx.compose.ui.unit.dp

private fun isDirectVideoPlaybackUrl(url: String): Boolean {
    val clean = url.lowercase()

    return clean.contains(".m3u8") ||
        clean.contains(".mp4") ||
        clean.contains("/magma-lite/") ||
        clean.contains("tvcluboficial.com") ||
        clean.contains("m3uts.xyz")
}

private fun isMagmaLiveUrl(url: String): Boolean {
    val clean = url.lowercase()
    return clean.contains("/magma-lite/live/") ||
        clean.contains("tvcluboficial.com") ||
        clean.contains("m3uts.xyz")
}



// STORETD_FORCE_LIVE_NAV_START
private fun storeTdIsLivePlaybackUrl(url: String): Boolean {
    val text = android.net.Uri.decode(url).lowercase()

    val isMovieOrSeries =
        text.contains("/xtream-lite/movie/") ||
        text.contains("/magma-lite/movie/") ||
        text.contains("/xtream-lite/series/") ||
        text.contains("/magma-lite/series/") ||
        text.contains("/movie/") ||
        text.contains("/series/")

    return !isMovieOrSeries && (
        text.contains("/xtream-lite/live/") ||
            text.contains("/magma-lite/live/") ||
            text.contains("/live/")
        )
}

private fun storeTdIsMoviePlaybackUrl(url: String): Boolean {
    val text = android.net.Uri.decode(url).lowercase()
    return text.contains("/xtream-lite/movie/") ||
        text.contains("/magma-lite/movie/") ||
        text.contains("/movie/")
}

private fun storeTdIsSeriesPlaybackUrl(url: String): Boolean {
    val text = android.net.Uri.decode(url).lowercase()
    return text.contains("/xtream-lite/series/") ||
        text.contains("/magma-lite/series/") ||
        text.contains("/series/")
}
// STORETD_FORCE_LIVE_NAV_END

@Composable
fun StoreTdPlayNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val liveTvViewModel: LiveTvViewModel = viewModel()

    var appConfig by remember {
        mutableStateOf(LocalAppConfig.get(context))
    }

    var securityMessage by remember {
        mutableStateOf<String?>(null)
    }

    var showStartupSplash by remember {
        mutableStateOf(true)
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

fun checkAccountStatus() {
        if (!LocalAccount.isActivated(context)) return

        scope.launch {
            val account = LocalAccount.getAccount(context)

            val result = withContext(Dispatchers.IO) {
                AccountStatusApi.check(
                    activationCode = account.activationCode,
                    deviceCode = account.deviceCode
                )
            }

            if (!result.allowed) {
                LocalAccount.logout(context)
                securityMessage = result.message
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadConfig()
        checkAccountStatus()
    }

    LaunchedEffect("brand_splash") {
        delay(1300L)
        showStartupSplash = false
    }


    LaunchedEffect(Unit) {
        while (true) {
            delay(120000L)
            checkAccountStatus()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAccountStatus()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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

    if (showStartupSplash) {
        BrandSplashScreen(
            appName = appConfig.appName,
            providerMessage = appConfig.providerMessage
        )
        return
    }

    securityMessage?.let { message ->
        SecurityBlockedScreen(
            message = message,
            onContinue = {
                securityMessage = null
                navigateAndClear(Routes.Activation)
            }
        )
        return
    }

    if (appConfig.maintenanceMode) {
        MaintenanceScreen(
            config = appConfig,
            onRetry = { reloadConfig() }
        )
        return
    }

    GlobalAppUpdateGate(enabled = LocalAccount.isActivated(context))

    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showSideMenu = currentRoute in listOf(Routes.Home, Routes.LiveTv, Routes.Movies, Routes.Series, Routes.Favorites, Routes.History, Routes.Account, Routes.Settings, Routes.Support, Routes.Epg)

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.horizontalGradient(
                colors = listOf(Color(0xFF000000), Color(0xFF09090B))
            ))
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().onPreviewKeyEvent { 
        if (it.key == Key.DirectionLeft) { 
            com.storetd.play.ui.components.globalLastLeftPressTime = System.currentTimeMillis() 
        }
        false 
    }) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(start = if (showSideMenu) 65.dp else 0.dp)) {
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
                    if (LocalAccount.hasUsedDemo(context)) {
                        return@ActivationScreen
                    }

                    LocalAccount.activateDemo(
                        context = context,
                        playlistUrl = "storetdplay://optimized/demo",
                        epgUrl = ""
                    )
                    navigateAndClear(Routes.Home)
                }
            )
        }

        composable(Routes.Home) {
            HomeScreen(
                onOpenLiveTv = { navController.navigate(Routes.LiveTv) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenMovies = { navController.navigate(Routes.Movies) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenSeries = { navController.navigate(Routes.Series) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenFavorites = { navController.navigate(Routes.Favorites) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenHistory = { navController.navigate(Routes.History) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenVodDetail = { item ->
                    val encName = android.net.Uri.encode(item.name)
                    val encUrl = android.net.Uri.encode(item.streamUrl)
                    val encGroup = android.net.Uri.encode(item.group)
                    val encLogo = if (item.logoUrl.isNullOrBlank() || item.logoUrl == "-") "-" else android.net.Uri.encode(item.logoUrl!!)
                    navController.navigate("${Routes.VodDetail}/$encName/$encUrl/$encGroup/$encLogo")
                },
                onOpenContinueItem = { item ->
                    PlayerSession.setQueue(
                        channels = listOf(item),
                        currentStreamUrl = item.streamUrl
                    )

                    navController.navigate(
                        "player/${Uri.encode(item.name)}/${Uri.encode(item.streamUrl)}/${Uri.encode(item.group)}/${Uri.encode(item.logoUrl.orEmpty())}"
                    )
                },
                onOpenEpg = { navController.navigate(Routes.Epg) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenAccount = { navController.navigate(Routes.Account) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenSupport = { navController.navigate(Routes.Support) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.Settings) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
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
                viewModel = liveTvViewModel,
                contentMode = ContentMode.LiveTv,
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

        composable(Routes.Movies) {
            LiveTvScreen(
                viewModel = liveTvViewModel,
                contentMode = ContentMode.Movies,
                onBack = { navController.popBackStack() },
                onPlay = { channel, visibleChannels ->
                    val saved = SavedChannel.from(channel)
                    PlayerSession.setQueue(
                        channels = visibleChannels.map { SavedChannel.from(it) },
                        currentStreamUrl = channel.streamUrl
                    )
                    LocalLibrary.addHistory(context, saved)

                    if (isMagmaLiveUrl(saved.streamUrl)) {
                        openPlayer(saved)
                    } else {
                        val encName = android.net.Uri.encode(saved.name)
                        val encUrl = android.net.Uri.encode(saved.streamUrl)
                        val encGroup = android.net.Uri.encode(saved.group)
                        val encLogo = if (saved.logoUrl.isNullOrBlank() || saved.logoUrl == "-") "-" else android.net.Uri.encode(saved.logoUrl!!)
                        if (storeTdIsLivePlaybackUrl(saved.streamUrl)) {
                            PlayerSession.setQueue(
                                channels = listOf(saved),
                                currentStreamUrl = saved.streamUrl
                            )

                            LocalLibrary.addHistory(context, saved)

                            navController.navigate("${Routes.Player}/$encName/$encUrl/$encGroup/$encLogo")
                        } else {
                            navController.navigate("${Routes.VodDetail}/$encName/$encUrl/$encGroup/$encLogo")
                        }
                    }
                }
            )
        }

        composable(Routes.Series) {
            LiveTvScreen(
                viewModel = liveTvViewModel,
                contentMode = ContentMode.Series,
                onBack = { navController.popBackStack() },
                onPlay = { channel, visibleChannels ->
                    val saved = SavedChannel.from(channel)

                    PlayerSession.setQueue(
                        channels = visibleChannels.map { SavedChannel.from(it) },
                        currentStreamUrl = channel.streamUrl
                    )

                    LocalLibrary.addHistory(context, saved)

                    val isMagmaVodEpisode = saved.streamUrl.contains(
                        "/magma-lite/movie/",
                        ignoreCase = true
                    )

                    if (isDirectVideoPlaybackUrl(saved.streamUrl) && !isMagmaVodEpisode) {
                        openPlayer(saved)
                    } else {
                        val encName = android.net.Uri.encode(saved.name)
                        val encUrl = android.net.Uri.encode(saved.streamUrl)
                        val encGroup = android.net.Uri.encode(saved.group)
                        val encLogo = if (saved.logoUrl.isNullOrBlank() || saved.logoUrl == "-") {
                            "-"
                        } else {
                            android.net.Uri.encode(saved.logoUrl!!)
                        }

                        if (storeTdIsLivePlaybackUrl(saved.streamUrl)) {
                            PlayerSession.setQueue(
                                channels = listOf(saved),
                                currentStreamUrl = saved.streamUrl
                            )

                            LocalLibrary.addHistory(context, saved)

                            navController.navigate("${Routes.Player}/$encName/$encUrl/$encGroup/$encLogo")
                        } else {
                            navController.navigate("${Routes.VodDetail}/$encName/$encUrl/$encGroup/$encLogo")
                        }
                    }
                }
            )
        }

                composable(
            route = "${Routes.VodDetail}/{name}/{url}/{group}/{logo}",
            arguments = listOf(
                androidx.navigation.navArgument("name") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("group") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("logo") { type = androidx.navigation.NavType.StringType }
            )
        ) { entry ->
            val name = entry.arguments?.getString("name").orEmpty()
            val url = entry.arguments?.getString("url").orEmpty()
            val group = entry.arguments?.getString("group").orEmpty()
            val logo = entry.arguments?.getString("logo").orEmpty()

            // STORETD_VODDETAIL_LIVE_GUARD_START
            // Si una URL live llega por error a VodDetail, nunca mostrar detalle VOD:
            // redirige directo al reproductor de TV.
            if (storeTdIsLivePlaybackUrl(url)) {
                val cleanLogo = logo.takeIf { it.isNotBlank() && it != "-" }

                val selectedSaved = SavedChannel(
                    id = "${name.lowercase()}|$url".hashCode().toString(),
                    name = name,
                    streamUrl = url,
                    logoUrl = cleanLogo,
                    group = group,
                    tvgId = null
                )

                val encName = android.net.Uri.encode(name)
                val encUrl = android.net.Uri.encode(url)
                val encGroup = android.net.Uri.encode(group)
                val encLogo = if (cleanLogo.isNullOrBlank()) "-" else android.net.Uri.encode(cleanLogo)

                androidx.compose.runtime.LaunchedEffect(url) {
                    PlayerSession.setQueue(
                        channels = listOf(selectedSaved),
                        currentStreamUrl = url
                    )

                    LocalLibrary.addHistory(context, selectedSaved)

                    navController.navigate("${Routes.Player}/$encName/$encUrl/$encGroup/$encLogo") {
                        launchSingleTop = true
                    }
                }

                return@composable
            }
            // STORETD_VODDETAIL_LIVE_GUARD_END

            VodDetailScreen(
                channelName = name,
                streamUrl = url,
                groupName = group,
                logoUrl = logo.takeIf { it != "-" },
                onPlay = { selectedStreamUrl ->
                    val cleanLogo = logo.takeIf { it.isNotBlank() && it != "-" }

                    val encName = android.net.Uri.encode(name)
                    val encUrl = android.net.Uri.encode(selectedStreamUrl)
                    val encGroup = android.net.Uri.encode(group)
                    val encLogo = if (logo.isBlank() || logo == "-") "-" else android.net.Uri.encode(logo)

                    if (isDirectVideoPlaybackUrl(selectedStreamUrl)) {
                        val selectedSaved = SavedChannel(
                            id = "${name.lowercase()}|$selectedStreamUrl".hashCode().toString(),
                            name = name,
                            streamUrl = selectedStreamUrl,
                            logoUrl = cleanLogo,
                            group = group,
                            tvgId = null
                        )

                        PlayerSession.setQueue(
                            channels = listOf(selectedSaved),
                            currentStreamUrl = selectedStreamUrl
                        )

                        LocalLibrary.addHistory(context, selectedSaved)

                        navController.navigate("${Routes.Player}/$encName/$encUrl/$encGroup/$encLogo")
                    } else {
                        navController.navigate("${Routes.Player}/$encName/$encUrl/$encGroup/$encLogo")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.WebPlayer}/{name}/{url}/{group}/{logo}",
            arguments = listOf(
                androidx.navigation.navArgument("name") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("group") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("logo") { type = androidx.navigation.NavType.StringType }
            )
        ) { entry ->
            WebViewPlayerScreen(
                title = entry.arguments?.getString("name").orEmpty(),
                url = entry.arguments?.getString("url").orEmpty(),
                onBack = { navController.popBackStack() }
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
        
            if (showSideMenu) {
                com.storetd.play.ui.components.PremiumSideMenu(navController = navController, currentRoute = currentRoute)
            }
    }
    }
}

@Composable
private fun GlobalAppUpdateGate(
    enabled: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var appUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }

    fun checkForUpdate() {
        if (!enabled || checkingUpdate) return

        checkingUpdate = true

        scope.launch {
            val update = withContext(Dispatchers.IO) {
                AppUpdateApi.check()
            }

            checkingUpdate = false

            if (update.success && update.updateAvailable && update.apkUrl.isNotBlank()) {
                appUpdateInfo = update
                showAppUpdateDialog = true
            }
        }
    }

    DisposableEffect(enabled, lifecycleOwner) {
        if (enabled) {
            checkForUpdate()
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && enabled) {
                checkForUpdate()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAppUpdateDialog && appUpdateInfo != null) {
        val update = appUpdateInfo!!

        AlertDialog(
            onDismissRequest = {
                if (!update.forceUpdate) {
                    showAppUpdateDialog = false
                }
            },
            title = {
                Text(
                    text = if (update.forceUpdate) {
                        "Actualización requerida"
                    } else {
                        "Actualización disponible"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nueva versión: ${update.latestVersionName}")

                    if (update.changelog.isNotBlank()) {
                        Text(update.changelog)
                    }

                    Text("Tocá Actualizar para descargar e instalar la nueva APK.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppUpdateDownloader.downloadAndInstall(context, update.apkUrl)

                        if (!update.forceUpdate) {
                            showAppUpdateDialog = false
                        }
                    }
                ) {
                    Text("Actualizar")
                }
            },
            dismissButton = {
                if (!update.forceUpdate) {
                    TextButton(
                        onClick = {
                            showAppUpdateDialog = false
                        }
                    ) {
                        Text("Más tarde")
                    }
                }
            }
        )
    }
}

