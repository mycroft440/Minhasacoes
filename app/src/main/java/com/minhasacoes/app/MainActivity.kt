package com.minhasacoes.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.minhasacoes.app.notify.Notifier
import com.minhasacoes.app.ui.home.HomeScreen
import com.minhasacoes.app.ui.settings.SettingsScreen
import com.minhasacoes.app.ui.theme.MinhasAcoesTheme
import com.minhasacoes.app.ui.wallet.WalletScreen

class MainActivity : ComponentActivity() {

    private val pendingWallet = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingWallet.value = walletIdFrom(intent)
        setContent {
            MinhasAcoesTheme {
                AppNavigation(pendingWallet)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        walletIdFrom(intent)?.let { pendingWallet.value = it }
    }

    private fun walletIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(Notifier.EXTRA_WALLET_ID, -1L)?.takeIf { it > 0 }
}

private const val ANIM_MS = 260

@Composable
private fun AppNavigation(pendingWallet: MutableState<Long?>) {
    val nav = rememberNavController()
    RequestNotificationPermissionOnce()

    // Abre a carteira ao tocar em uma notificação.
    LaunchedEffect(pendingWallet.value) {
        val id = pendingWallet.value ?: return@LaunchedEffect
        pendingWallet.value = null
        nav.navigate("wallet/$id") {
            popUpTo("home")
            launchSingleTop = true
        }
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { fadeIn(tween(ANIM_MS)) + slideInHorizontally(tween(ANIM_MS)) { it / 10 } },
        exitTransition = { fadeOut(tween(ANIM_MS)) },
        popEnterTransition = { fadeIn(tween(ANIM_MS)) },
        popExitTransition = { fadeOut(tween(ANIM_MS)) + slideOutHorizontally(tween(ANIM_MS)) { it / 10 } },
    ) {
        composable("home") {
            HomeScreen(
                onOpenWallet = { id -> nav.navigate("wallet/$id") { launchSingleTop = true } },
                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
            )
        }
        composable(
            route = "wallet/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            WalletScreen(
                walletId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { if (nav.previousBackStackEntry != null) nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { if (nav.previousBackStackEntry != null) nav.popBackStack() })
        }
    }
}

@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
